package io.github.eonewg.gnome.sync

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import io.github.eonewg.gnome.data.constant.GnomeException
import io.github.eonewg.gnome.data.local.LocalMemoDataSource
import io.github.eonewg.gnome.data.local.fileUriToPath
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.local.entity.SyncEntityType
import io.github.eonewg.gnome.data.local.entity.SyncOperationEntity
import io.github.eonewg.gnome.data.local.entity.SyncOperationType
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.Memo
import io.github.eonewg.gnome.data.model.Resource
import io.github.eonewg.gnome.data.model.User
import io.github.eonewg.gnome.data.remote.RemoteDataSource
import io.github.eonewg.gnome.ext.getErrorMessage
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * The single synchronization algorithm. Owns no UI state and must only be invoked
 * through the per-account serialization its callers provide (the repository's
 * mutex covers both manual syncs and SyncWorker runs in this process).
 *
 * Reaches Room exclusively through [LocalMemoDataSource] and the server
 * exclusively through [RemoteDataSource].
 *
 * Strategy (kept from the original implementation, intentionally simple):
 * pull the full server snapshot, reconcile it against Room row-by-row using
 * [ConflictResolver], push local pending rows, then drain the durable outbox.
 */
class SyncEngine(
    private val localData: LocalMemoDataSource,
    private val fileStore: SyncFileStore,
    private val remoteRepository: RemoteDataSource,
    private val account: Account,
    private val onUserSynced: suspend (User) -> Unit = {},
) {
    private data class UploadedResourcesResult(
        val remoteResourceIds: List<String>,
        val failedUploads: Int,
    )

    private val accountKey = account.accountKey()
    var currentUser: User = account.toUser()
        private set
    private var pendingDetailedSyncError: String? = null

    suspend fun reconcile(): ApiResponse<Unit> {
        val currentUserSync = refreshCurrentUserFromRemoteStrict()
        if (currentUserSync !is ApiResponse.Success) {
            return currentUserSync
        }

        val remoteNormal = remoteRepository.listMemos()
        if (remoteNormal !is ApiResponse.Success) {
            return remoteNormal.mapFailureToUnit()
        }

        val remoteArchived = remoteRepository.listArchivedMemos()
        if (remoteArchived !is ApiResponse.Success) {
            return remoteArchived.mapFailureToUnit()
        }

        val remoteMemos = remoteNormal.data + remoteArchived.data

        // Remote ids known to exist on the server, including anything this
        // reconcile creates mid-run; the stale pull snapshot alone would purge
        // freshly pushed rows (e.g. conflict duplicates) until the next sync.
        val knownRemoteIds = remoteMemos.mapTo(hashSetOf()) { remoteMemoId(it) }

        var hadErrors = false
        var firstErrorMessage: String? = null
        fun recordFailure(message: String? = null) {
            hadErrors = true
            if (firstErrorMessage == null) {
                firstErrorMessage = message ?: consumeDetailedSyncError()
            }
        }

        val localMemos = localData.getAllForSync(accountKey)
        val localByRemoteId = localMemos.mapNotNull { memo ->
            memo.remoteId?.let { it to memo }
        }.toMap()

        for (remoteMemo in remoteMemos) {
            val remoteId = remoteMemoId(remoteMemo)
            val local = localByRemoteId[remoteId]
            val localResources = local?.let { localData.getMemoResources(it.identifier, accountKey) }
            val equivalent = local != null && ConflictResolver.memoEquivalent(local, remoteMemo, localResources.orEmpty())
            val remoteChanged = local != null && ConflictResolver.hasRemoteChanged(local.lastSyncedAt, remoteMemo)

            when (ConflictResolver.resolve(local?.toLocalState(), equivalent, remoteChanged)) {
                ConflictResolver.MemoDecision.APPLY_REMOTE ->
                    applyRemoteMemo(remoteMemo, local?.identifier, knownRemoteIds)
                ConflictResolver.MemoDecision.MARK_SYNCED ->
                    markSynced(local!!, remoteMemo)
                ConflictResolver.MemoDecision.PUSH_LOCAL -> {
                    if (!pushLocalMemo(local!!.identifier)) {
                        recordFailure()
                    }
                }
                ConflictResolver.MemoDecision.DUPLICATE -> {
                    if (!duplicateConflict(local!!, remoteMemo, knownRemoteIds)) {
                        recordFailure()
                    }
                }
                ConflictResolver.MemoDecision.DELETE_REMOTE -> {
                    val deleted = remoteRepository.deleteMemo(remoteId)
                    if (deleted is ApiResponse.Success) {
                        permanentlyDeleteMemo(local!!.identifier)
                    } else {
                        recordFailure(deleted.getErrorMessage())
                    }
                }
            }
        }

        val latestLocals = localData.getAllForSync(accountKey)
        for (local in latestLocals) {
            if (local.remoteId != null) {
                if (local.remoteId in knownRemoteIds) {
                    continue
                }
                if (local.isDeleted) {
                    permanentlyDeleteMemo(local.identifier)
                } else if (local.needsSync) {
                    if (!pushLocalMemo(local.identifier, forceCreate = true, knownRemoteIds = knownRemoteIds)) {
                        recordFailure()
                    }
                } else {
                    permanentlyDeleteMemo(local.identifier)
                }
                continue
            }

            if (local.isDeleted) {
                permanentlyDeleteMemo(local.identifier)
            } else if (local.needsSync) {
                if (!pushLocalMemo(local.identifier, forceCreate = true, knownRemoteIds = knownRemoteIds)) {
                    recordFailure()
                }
            }
        }

        val outboxError = processOutbox()
        if (outboxError != null) {
            recordFailure(outboxError)
        }

        return if (hadErrors) {
            ApiResponse.Failure.Exception(
                Exception(firstErrorMessage ?: "Sync finished with partial failures")
            )
        } else {
            ApiResponse.Success(Unit)
        }
    }

    /**
     * Drains the durable outbox. Memo operations converge on the row's latest
     * state, so they are safe to process in any order and after any reconcile.
     * Rounds re-read the table so operations enqueued mid-drain are picked up;
     * a round that deletes nothing means the remainder is stuck (e.g. offline)
     * and waits for the next scheduled run instead of spinning.
     * Returns the first error message, or null when everything succeeded.
     */
    suspend fun processOutbox(): String? {
        var firstError: String? = null

        while (true) {
            val operations = localData.operations(accountKey)
            if (operations.isEmpty()) break

            var deletedThisRound = 0
            for (operation in operations) {
                val opError = try {
                    processOperation(operation)
                } catch (e: Throwable) {
                    e.message ?: e.javaClass.simpleName
                }
                if (opError == null) {
                    localData.deleteOperation(operation.id)
                    deletedThisRound += 1
                } else {
                    firstError = firstError ?: opError
                    localData.requeueOperation(operation.copy(
                        attemptCount = operation.attemptCount + 1,
                        lastAttemptAt = Instant.now(),
                        lastError = opError,
                    ))
                }
            }
            if (deletedThisRound == 0) break
        }
        return firstError
    }

    private suspend fun processOperation(operation: SyncOperationEntity): String? {
        pendingDetailedSyncError = null
        return when (operation.entityType) {
            SyncEntityType.MEMO -> processMemoOperation(operation)
            SyncEntityType.ATTACHMENT -> processAttachmentOperation(operation)
        }
    }

    private suspend fun processMemoOperation(operation: SyncOperationEntity): String? {
        val memo = localData.getMemo(operation.entityId, accountKey) ?: return null
        return when (operation.operation) {
            SyncOperationType.UPSERT -> {
                if (!memo.needsSync && !memo.isDeleted) return null
                if (pushLocalMemo(memo.identifier)) null else operationFailed()
            }
            SyncOperationType.DELETE -> {
                if (!memo.isDeleted) return null
                if (pushLocalMemo(memo.identifier)) null else operationFailed()
            }
        }
    }

    private suspend fun processAttachmentOperation(operation: SyncOperationEntity): String? {
        return when (operation.operation) {
            SyncOperationType.UPSERT -> {
                val resource = localData.getResource(operation.entityId, accountKey) ?: return null
                if (resource.remoteId != null) return null
                if (pushLocalResource(resource.identifier)) null else operationFailed()
            }
            SyncOperationType.DELETE -> {
                val remoteId = operation.payload ?: return null
                val deleted = remoteRepository.deleteResource(remoteId)
                when {
                    deleted is ApiResponse.Success -> null
                    deleted is ApiResponse.Failure.Error && deleted.rawStatusCode() == 404 -> null
                    else -> deleted.getErrorMessage()
                }
            }
        }
    }

    private fun operationFailed(): String {
        return pendingDetailedSyncError ?: "Sync operation failed"
    }

    private suspend fun refreshCurrentUserFromRemoteStrict(): ApiResponse<Unit> {
        val remoteUser = try {
            remoteRepository.getCurrentUser()
        } catch (e: Throwable) {
            return ApiResponse.Failure.Exception(e)
        }

        return when (remoteUser) {
            is ApiResponse.Success -> {
                currentUser = remoteUser.data
                try {
                    onUserSynced(remoteUser.data)
                    ApiResponse.Success(Unit)
                } catch (e: Throwable) {
                    ApiResponse.Failure.Exception(e)
                }
            }
            is ApiResponse.Failure.Error -> {
                val code = remoteUser.rawStatusCode()
                if (code == 401 || code == 403) {
                    ApiResponse.Failure.Exception(GnomeException.accessTokenInvalid)
                } else {
                    remoteUser.mapFailureToUnit()
                }
            }
            is ApiResponse.Failure.Exception -> remoteUser.mapFailureToUnit()
        }
    }

    private suspend fun pushLocalMemo(identifier: String, forceCreate: Boolean = false, knownRemoteIds: MutableSet<String>? = null): Boolean {
        pendingDetailedSyncError = null
        val local = localData.getMemo(identifier, accountKey) ?: return true

        if (local.isDeleted) {
            return if (local.remoteId != null) {
                val deleted = remoteRepository.deleteMemo(local.remoteId)
                if (deleted is ApiResponse.Success) {
                    permanentlyDeleteMemo(local.identifier)
                    true
                } else {
                    false
                }
            } else {
                permanentlyDeleteMemo(local.identifier)
                true
            }
        }

        val uploadedResources = ensureUploadedResources(local)
        if (uploadedResources.failedUploads > 0) {
            pendingDetailedSyncError = ATTACHMENT_UPLOAD_FAILED_MESSAGE
            return false
        }
        val remoteResourceIds = uploadedResources.remoteResourceIds

        return if (!forceCreate && local.remoteId != null) {
            val updated = remoteRepository.updateMemo(
                remoteId = local.remoteId,
                content = local.content,
                resourceRemoteIds = remoteResourceIds,
                visibility = local.visibility,
                pinned = local.pinned,
                archived = local.archived
            )
            if (updated is ApiResponse.Success) {
                applyRemoteMemo(
                    updated.data.copy(archived = local.archived),
                    preferredLocalIdentifier = local.identifier,
                    knownRemoteIds = knownRemoteIds,
                )
                true
            } else {
                false
            }
        } else {
            val created = remoteRepository.createMemo(
                content = local.content,
                visibility = local.visibility,
                resourceRemoteIds = remoteResourceIds,
                tags = null,
                createdAt = local.date
            )
            if (created !is ApiResponse.Success) {
                return false
            }

            val createdRemoteId = remoteMemoId(created.data)

            applyRemoteMemo(
                created.data.copy(remoteId = createdRemoteId),
                preferredLocalIdentifier = local.identifier,
                knownRemoteIds = knownRemoteIds,
            )
            true
        }
    }

    private suspend fun duplicateConflict(local: MemoEntity, remoteMemo: Memo, knownRemoteIds: MutableSet<String>): Boolean {
        val duplicateLocal = local.copy(
            identifier = UUID.randomUUID().toString(),
            remoteId = null,
            needsSync = true,
            isDeleted = false,
            lastSyncedAt = null,
            lastModified = Instant.now()
        )

        localData.insertMemoWithClonedResources(accountKey, local.identifier, duplicateLocal)

        applyRemoteMemo(remoteMemo, local.identifier, knownRemoteIds)
        return pushLocalMemo(duplicateLocal.identifier, forceCreate = true, knownRemoteIds = knownRemoteIds)
    }

    private suspend fun ensureUploadedResources(localMemo: MemoEntity): UploadedResourcesResult {
        val resources = localData.getMemoResources(localMemo.identifier, accountKey)
        val uploaded = arrayListOf<String>()
        var failedUploads = 0

        for (resource in resources) {
            val ensured = ensureUploadedResource(resource, localMemo.remoteId)
            if (ensured?.remoteId != null) {
                uploaded.add(ensured.remoteId)
            } else if (resource.remoteId == null) {
                failedUploads += 1
            }
        }

        return UploadedResourcesResult(uploaded, failedUploads)
    }

    private suspend fun ensureUploadedResource(
        resource: ResourceEntity,
        memoRemoteId: String?,
    ): ResourceEntity? {
        if (resource.remoteId != null) {
            return resource
        }

        val uriString = resource.localUri ?: resource.uri
        val path = fileUriToPath(uriString) ?: return null
        val file = File(path)
        if (!file.exists()) {
            return null
        }

        val uploaded = remoteRepository.createResource(
            filename = resource.filename,
            type = resource.mimeType?.toMediaTypeOrNull(),
            contentLength = file.length(),
            openInputStream = { file.inputStream() },
            memoRemoteId = memoRemoteId
        )

        val remoteResource = uploaded.getOrNull() ?: return null
        val synced = resource.copy(
            remoteId = remoteResourceId(remoteResource),
            uri = remoteResource.uri,
            localUri = resource.localUri ?: resource.uri
        )
        localData.upsertResource(synced)
        return synced
    }

    private suspend fun applyRemoteMemo(
        remoteMemo: Memo,
        preferredLocalIdentifier: String? = null,
        knownRemoteIds: MutableSet<String>? = null,
    ) {
        val remoteId = remoteMemoId(remoteMemo)
        knownRemoteIds?.add(remoteId)
        val current = localData.getMemoByRemoteId(remoteId, accountKey)
            ?: preferredLocalIdentifier?.let { localData.getMemo(it, accountKey) }

        val localIdentifier = current?.identifier ?: UUID.randomUUID().toString()
        val remoteUpdatedAt = remoteMemo.updatedAt ?: remoteMemo.date

        val entity = MemoEntity(
            identifier = localIdentifier,
            remoteId = remoteId,
            accountKey = accountKey,
            content = remoteMemo.content,
            date = remoteMemo.date,
            visibility = remoteMemo.visibility,
            pinned = remoteMemo.pinned,
            archived = remoteMemo.archived,
            needsSync = false,
            isDeleted = false,
            lastModified = remoteUpdatedAt,
            lastSyncedAt = remoteUpdatedAt
        )
        val remoteResources = remoteMemo.resources.map { resource ->
            ResourceEntity(
                identifier = remoteResourceId(resource),
                remoteId = remoteResourceId(resource),
                accountKey = accountKey,
                date = resource.date,
                filename = resource.filename,
                uri = resource.uri,
                localUri = null,
                mimeType = resource.mimeType,
                memoId = localIdentifier
            )
        }

        val staleFiles = localData.replaceSyncedMemo(entity, remoteResources)
        staleFiles.forEach(fileStore::deleteFile)
    }

    private suspend fun markSynced(local: MemoEntity, remoteMemo: Memo) {
        localData.upsertMemo(
            local.copy(
                remoteId = remoteMemoId(remoteMemo),
                date = remoteMemo.date,
                needsSync = false,
                isDeleted = false,
                archived = remoteMemo.archived,
                lastSyncedAt = remoteMemo.updatedAt ?: remoteMemo.date
            )
        )
    }

    private suspend fun pushLocalResource(identifier: String): Boolean {
        pendingDetailedSyncError = null
        val local = localData.getResource(identifier, accountKey) ?: return true
        val ensured = ensureUploadedResource(local, memoRemoteId = null)
            ?: run {
                pendingDetailedSyncError = ATTACHMENT_UPLOAD_FAILED_MESSAGE
                return false
            }
        return ensured.remoteId != null
    }

    private suspend fun permanentlyDeleteMemo(identifier: String) {
        val removedResources = localData.purgeMemo(identifier, accountKey)
        removedResources.forEach(::deleteLocalFile)
    }

    private fun deleteLocalFile(resource: ResourceEntity) {
        val uri = resource.localUri ?: resource.uri.takeIf { it.startsWith("file:") }
        if (uri != null) {
            fileStore.deleteFile(uri)
        }
    }

    private fun remoteMemoId(memo: Memo): String {
        return memo.remoteId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("RemoteDataSource must return memos with non-empty remoteId")
    }

    private fun remoteResourceId(resource: Resource): String {
        return resource.remoteId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("RemoteDataSource must return resources with non-empty remoteId")
    }

    private fun consumeDetailedSyncError(): String? {
        val message = pendingDetailedSyncError
        pendingDetailedSyncError = null
        return message
    }

    private fun <T> ApiResponse<T>.mapFailureToUnit(): ApiResponse<Unit> {
        return when (this) {
            is ApiResponse.Success -> ApiResponse.Success(Unit)
            is ApiResponse.Failure.Error -> ApiResponse.Failure.Error(this.payload)
            is ApiResponse.Failure.Exception -> ApiResponse.Failure.Exception(this.throwable)
        }
    }

    private fun MemoEntity.toLocalState() = ConflictResolver.LocalMemoState(
        isDeleted = isDeleted,
        needsSync = needsSync,
    )

    companion object {
        const val ATTACHMENT_UPLOAD_FAILED_MESSAGE =
            "Failed to upload one or more attachments during sync"
    }
}
