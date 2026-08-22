package io.github.eonewg.gnome.feature

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.skydoves.sandwich.ApiResponse
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.data.account.AccountExportService
import io.github.eonewg.gnome.data.account.AccountSession
import io.github.eonewg.gnome.data.account.AccountStore
import io.github.eonewg.gnome.data.account.MemosClientFactory
import io.github.eonewg.gnome.data.account.RemoteDataSourceFactory
import io.github.eonewg.gnome.data.account.SecureTokenStorage
import io.github.eonewg.gnome.data.account.ServerCompatibilityChecker
import io.github.eonewg.gnome.data.account.SyncCompatibility
import io.github.eonewg.gnome.data.local.FileStorage
import io.github.eonewg.gnome.data.local.GnomeDatabase
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.SyncStatus
import io.github.eonewg.gnome.data.model.TagUsage
import io.github.eonewg.gnome.data.repository.MemoRepository
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoService
import io.github.eonewg.gnome.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * In-memory fakes for the feature ViewModels (timeline/editor). The services
 * subclass the real `open` singletons; the super constructor gets dormant real
 * dependencies that no test ever exercises — everything the ViewModel touches
 * is overridden below.
 */
internal class FakeAccountService private constructor(
    accountStore: AccountStore,
    session: AccountSession,
    clientFactory: MemosClientFactory,
    checker: ServerCompatibilityChecker,
    export: AccountExportService,
) : AccountService(accountStore, session, clientFactory, checker, export) {

    val accountState = MutableStateFlow<Account?>(Account.Local())

    /** Programmable verdict for [checkCurrentAccountSyncCompatibility]. */
    var syncCompatibility: SyncCompatibility = SyncCompatibility.Allowed

    val rememberedAcceptedVersions = mutableListOf<String>()

    override val currentAccount: Flow<Account?> = accountState

    override suspend fun checkCurrentAccountSyncCompatibility(
        isAutomatic: Boolean,
        allowHigherV1Version: String?,
    ): SyncCompatibility = syncCompatibility

    override suspend fun rememberAcceptedUnsupportedSyncVersion(version: String) {
        rememberedAcceptedVersions.add(version)
    }

    companion object {
        fun build(context: Context): FakeAccountService {
            val accountStore = AccountStore(context, SecureTokenStorage(context))
            val clientFactory = MemosClientFactory(OkHttpClient())
            val database = Room.inMemoryDatabaseBuilder(context, GnomeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            return FakeAccountService(
                accountStore,
                AccountSession(
                    accountStore,
                    RemoteDataSourceFactory(clientFactory),
                    database,
                    FileStorage(context),
                    SyncScheduler(context),
                    OkHttpClient(),
                ),
                clientFactory,
                ServerCompatibilityChecker(context, clientFactory, accountStore),
                AccountExportService(context, database),
            )
        }
    }
}

internal class FakeMemoService(
    accountService: AccountService,
    val repository: FakeMemoRepository,
) : MemoService(accountService) {

    val domainMemoState = MutableStateFlow<List<Memo>>(emptyList())
    val syncStatusState = MutableStateFlow(SyncStatus())

    var syncCalls = 0
    var syncResult: ApiResponse<Unit> = ApiResponse.Success(Unit)

    override val domainMemos: Flow<List<Memo>> = domainMemoState

    override val syncStatus: Flow<SyncStatus> = syncStatusState

    override suspend fun getMemoRepository(): MemoRepository = repository

    override suspend fun sync(force: Boolean): ApiResponse<Unit> {
        syncCalls++
        return syncResult
    }
}

/** Records every call; create/update/delete mutate an in-memory map unless [failWrites]. */
internal class FakeMemoRepository : MemoRepository {

    data class CreateCall(val content: String, val visibility: MemoVisibility)
    data class UpdateCall(
        val identifier: String,
        val content: String?,
        val attachments: List<Attachment>?,
        val visibility: MemoVisibility?,
        val pinned: Boolean?,
    )

    override val accountKeyValue: String = "fake-account"
    override val syncEnabled: Boolean = false
    override val syncStatus = MutableStateFlow(SyncStatus())

    val memosById = linkedMapOf<String, Memo>()
    var nextId = 1

    val createCalls = mutableListOf<CreateCall>()
    val updateCalls = mutableListOf<UpdateCall>()
    val deletedIds = mutableListOf<String>()

    var failWrites = false
    var tagsResult: ApiResponse<List<String>> = ApiResponse.Success(emptyList())

    /** While set, write calls suspend until completed — lets tests observe mid-batch state. */
    var writeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    private suspend fun awaitGate() {
        writeGate?.await()
    }

    private fun memo(id: String): Memo =
        memosById.getValue(id)

    override fun observeTimeline(): Flow<List<Memo>> = MutableStateFlow(memosById.values.toList())

    override suspend fun getMemo(identifier: String): Memo? = memosById[identifier]

    override suspend fun getAttachment(identifier: String): Attachment? = null

    override suspend fun listArchived(): ApiResponse<List<Memo>> =
        ApiResponse.Success(memosById.values.filter { it.archived })

    override suspend fun listTags(): ApiResponse<List<String>> = tagsResult

    /** Programmable Room-index source; the editor subscriptions consume this. */
    val tagUsageState = MutableStateFlow<List<TagUsage>>(emptyList())

    val memosByTagState = MutableStateFlow<List<Memo>>(emptyList())

    var suggestionsResult: List<TagUsage> = emptyList()

    override fun observeTagsFlow(): Flow<List<TagUsage>> = tagUsageState

    override fun observeMemosByTag(tag: String): Flow<List<Memo>> = memosByTagState

    override suspend fun getTagSuggestions(query: String): List<TagUsage> = suggestionsResult

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        attachments: List<Attachment>,
    ): ApiResponse<Memo> {
        createCalls.add(CreateCall(content, visibility))
        if (failWrites) return ApiResponse.Failure.Exception(IllegalStateException("write failed"))
        val memo = Memo(
            id = "new-${nextId++}",
            content = content,
            date = Instant.EPOCH,
            visibility = visibility,
            attachments = attachments,
        )
        memosById[memo.id] = memo
        return ApiResponse.Success(memo)
    }

    override suspend fun updateMemo(
        identifier: String,
        content: String?,
        attachments: List<Attachment>?,
        visibility: MemoVisibility?,
        pinned: Boolean?,
    ): ApiResponse<Memo> {
        updateCalls.add(UpdateCall(identifier, content, attachments, visibility, pinned))
        awaitGate()
        if (failWrites) return ApiResponse.Failure.Exception(IllegalStateException("write failed"))
        val updated = memo(identifier).copy(
            content = content ?: memo(identifier).content,
            attachments = attachments ?: memo(identifier).attachments,
            visibility = visibility ?: memo(identifier).visibility,
            pinned = pinned ?: memo(identifier).pinned,
        )
        memosById[identifier] = updated
        return ApiResponse.Success(updated)
    }

    override suspend fun deleteMemo(identifier: String): ApiResponse<Unit> {
        deletedIds.add(identifier)
        awaitGate()
        if (failWrites) return ApiResponse.Failure.Exception(IllegalStateException("write failed"))
        memosById.remove(identifier)
        return ApiResponse.Success(Unit)
    }

    override suspend fun archiveMemo(identifier: String): ApiResponse<Unit> {
        memosById[identifier] = memo(identifier).copy(archived = true)
        return ApiResponse.Success(Unit)
    }

    override suspend fun restoreMemo(identifier: String): ApiResponse<Unit> {
        memosById[identifier] = memo(identifier).copy(archived = false)
        return ApiResponse.Success(Unit)
    }

    override suspend fun createAttachment(
        filename: String,
        mimeType: String?,
        contentUri: Uri,
        memoIdentifier: String?,
    ): ApiResponse<Attachment> = ApiResponse.Failure.Exception(UnsupportedOperationException())

    override suspend fun deleteAttachment(identifier: String): ApiResponse<Unit> =
        ApiResponse.Success(Unit)

    override suspend fun cacheAttachmentFile(identifier: String, downloadedUri: Uri): ApiResponse<Unit> =
        ApiResponse.Success(Unit)

    override suspend fun sync(): ApiResponse<Unit> = ApiResponse.Success(Unit)

    override fun close() {}
}
