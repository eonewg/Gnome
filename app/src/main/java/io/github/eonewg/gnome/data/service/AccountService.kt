package io.github.eonewg.gnome.data.service

import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.eonewg.gnome.data.account.AccountExportService
import io.github.eonewg.gnome.data.account.AccountSession
import io.github.eonewg.gnome.data.account.AccountStore
import io.github.eonewg.gnome.data.account.LoginCompatibility
import io.github.eonewg.gnome.data.account.MemosClientFactory
import io.github.eonewg.gnome.data.account.ServerCompatibilityChecker
import io.github.eonewg.gnome.data.account.SyncCompatibility
import io.github.eonewg.gnome.data.api.MemosV0Api
import io.github.eonewg.gnome.data.api.MemosV1Api
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.UserData
import io.github.eonewg.gnome.data.repository.AbstractMemoRepository
import io.github.eonewg.gnome.data.remote.RemoteDataSource
import io.github.eonewg.gnome.data.repository.MemoRepository
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Account-facing facade over the data/account components:
 *  - [AccountStore] — persistence of accounts and encrypted tokens
 *  - [AccountSession] — live repository/HTTP client per current account,
 *    plus worker recovery via [getSyncRepository]
 *  - [MemosClientFactory] — authenticated OkHttp/Retrofit construction
 *  - [ServerCompatibilityChecker] — server version detection & policy
 *  - [AccountExportService] — local account ZIP export
 *
 * Multi-step operations (switch/add/remove) are serialized by [mutex] in
 * the order persistence-write → session rebuild → purge, matching the
 * pre-split behavior.
 */
// Open for the ViewModel unit tests, which subclass with in-memory fakes.
@Singleton
open class AccountService @Inject constructor(
    private val accountStore: AccountStore,
    private val session: AccountSession,
    private val memosClientFactory: MemosClientFactory,
    private val compatibilityChecker: ServerCompatibilityChecker,
    private val exportService: AccountExportService,
) {
    private val mutex = Mutex()

    val accounts get() = accountStore.accounts

    open val currentAccount get() = accountStore.currentAccount

    val httpClient: OkHttpClient get() = session.httpClient

    suspend fun switchAccount(accountKey: String) {
        session.awaitInitialization()
        mutex.withLock {
            accountStore.setCurrentAccountKey(accountKey)
            session.refresh(accountStore.findAccount(accountKey))
        }
    }

    suspend fun addAccount(account: Account) {
        session.awaitInitialization()
        mutex.withLock {
            accountStore.addAccount(account)
            session.refresh(account)
        }
    }

    suspend fun removeAccount(accountKey: String) {
        session.awaitInitialization()
        mutex.withLock {
            val newCurrentAccount = accountStore.removeAccount(accountKey)
            session.refresh(newCurrentAccount)
            session.purgeAccountData(accountKey)
        }
    }

    suspend fun getRepository(): AbstractMemoRepository = session.getRepository()

    /** The current account's repository under the domain-typed contract. */
    suspend fun getMemoRepository(): MemoRepository = session.getMemoRepository()

    suspend fun getRemoteDataSource(): RemoteDataSource? = session.getRemoteDataSource()

    suspend fun getSyncRepository(accountKey: String): AccountSession.MemoRepositoryHandle? =
        session.getSyncRepository(accountKey)

    fun createMemosV0Client(host: String, accessToken: String?): Pair<OkHttpClient, MemosV0Api> {
        return memosClientFactory.createV0Client(host, accessToken)
    }

    fun createMemosV1Client(host: String, accessToken: String?): Pair<OkHttpClient, MemosV1Api> {
        return memosClientFactory.createV1Client(host, accessToken)
    }

    suspend fun checkLoginCompatibility(host: String, allowHigherV1Version: Boolean = false): LoginCompatibility {
        return compatibilityChecker.checkLoginCompatibility(host, allowHigherV1Version)
    }

    open suspend fun checkCurrentAccountSyncCompatibility(
        isAutomatic: Boolean,
        allowHigherV1Version: String? = null,
    ): SyncCompatibility {
        session.awaitInitialization()
        val account = currentAccount.first() ?: return SyncCompatibility.Allowed
        return compatibilityChecker.checkAccountSyncCompatibility(account, isAutomatic, allowHigherV1Version)
    }

    open suspend fun rememberAcceptedUnsupportedSyncVersion(version: String) {
        session.awaitInitialization()
        val accountKey = currentAccount.first()?.accountKey() ?: return
        accountStore.rememberAcceptedUnsupportedSyncVersion(accountKey, version)
    }

    suspend fun detectAccountCase(host: String): UserData.AccountCase {
        return compatibilityChecker.detectAccountCase(host)
    }

    suspend fun exportLocalAccountZip(destinationUri: Uri) {
        exportService.exportLocalAccountZip(destinationUri)
    }
}
