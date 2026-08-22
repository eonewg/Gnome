package io.github.eonewg.gnome.data.repository

import android.net.Uri
import androidx.core.net.toUri
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrNull
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.toDomain
import io.github.eonewg.gnome.data.local.FileStorage
import io.github.eonewg.gnome.data.local.LocalMemoDataSource
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.MemoWithResources
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.data.model.SyncStatus
import io.github.eonewg.gnome.data.model.User
import io.github.eonewg.gnome.data.remote.RemoteDataSource
import io.github.eonewg.gnome.sync.SyncEngine
import io.github.eonewg.gnome.sync.SyncFileStore
import io.github.eonewg.gnome.sync.SyncScheduler
import io.github.eonewg.gnome.ext.getErrorMessage
import io.github.eonewg.gnome.util.extractCustomTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Gnome's single feature-facing memo entry point, replacing the former
 * SyncingRepository / LocalDatabaseRepository split.
 *
 * One code path for every account kind:
 *  - writes: [LocalMemoDataSource] transaction (entity + outbox) → SyncScheduler
 *    for remote accounts; plain persistence for local-only accounts
 *  - reads: Room flows, optionally projected into the domain model via
 *    [observeTimeline]
 *  - sync: [SyncEngine] behind a mutex shared by manual sync and SyncWorker
 *
 * Local success IS user success; server push is WorkManager's job.
 */
class MemoRepository(
    private val localData: LocalMemoDataSource,
    private val fileStorage: FileStorage,
    private val account: Account,
    private val syncScheduler: SyncScheduler? = null,
    private val remote: RemoteDataSource? = null,
    private val onUserSynced: suspend (User) -> Unit = {},
) : AbstractMemoRepository() {

    val accountKeyValue: String get() = account.accountKey()

    /** True for remote (syncing) accounts; local-only accounts just persist. */
    val syncEnabled: Boolean = remote != null && account !is Account.Local

    private val engine: SyncEngine? = if (syncEnabled && remote != null) {
        SyncEngine(
            localData = localData,
            fileStore = SyncFileStore { uri -> fileStorage.deleteFile(uri.toUri()) },
            remoteRepository = remote,
            account = account,
            onUserSynced = onUserSynced,
        )
    } else {
        null
    }

    private val operationMutex = Mutex()

    // Local-only scope: derives the pending indicator from Room so every write
    // path (including SyncEngine's) is reflected without manual bookkeeping.
    // Never launches network work.
    private val statusScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _syncStatus = MutableStateFlow(SyncStatus())
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        if (syncEnabled) {
            statusScope.launch {
                localData.observeUnsyncedCount(accountKeyValue).collect { count ->
                    _syncStatus.update { it.copy(unsyncedCount = count) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /** Timeline as domain models — the read path new UI code should migrate to. */
    fun observeTimeline(): Flow<List<Memo>> {
        return localData.observeTimeline(accountKeyValue).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeMemos(): Flow<List<MemoEntity>> {
        return localData.observeTimeline(accountKeyValue).map { memos ->
            memos.map { it.toMemoEntity() }
        }
    }

    override suspend fun listMemos(): ApiResponse<List<MemoEntity>> {
        return try {
            ApiResponse.Success(localData.getTimeline(accountKeyValue).map { withResources(it) })
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun listArchivedMemos(): ApiResponse<List<MemoEntity>> {
        return try {
            val memos = localData.getArchived(accountKeyValue)
                .filterNot { it.isDeleted }
                .map { withResources(it) }
            ApiResponse.Success(memos)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun listTags(): ApiResponse<List<String>> {
        return try {
            val localTags = localData.getTimeline(accountKeyValue)
                .asSequence()
                .flatMap { extractCustomTags(it.content).asSequence() }
                .filter { it.isNotBlank() }
                .toSet()
            // Remote accounts refresh the tag list from the Memos instance
            // once per editor entry; network failures fall back to the
            // offline snapshot. Local accounts derive tags from content only.
            val remoteTags = if (remote != null) {
                try {
                    remote.listTags().getOrNull().orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            ApiResponse.Success(mergeTags(localTags, remoteTags))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun listResources(): ApiResponse<List<ResourceEntity>> {
        return try {
            ApiResponse.Success(localData.getAllResources(accountKeyValue))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun getCurrentUser(): ApiResponse<User> {
        return ApiResponse.Success(engine?.currentUser ?: account.toUser())
    }

    // -----------------------------------------------------------------------
    // Writes (transaction first, then schedule the push)
    // -----------------------------------------------------------------------

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resources: List<ResourceEntity>,
        tags: List<String>?
    ): ApiResponse<MemoEntity> {
        return try {
            val now = Instant.now()
            val localMemo = MemoEntity(
                identifier = UUID.randomUUID().toString(),
                remoteId = null,
                accountKey = accountKeyValue,
                content = content,
                date = now,
                visibility = visibility,
                pinned = false,
                archived = false,
                needsSync = syncEnabled,
                isDeleted = false,
                lastModified = now,
                lastSyncedAt = if (syncEnabled) null else now
            )
            localData.createLocalMemo(localMemo, resources, sync = syncEnabled)
            afterLocalWrite()
            ApiResponse.Success(withResources(localMemo))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun updateMemo(
        identifier: String,
        content: String?,
        resources: List<ResourceEntity>?,
        visibility: MemoVisibility?,
        tags: List<String>?,
        pinned: Boolean?
    ): ApiResponse<MemoEntity> {
        return try {
            val existingMemo = localData.getMemo(identifier, accountKeyValue)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))

            val updatedAt = Instant.now()
            val updatedMemo = existingMemo.copy(
                content = content ?: existingMemo.content,
                visibility = visibility ?: existingMemo.visibility,
                pinned = pinned ?: existingMemo.pinned,
                needsSync = syncEnabled,
                isDeleted = false,
                lastModified = updatedAt,
                lastSyncedAt = if (syncEnabled) existingMemo.lastSyncedAt else updatedAt
            )
            val staleFiles = localData.updateLocalMemo(updatedMemo, resources, sync = syncEnabled)
            deleteFilesAfterCommit(staleFiles)
            afterLocalWrite()
            ApiResponse.Success(withResources(updatedMemo))
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun deleteMemo(identifier: String): ApiResponse<Unit> {
        return try {
            val memo = localData.getMemo(identifier, accountKeyValue)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))
            if (syncEnabled) {
                localData.markMemoDeleted(memo)
            } else {
                // Local accounts have no server to tell; hard-delete instead
                // of accumulating tombstones nobody would ever drain.
                val removedResources = localData.purgeMemo(identifier, accountKeyValue)
                removedResources.forEach { deleteLocalFile(it) }
            }
            afterLocalWrite()
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun archiveMemo(identifier: String): ApiResponse<Unit> {
        return try {
            val memo = localData.getMemo(identifier, accountKeyValue)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))
            localData.setMemoArchived(memo, archived = true, sync = syncEnabled)
            afterLocalWrite()
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun restoreMemo(identifier: String): ApiResponse<Unit> {
        return try {
            val memo = localData.getMemo(identifier, accountKeyValue)
                ?: return ApiResponse.Failure.Exception(Exception("Memo not found"))
            localData.setMemoArchived(memo, archived = false, sync = syncEnabled)
            afterLocalWrite()
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentUri: Uri,
        memoIdentifier: String?
    ): ApiResponse<ResourceEntity> {
        return try {
            val uri = fileStorage.saveFile(
                accountKey = accountKeyValue,
                sourceUri = contentUri,
                filename = UUID.randomUUID().toString() + "_" + filename
            )
            val resource = ResourceEntity(
                identifier = UUID.randomUUID().toString(),
                remoteId = null,
                accountKey = accountKeyValue,
                date = Instant.now(),
                filename = filename,
                uri = uri.toString(),
                localUri = uri.toString(),
                mimeType = type?.toString(),
                memoId = memoIdentifier
            )
            if (!memoIdentifier.isNullOrBlank()) {
                localData.attachResourceToMemo(resource, memoIdentifier, markDirty = syncEnabled)
            } else {
                localData.insertStandaloneResource(resource, sync = syncEnabled)
            }
            afterLocalWrite()
            ApiResponse.Success(resource)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun deleteResource(identifier: String): ApiResponse<Unit> {
        return try {
            val resource = localData.getResource(identifier, accountKeyValue)
                ?: return ApiResponse.Failure.Exception(Exception("Resource not found"))

            val staleFile = localData.detachResource(resource, sync = syncEnabled)
            staleFile?.let { fileStorage.deleteFile(it.toUri()) }
            afterLocalWrite()
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    override suspend fun cacheResourceFile(identifier: String, downloadedUri: Uri): ApiResponse<Unit> {
        return try {
            val resource = localData.getResource(identifier, accountKeyValue)
                ?: return ApiResponse.Failure.Exception(Exception("Resource not found"))
            val existingLocal = existingLocalUri(resource)
            if (existingLocal != null) {
                return ApiResponse.Success(Unit)
            }

            val sourcePath = downloadedUri.path ?: return ApiResponse.Failure.Exception(Exception("Invalid downloaded file"))
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return ApiResponse.Failure.Exception(Exception("Downloaded file does not exist"))
            }

            val canonical = fileStorage.saveFile(
                accountKey = accountKeyValue,
                input = sourceFile.inputStream(),
                filename = "${resource.identifier}_${resource.filename}"
            ).toString()

            resource.localUri?.takeIf { it != canonical }?.let { oldLocal ->
                val oldUri = oldLocal.toUri()
                if (oldUri.scheme == "file") {
                    fileStorage.deleteFile(oldUri)
                }
            }

            val updatedUri = if (resource.remoteId == null && resource.uri.toUri().scheme == "file") {
                canonical
            } else {
                resource.uri
            }
            localData.upsertResource(
                resource.copy(
                    uri = updatedUri,
                    localUri = canonical
                )
            )
            ApiResponse.Success(Unit)
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    // -----------------------------------------------------------------------
    // Sync
    // -----------------------------------------------------------------------

    override suspend fun sync(): ApiResponse<Unit> {
        val syncEngine = engine ?: return ApiResponse.Success(Unit)
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                setSyncing(true)
                try {
                    val result = syncEngine.reconcile()
                    if (result is ApiResponse.Success) {
                        setSyncError(null)
                    } else {
                        setSyncError(result.getErrorMessage())
                    }
                    result
                } catch (e: Throwable) {
                    val failure = ApiResponse.Failure.Exception(e)
                    setSyncError(failure.getErrorMessage())
                    failure
                } finally {
                    setSyncing(false)
                }
            }
        }
    }

    override fun close() {
        statusScope.cancel()
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private suspend fun afterLocalWrite() {
        if (syncEnabled) {
            syncScheduler?.schedule(accountKeyValue)
        }
    }

    private fun deleteFilesAfterCommit(uris: List<String>) {
        uris.forEach { fileStorage.deleteFile(it.toUri()) }
    }

    private fun deleteLocalFile(resource: ResourceEntity) {
        val uri = resource.localUri ?: resource.uri.takeIf { it.startsWith("file:") }
        if (uri != null) {
            fileStorage.deleteFile(uri.toUri())
        }
    }

    private fun setSyncing(syncing: Boolean) {
        _syncStatus.update { it.copy(syncing = syncing) }
    }

    private fun setSyncError(message: String?) {
        _syncStatus.update { it.copy(errorMessage = message) }
    }

    private suspend fun withResources(memo: MemoEntity): MemoEntity {
        val resources = localData.getMemoResources(memo.identifier, accountKeyValue)
        return memo.copy().also { it.resources = resources }
    }

    private fun existingLocalUri(resource: ResourceEntity): Uri? {
        val local = resource.localUri ?: return null
        val uri = local.toUri()
        return if (uri.scheme == "file" && File(uri.path ?: "").exists()) uri else null
    }
}

internal fun mergeTags(localTags: Collection<String>, remoteTags: Collection<String>): List<String> {
    return (localTags + remoteTags)
        .asSequence()
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .toList()
}

private fun MemoWithResources.toMemoEntity(): MemoEntity {
    return memo.copy().also { it.resources = resources }
}
