package io.github.eonewg.gnome.data.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.eonewg.gnome.data.local.FileStorage
import io.github.eonewg.gnome.data.local.GnomeDatabase
import io.github.eonewg.gnome.data.local.LocalMemoDataSource
import io.github.eonewg.gnome.data.local.RoomTransactionRunner
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.LocalAccount
import io.github.eonewg.gnome.data.repository.AbstractMemoRepository
import io.github.eonewg.gnome.data.repository.MemoRepository
import io.github.eonewg.gnome.data.repository.MemoRepositoryImpl
import io.github.eonewg.gnome.data.remote.RemoteDataSource
import io.github.eonewg.gnome.sync.SyncScheduler
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the current account's live objects: the feature repository, its
 * remote data source and the authenticated HTTP client — rebuilt whenever
 * the current account changes. Also rebuilds transient syncing
 * repositories for background workers purely from persisted state
 * (accountKey + [AccountStore] + Room), which is what makes SyncWorker
 * survive process death.
 */
@Singleton
class AccountSession @Inject constructor(
    private val accountStore: AccountStore,
    private val remoteDataSourceFactory: RemoteDataSourceFactory,
    private val database: GnomeDatabase,
    private val fileStorage: FileStorage,
    private val syncScheduler: SyncScheduler,
    private val baseHttpClient: OkHttpClient,
) {
    data class MemoRepositoryHandle(
        val repository: MemoRepository,
        /** True when the caller owns the repository and must close it after use. */
        val ownsLifecycle: Boolean,
    )

    @Volatile
    var httpClient: OkHttpClient = baseHttpClient
        private set

    @Volatile
    private var repository: AbstractMemoRepository = MemoRepositoryImpl(
        localMemoDataSource(),
        fileStorage,
        Account.Local(LocalAccount()),
    )

    @Volatile
    private var remoteRepository: RemoteDataSource? = null

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialization = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                refresh(accountStore.currentAccountSnapshot())
                initialization.complete(Unit)
            } catch (e: Throwable) {
                initialization.completeExceptionally(e)
            }
        }
    }

    suspend fun awaitInitialization() {
        initialization.await()
    }

    suspend fun getRepository(): AbstractMemoRepository {
        awaitInitialization()
        return mutex.withLock { repository }
    }

    suspend fun getRemoteDataSource(): RemoteDataSource? {
        awaitInitialization()
        return mutex.withLock { remoteRepository }
    }

    /**
     * Rebuilds the live objects for the account that just became current
     * (null or Local falls back to local-only mode). Callers are expected
     * to have awaited initialization; the init path itself calls this
     * before initialization completes.
     */
    suspend fun refresh(account: Account?) {
        mutex.withLock { refreshLocked(account) }
    }

    /**
     * Resolves a syncing repository for any persisted remote account — the
     * foundation of SyncWorker's process recovery. The active account reuses
     * its live repository; other accounts are rebuilt on demand from the
     * persisted config + token store + Room, with
     * [MemoRepositoryHandle.ownsLifecycle] telling the caller to close the
     * transient instance when done. Local-only and unknown accounts return
     * null (nothing to sync).
     */
    suspend fun getSyncRepository(accountKey: String): MemoRepositoryHandle? {
        awaitInitialization()
        mutex.withLock {
            val active = repository
            if (active is MemoRepository && active.syncEnabled && active.accountKeyValue == accountKey) {
                return MemoRepositoryHandle(active, ownsLifecycle = false)
            }

            val account = accountStore.accounts.first().firstOrNull { it.accountKey() == accountKey }
                ?: return null
            return when (account) {
                is Account.MemosV0, is Account.MemosV1 -> {
                    val remote = remoteDataSourceFactory.create(account) ?: return null
                    MemoRepositoryHandle(
                        buildMemoRepository(remote.remoteDataSource, account),
                        ownsLifecycle = true,
                    )
                }
                is Account.Local -> null
            }
        }
    }

    /** Drops all persisted data (rows, outbox, files) of a removed account. */
    suspend fun purgeAccountData(accountKey: String) {
        val memoDao = database.memoDao()
        memoDao.deleteResourcesByAccount(accountKey)
        memoDao.deleteMemosByAccount(accountKey)
        database.syncOperationDao().deleteAllForAccount(accountKey)
        fileStorage.deleteAccountFiles(accountKey)
    }

    private fun refreshLocked(account: Account?) {
        repository.close()
        when (account) {
            null, is Account.Local -> {
                this.repository = MemoRepositoryImpl(
                    localMemoDataSource(),
                    fileStorage,
                    account ?: Account.Local(LocalAccount()),
                )
                this.remoteRepository = null
                this.httpClient = baseHttpClient
            }
            is Account.MemosV0, is Account.MemosV1 -> {
                val remote = remoteDataSourceFactory.create(account)
                    ?: error("RemoteDataSourceFactory returned null for ${account.accountKey()}")
                this.repository = buildMemoRepository(remote.remoteDataSource, account)
                this.remoteRepository = remote.remoteDataSource
                this.httpClient = remote.httpClient
            }
        }
    }

    private fun localMemoDataSource(): LocalMemoDataSource =
        LocalMemoDataSource(
            database.memoDao(),
            database.syncOperationDao(),
            RoomTransactionRunner(database),
        )

    private fun buildMemoRepository(remote: RemoteDataSource, account: Account): MemoRepositoryImpl {
        return MemoRepositoryImpl(
            localMemoDataSource(),
            fileStorage,
            account,
            syncScheduler,
            remote,
        ) { user ->
            accountStore.updateAccountUser(account.accountKey(), user)
        }
    }
}
