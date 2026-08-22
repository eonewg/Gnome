package io.github.eonewg.gnome.data.local

import io.github.eonewg.gnome.core.tag.MemosTagParser
import io.github.eonewg.gnome.data.local.dao.MemoDao
import io.github.eonewg.gnome.data.local.dao.SyncOperationDao
import io.github.eonewg.gnome.data.local.dao.TagDao
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.MemoTagEntity
import io.github.eonewg.gnome.data.local.entity.MemoWithResources
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.local.entity.SyncEntityType
import io.github.eonewg.gnome.data.local.entity.SyncOperationEntity
import io.github.eonewg.gnome.data.local.entity.SyncOperationType
import java.io.File
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The only component allowed to touch the memos/resources/sync_operations
 * tables. Owns Room, DAOs and transaction composition; knows nothing about
 * Retrofit, WorkManager or files beyond computing which local files became
 * stale (returned to callers, who delete them outside any transaction).
 *
 * Mutation methods that represent user intent also enqueue the matching
 * outbox operation inside the same transaction, so Room success is durable
 * success. SyncEngine's read/write-back paths use the single-row primitives
 * and manage outbox rows explicitly.
 */
class LocalMemoDataSource(
    private val memoDao: MemoDao,
    private val syncOperationDao: SyncOperationDao,
    private val tagDao: TagDao,
    private val transactionRunner: TransactionRunner,
) {

    /**
     * Replaces the memo's tag index rows with a fresh parse of its content.
     * Must run inside the same transaction as the memo row write it follows.
     */
    private suspend fun rebuildTags(memo: MemoEntity) {
        tagDao.deleteByMemo(memo.accountKey, memo.identifier)
        val tags = MemosTagParser.extractTags(memo.content)
        if (tags.isNotEmpty()) {
            tagDao.insertAll(tags.map { MemoTagEntity(memo.accountKey, memo.identifier, it) })
        }
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    fun observeTimeline(accountKey: String): Flow<List<MemoWithResources>> =
        memoDao.observeAllMemos(accountKey)

    fun observeUnsyncedCount(accountKey: String): Flow<Int> =
        memoDao.observeUnsyncedCount(accountKey)

    suspend fun getTimeline(accountKey: String): List<MemoEntity> =
        memoDao.getAllMemos(accountKey)

    suspend fun getArchived(accountKey: String): List<MemoEntity> =
        memoDao.getArchivedMemos(accountKey)

    suspend fun getAllForSync(accountKey: String): List<MemoEntity> =
        memoDao.getAllMemosForSync(accountKey)

    suspend fun getAllResources(accountKey: String): List<ResourceEntity> =
        memoDao.getAllResources(accountKey)

    suspend fun getMemo(identifier: String, accountKey: String): MemoEntity? =
        memoDao.getMemoById(identifier, accountKey)

    suspend fun getMemoByRemoteId(remoteId: String, accountKey: String): MemoEntity? =
        memoDao.getMemoByRemoteId(remoteId, accountKey)

    suspend fun getMemoResources(memoId: String, accountKey: String): List<ResourceEntity> =
        memoDao.getMemoResources(memoId, accountKey)

    suspend fun getResource(identifier: String, accountKey: String): ResourceEntity? =
        memoDao.getResourceById(identifier, accountKey)

    // -----------------------------------------------------------------------
    // Single-row primitives (SyncEngine write-back paths; no implicit outbox)
    // -----------------------------------------------------------------------

    suspend fun upsertMemo(memo: MemoEntity) = transactionRunner.inTransaction {
        memoDao.insertMemo(memo)
        rebuildTags(memo)
    }

    suspend fun upsertResource(resource: ResourceEntity) = memoDao.insertResource(resource)

    suspend fun deleteResourceRow(resource: ResourceEntity) = memoDao.deleteResource(resource)

    suspend fun deleteMemoRow(memo: MemoEntity) = memoDao.deleteMemo(memo)

    /**
     * Atomically replaces the local row with the server's version: upserts the
     * memo, drops local resources the server no longer references (keeping
     * already-downloaded files where possible) and inserts the remote set.
     * Returns the local file URIs that became stale; the caller deletes them
     * outside the transaction.
     */
    suspend fun replaceSyncedMemo(
        memo: MemoEntity,
        remoteResources: List<ResourceEntity>,
    ): List<String> {
        val staleFiles = arrayListOf<String>()
        transactionRunner.inTransaction {
            memoDao.insertMemo(memo)
            rebuildTags(memo)

            val currentResources = memoDao.getMemoResources(memo.identifier, memo.accountKey)
            val remoteResourceIds = remoteResources.mapTo(hashSetOf()) { it.remoteId }
            currentResources.forEach { current ->
                if (current.remoteId !in remoteResourceIds) {
                    localFileUriOf(current)?.let(staleFiles::add)
                    memoDao.deleteResource(current)
                }
            }

            remoteResources.forEach { resource ->
                val existing = currentResources.firstOrNull { it.remoteId == resource.remoteId }
                val localResourceIdentifier = existing?.identifier ?: UUID.randomUUID().toString()
                val preferredLocalUri = when {
                    existing?.localUri != null && fileExists(existing.localUri) -> existing.localUri
                    existing != null && existing.uri.startsWith("file:") && fileExists(existing.uri) -> existing.uri
                    else -> null
                }
                memoDao.insertResource(
                    resource.copy(identifier = localResourceIdentifier, localUri = preferredLocalUri)
                )
            }
        }
        return staleFiles
    }

    /**
     * Inserts a standalone row with copies of another memo's resources. Used
     * by conflict duplication; deliberately enqueues no outbox operation —
     * the reconcile that creates the duplicate pushes it directly.
     */
    suspend fun insertMemoWithClonedResources(
        accountKey: String,
        sourceMemoId: String,
        memo: MemoEntity,
    ) {
        transactionRunner.inTransaction {
            memoDao.insertMemo(memo)
            rebuildTags(memo)
            memoDao.getMemoResources(sourceMemoId, accountKey).forEach { resource ->
                memoDao.insertResource(
                    resource.copy(identifier = UUID.randomUUID().toString(), memoId = memo.identifier)
                )
            }
        }
    }

    /**
     * Removes a memo row and its resources permanently. Returns the resource
     * rows that were removed so the caller can delete their local files
     * outside the transaction; empty when the memo does not exist.
     */
    suspend fun purgeMemo(identifier: String, accountKey: String): List<ResourceEntity> {
        val memo = memoDao.getMemoById(identifier, accountKey) ?: return emptyList()
        val resources = memoDao.getMemoResources(identifier, accountKey)
        transactionRunner.inTransaction {
            resources.forEach { memoDao.deleteResource(it) }
            tagDao.deleteByMemo(accountKey, identifier)
            memoDao.deleteMemo(memo)
        }
        return resources
    }

    // -----------------------------------------------------------------------
    // User-intent mutations (transactional write + outbox enqueue)
    // -----------------------------------------------------------------------

    /**
     * Offline create: memo + attachments (+ MEMO UPSERT operation), atomic.
     * Local-only accounts pass sync=false and simply persist.
     */
    suspend fun createLocalMemo(memo: MemoEntity, resources: List<ResourceEntity>, sync: Boolean = true) {
        transactionRunner.inTransaction {
            memoDao.insertMemo(memo)
            rebuildTags(memo)
            resources.forEach { resource ->
                memoDao.insertResource(resource.copy(accountKey = memo.accountKey, memoId = memo.identifier))
            }
            if (sync) {
                enqueueOperation(memo.accountKey, SyncEntityType.MEMO, SyncOperationType.UPSERT, memo.identifier)
            }
        }
    }

    /**
     * Offline edit: upserts the memo, replaces the attachment set when given,
     * and enqueues MEMO UPSERT. Returns local file URIs that became stale.
     */
    suspend fun updateLocalMemo(
        memo: MemoEntity,
        resources: List<ResourceEntity>?,
        sync: Boolean = true,
    ): List<String> {
        val staleFiles = arrayListOf<String>()
        transactionRunner.inTransaction {
            memoDao.insertMemo(memo)
            rebuildTags(memo)

            if (resources != null) {
                val existingResources = memoDao.getMemoResources(memo.identifier, memo.accountKey)
                val incomingIds = resources.mapTo(hashSetOf()) { it.identifier }
                existingResources.forEach { existing ->
                    if (existing.identifier !in incomingIds) {
                        localFileUriOf(existing)?.let(staleFiles::add)
                        memoDao.deleteResource(existing)
                    }
                }
                resources.forEach { resource ->
                    memoDao.insertResource(
                        resource.copy(accountKey = memo.accountKey, memoId = memo.identifier)
                    )
                }
            }
            if (sync) {
                enqueueOperation(memo.accountKey, SyncEntityType.MEMO, SyncOperationType.UPSERT, memo.identifier)
            }
        }
        return staleFiles
    }

    /** Soft delete: tombstone + MEMO DELETE operation. */
    suspend fun markMemoDeleted(memo: MemoEntity) {
        transactionRunner.inTransaction {
            memoDao.insertMemo(memo.copy(isDeleted = true, needsSync = true, lastModified = Instant.now()))
            tagDao.deleteByMemo(memo.accountKey, memo.identifier)
            enqueueOperation(memo.accountKey, SyncEntityType.MEMO, SyncOperationType.DELETE, memo.identifier)
        }
    }

    /** Archive or restore: flag (+ MEMO UPSERT operation for sync accounts). */
    suspend fun setMemoArchived(memo: MemoEntity, archived: Boolean, sync: Boolean = true) {
        val now = Instant.now()
        transactionRunner.inTransaction {
            memoDao.insertMemo(
                memo.copy(
                    archived = archived,
                    needsSync = sync,
                    lastModified = now,
                    lastSyncedAt = if (sync) memo.lastSyncedAt else now,
                )
            )
            if (sync) {
                enqueueOperation(memo.accountKey, SyncEntityType.MEMO, SyncOperationType.UPSERT, memo.identifier)
            }
        }
    }

    /**
     * Resource attached to a memo on creation/edit: row + memo needsSync +
     * MEMO UPSERT. Local-only accounts pass markDirty=false for a plain insert.
     */
    suspend fun attachResourceToMemo(resource: ResourceEntity, memoId: String, markDirty: Boolean = true) {
        transactionRunner.inTransaction {
            memoDao.insertResource(resource)
            if (markDirty) {
                memoDao.getMemoById(memoId, resource.accountKey)?.let { memo ->
                    memoDao.insertMemo(memo.copy(needsSync = true, lastModified = Instant.now()))
                }
                enqueueOperation(resource.accountKey, SyncEntityType.MEMO, SyncOperationType.UPSERT, memoId)
            }
        }
    }

    /** Standalone upload (editor staging): row (+ ATTACHMENT UPSERT for sync accounts). */
    suspend fun insertStandaloneResource(resource: ResourceEntity, sync: Boolean = true) {
        transactionRunner.inTransaction {
            memoDao.insertResource(resource)
            if (sync) {
                enqueueOperation(
                    resource.accountKey,
                    SyncEntityType.ATTACHMENT,
                    SyncOperationType.UPSERT,
                    resource.identifier,
                )
            }
        }
    }

    /**
     * Resource removal: deletes the row, marks the owning memo dirty and
     * enqueues the memo update before the attachment delete (reference
     * released first). Returns the stale local file URI, if any.
     */
    suspend fun detachResource(resource: ResourceEntity, sync: Boolean = true): String? {
        var staleFile: String? = null
        transactionRunner.inTransaction {
            staleFile = localFileUriOf(resource)
            memoDao.deleteResource(resource)

            val memoId = resource.memoId
            if (sync && !memoId.isNullOrBlank()) {
                memoDao.getMemoById(memoId, resource.accountKey)?.let { memo ->
                    memoDao.insertMemo(memo.copy(needsSync = true, lastModified = Instant.now()))
                }
                enqueueOperation(resource.accountKey, SyncEntityType.MEMO, SyncOperationType.UPSERT, memoId)
            }
            if (sync && resource.remoteId != null) {
                enqueueOperation(
                    resource.accountKey,
                    SyncEntityType.ATTACHMENT,
                    SyncOperationType.DELETE,
                    resource.identifier,
                    payload = resource.remoteId,
                )
            }
        }
        return staleFile
    }

    // -----------------------------------------------------------------------
    // Outbox
    // -----------------------------------------------------------------------

    suspend fun operations(accountKey: String): List<SyncOperationEntity> =
        syncOperationDao.getOperations(accountKey)

    suspend fun deleteOperation(id: String) = syncOperationDao.delete(id)

    /** Re-queues a failed operation with its updated attempt bookkeeping. */
    suspend fun requeueOperation(operation: SyncOperationEntity) = syncOperationDao.enqueue(operation)

    private suspend fun enqueueOperation(
        accountKey: String,
        entityType: SyncEntityType,
        operation: SyncOperationType,
        entityId: String,
        payload: String? = null,
    ) {
        syncOperationDao.enqueue(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                accountKey = accountKey,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payload = payload,
                createdAt = Instant.now(),
            )
        )
    }

    // -----------------------------------------------------------------------
    // Local file URIs (computed here; deletion stays with callers)
    // -----------------------------------------------------------------------

    private fun localFileUriOf(resource: ResourceEntity): String? {
        return resource.localUri ?: resource.uri.takeIf { it.startsWith("file:") }
    }

    private fun fileExists(uriString: String?): Boolean {
        val path = uriString?.let(::fileUriToPath) ?: return false
        return File(path).exists()
    }
}

/**
 * Local path behind a file:// URI string, percent-decoded, or null for other
 * schemes. java.net.URI decodes both hierarchical ("file:///a%20b") and opaque
 * ("file:C:%5CUsers" as produced by Uri.fromFile on some JVMs) forms; the
 * manual fallback covers strings URI cannot parse.
 */
internal fun fileUriToPath(uriString: String): String? {
    if (!uriString.startsWith("file:")) return null
    try {
        val uri = URI.create(uriString)
        if (uri.isOpaque) {
            uri.schemeSpecificPart?.let { return it }
        } else {
            uri.path?.let { return it }
        }
    } catch (_: Throwable) {
        // fall through to manual parsing
    }
    var rest = uriString.substringAfter(':')
    if (rest.startsWith("//")) rest = rest.substring(2)
    return rest.takeIf { it.isNotBlank() }
}
