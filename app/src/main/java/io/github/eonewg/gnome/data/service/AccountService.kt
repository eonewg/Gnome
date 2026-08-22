package io.github.eonewg.gnome.data.service

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.getOrThrow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.data.account.AccountStore
import io.github.eonewg.gnome.data.account.MemosClientFactory
import io.github.eonewg.gnome.data.account.RemoteDataSourceFactory
import io.github.eonewg.gnome.data.api.MemosV0Api
import io.github.eonewg.gnome.data.api.MemosV1Api
import io.github.eonewg.gnome.data.constant.MemosVersionSupport
import io.github.eonewg.gnome.data.constant.MemosVersionSupport.MEMOS_V0_MIN_VERSION
import io.github.eonewg.gnome.data.constant.MemosVersionSupport.MEMOS_V1_MAX_VERSION
import io.github.eonewg.gnome.data.constant.MemosVersionSupport.MEMOS_V1_MIN_VERSION
import io.github.eonewg.gnome.data.local.FileStorage
import io.github.eonewg.gnome.data.local.GnomeDatabase
import io.github.eonewg.gnome.data.local.LocalMemoDataSource
import io.github.eonewg.gnome.data.local.RoomTransactionRunner
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.LocalAccount
import io.github.eonewg.gnome.data.model.User
import io.github.eonewg.gnome.data.model.UserData
import io.github.eonewg.gnome.data.repository.AbstractMemoRepository
import io.github.eonewg.gnome.data.remote.RemoteDataSource
import io.github.eonewg.gnome.data.repository.MemoRepository
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.sync.SyncScheduler
import net.swiftzer.semver.SemVer
import okhttp3.OkHttpClient
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val database: GnomeDatabase,
    private val fileStorage: FileStorage,
    private val syncScheduler: SyncScheduler,
    private val accountStore: AccountStore,
    private val memosClientFactory: MemosClientFactory,
    private val remoteDataSourceFactory: RemoteDataSourceFactory,
) {
    sealed class LoginCompatibility {
        data class Supported(val accountCase: UserData.AccountCase) : LoginCompatibility()
        data class Unsupported(val message: String) : LoginCompatibility()
        data class RequiresConfirmation(
            val accountCase: UserData.AccountCase,
            val version: String,
            val message: String,
        ) : LoginCompatibility()
    }

    sealed class SyncCompatibility {
        object Allowed : SyncCompatibility()
        data class Blocked(val message: String?) : SyncCompatibility()
        data class RequiresConfirmation(val version: String, val message: String) : SyncCompatibility()
    }

    private data class ServerVersionInfo(
        val accountCase: UserData.AccountCase,
        val version: String,
    )

    private enum class VersionPolicy {
        SUPPORTED,
        TOO_LOW,
        V1_HIGHER,
    }

    private val exportDateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss", Locale.US)
        .withZone(ZoneId.systemDefault())

    @Volatile
    var httpClient: OkHttpClient = okHttpClient
        private set

    val accounts = accountStore.accounts

    val currentAccount = accountStore.currentAccount

    @Volatile
    private var repository: AbstractMemoRepository = MemoRepository(
        localMemoDataSource(),
        fileStorage,
        Account.Local(LocalAccount()),
    )

    @Volatile
    private var remoteRepository: RemoteDataSource? = null

    private val mutex = Mutex()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialization = CompletableDeferred<Unit>()

    init {
        serviceScope.launch {
            try {
                mutex.withLock {
                    updateCurrentAccount(currentAccount.first())
                }
                initialization.complete(Unit)
            } catch (e: Throwable) {
                initialization.completeExceptionally(e)
            }
        }
    }

    private fun updateCurrentAccount(account: Account?) {
        repository.close()
        when (account) {
            null, is Account.Local -> {
                this.repository = MemoRepository(localMemoDataSource(), fileStorage, account ?: Account.Local(LocalAccount()))
                this.remoteRepository = null
                httpClient = okHttpClient
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

    private fun buildMemoRepository(remote: RemoteDataSource, account: Account): MemoRepository {
        return MemoRepository(
            localMemoDataSource(),
            fileStorage,
            account,
            syncScheduler,
            remote,
        ) { user ->
            accountStore.updateAccountUser(account.accountKey(), user)
        }
    }

    /**
     * Resolves a syncing repository for any persisted remote account — the
     * foundation of SyncWorker's process recovery. The active account reuses
     * its live repository; other accounts are rebuilt on demand from the
     * persisted config + token store + Room, with [MemoRepositoryHandle.ownsLifecycle]
     * telling the caller to close the transient instance when done. Local-only
     * and unknown accounts return null (nothing to sync).
     */
    suspend fun getSyncRepository(accountKey: String): MemoRepositoryHandle? {
        awaitInitialization()
        mutex.withLock {
            val active = repository
            if (active is MemoRepository && active.syncEnabled && active.accountKeyValue == accountKey) {
                return MemoRepositoryHandle(active, ownsLifecycle = false)
            }

            val account = accounts.first().firstOrNull { it.accountKey() == accountKey }
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

    data class MemoRepositoryHandle(
        val repository: MemoRepository,
        /** True when the caller owns the repository and must close it after use. */
        val ownsLifecycle: Boolean,
    )

    suspend fun switchAccount(accountKey: String) {
        awaitInitialization()
        mutex.withLock {
            accountStore.setCurrentAccountKey(accountKey)
            updateCurrentAccount(accountStore.findAccount(accountKey))
        }
    }

    suspend fun addAccount(account: Account) {
        awaitInitialization()
        mutex.withLock {
            accountStore.addAccount(account)
            updateCurrentAccount(account)
        }
    }

    suspend fun removeAccount(accountKey: String) {
        awaitInitialization()
        mutex.withLock {
            val newCurrentAccount = accountStore.removeAccount(accountKey)
            updateCurrentAccount(newCurrentAccount)
            purgeAccountData(accountKey)
        }
    }

    suspend fun exportLocalAccountZip(destinationUri: Uri) {
        val accountKey = Account.Local().accountKey()
        val memoDao = database.memoDao()
        val memos = memoDao.getAllMemosForSync(accountKey)
            .filterNot { it.isDeleted }
            .sortedWith(compareBy({ it.date }, { it.content }))

        if (memos.isEmpty()) {
            throw IllegalStateException("No local memos to export")
        }

        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                val collisionMap = hashMapOf<String, Int>()
                for (memo in memos) {
                    val memoBaseName = uniqueMemoBaseName(memo.date, collisionMap)
                    zip.putNextEntry(ZipEntry("$memoBaseName.md"))
                    zip.write(memo.content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    val resources = memoDao.getMemoResources(memo.identifier, accountKey)
                        .sortedWith(compareBy<ResourceEntity>({ it.filename }, { it.uri }))
                    resources.forEachIndexed { index, resource ->
                        val sourceFile = localFileForResource(resource)
                            ?: throw IllegalStateException("Missing resource file: ${resource.filename}")
                        if (!sourceFile.exists()) {
                            throw IllegalStateException("Missing resource file: ${resource.filename}")
                        }
                        val ext = exportFileExtension(resource, sourceFile)
                        val attachmentName = if (ext.isBlank()) {
                            "$memoBaseName-${index + 1}"
                        } else {
                            "$memoBaseName-${index + 1}.$ext"
                        }
                        zip.putNextEntry(ZipEntry(attachmentName))
                        sourceFile.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: throw IllegalStateException("Unable to open export destination")
    }

    private fun uniqueMemoBaseName(date: Instant, collisionMap: MutableMap<String, Int>): String {
        val base = exportDateFormatter.format(date)
        val count = collisionMap[base] ?: 0
        collisionMap[base] = count + 1
        return if (count == 0) base else "${base}_$count"
    }

    private fun localFileForResource(resource: ResourceEntity): File? {
        val uri = (resource.localUri ?: resource.uri).toUri()
        if (uri.scheme != "file") {
            return null
        }
        val path = uri.path ?: return null
        return File(path)
    }

    private fun exportFileExtension(resource: ResourceEntity, sourceFile: File): String {
        val filenameExt = resource.filename.substringAfterLast('.', "")
        if (filenameExt.isNotBlank()) {
            return filenameExt.lowercase(Locale.US)
        }
        val sourceExt = sourceFile.extension
        if (sourceExt.isNotBlank()) {
            return sourceExt.lowercase(Locale.US)
        }
        val fromMime = resource.mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return fromMime?.lowercase(Locale.US) ?: ""
    }

    private suspend fun purgeAccountData(accountKey: String) {
        val memoDao = database.memoDao()
        memoDao.deleteResourcesByAccount(accountKey)
        memoDao.deleteMemosByAccount(accountKey)
        database.syncOperationDao().deleteAllForAccount(accountKey)
        fileStorage.deleteAccountFiles(accountKey)
    }

    fun createMemosV0Client(host: String, accessToken: String?): Pair<OkHttpClient, MemosV0Api> {
        return memosClientFactory.createV0Client(host, accessToken)
    }

    fun createMemosV1Client(host: String, accessToken: String?): Pair<OkHttpClient, MemosV1Api> {
        return memosClientFactory.createV1Client(host, accessToken)
    }

    suspend fun checkLoginCompatibility(host: String, allowHigherV1Version: Boolean = false): LoginCompatibility {
        val serverVersion = detectAccountCaseAndVersion(host)
        return when (evaluateVersionPolicy(serverVersion)) {
            VersionPolicy.SUPPORTED -> LoginCompatibility.Supported(serverVersion.accountCase)
            VersionPolicy.TOO_LOW -> LoginCompatibility.Unsupported(MemosVersionSupport.supportedVersionsMessage(context))
            VersionPolicy.V1_HIGHER -> {
                if (allowHigherV1Version) {
                    LoginCompatibility.Supported(serverVersion.accountCase)
                } else {
                    LoginCompatibility.RequiresConfirmation(
                        accountCase = serverVersion.accountCase,
                        version = serverVersion.version,
                        message = R.string.memos_login_version_higher_warning.string,
                    )
                }
            }
        }
    }

    suspend fun checkCurrentAccountSyncCompatibility(
        isAutomatic: Boolean,
        allowHigherV1Version: String? = null,
    ): SyncCompatibility {
        awaitInitialization()
        val account = currentAccount.first() ?: return SyncCompatibility.Allowed
        if (account !is Account.MemosV0 && account !is Account.MemosV1) {
            return SyncCompatibility.Allowed
        }

        val serverVersion = fetchVersionForAccount(account)
            ?: return if (isAutomatic) {
                SyncCompatibility.Blocked(null)
            } else {
                SyncCompatibility.Blocked(MemosVersionSupport.supportedVersionsMessage(context))
            }
        return when (evaluateVersionPolicy(serverVersion)) {
            VersionPolicy.SUPPORTED -> SyncCompatibility.Allowed
            VersionPolicy.TOO_LOW -> {
                if (isAutomatic) {
                    SyncCompatibility.Blocked(null)
                } else {
                    SyncCompatibility.Blocked(MemosVersionSupport.supportedVersionsMessage(context))
                }
            }
            VersionPolicy.V1_HIGHER -> {
                val accepted = accountStore.isUnsupportedSyncVersionAccepted(account.accountKey(), serverVersion.version)
                if (isAutomatic) {
                    return if (accepted) {
                        SyncCompatibility.Allowed
                    } else {
                        SyncCompatibility.Blocked(null)
                    }
                }
                if (allowHigherV1Version == serverVersion.version) {
                    return SyncCompatibility.Allowed
                }
                if (accepted) {
                    return SyncCompatibility.Allowed
                }
                SyncCompatibility.RequiresConfirmation(
                    version = serverVersion.version,
                    message = R.string.memos_sync_version_higher_warning.string,
                )
            }
        }
    }

    suspend fun rememberAcceptedUnsupportedSyncVersion(version: String) {
        awaitInitialization()
        val accountKey = currentAccount.first()?.accountKey() ?: return
        accountStore.rememberAcceptedUnsupportedSyncVersion(accountKey, version)
    }

    suspend fun detectAccountCase(host: String): UserData.AccountCase {
        return detectAccountCaseAndVersion(host).accountCase
    }

    suspend fun getRepository(): AbstractMemoRepository {
        awaitInitialization()
        mutex.withLock {
            return repository
        }
    }

    suspend fun getRemoteDataSource(): RemoteDataSource? {
        awaitInitialization()
        mutex.withLock {
            return remoteRepository
        }
    }

    private suspend fun detectAccountCaseAndVersion(host: String): ServerVersionInfo {
        val memosV0Status = createMemosV0Client(host, null).second.status().getOrNull()
        val memosV0Version = memosV0Status?.profile?.version?.trim().orEmpty()
        if (memosV0Version.isNotEmpty()) {
            return ServerVersionInfo(UserData.AccountCase.MEMOS_V0, memosV0Version)
        }

        val memosV1Profile = createMemosV1Client(host, null).second.getProfile().getOrThrow()
        val memosV1Version = memosV1Profile.version.trim()
        if (memosV1Version.isNotEmpty()) {
            return ServerVersionInfo(UserData.AccountCase.MEMOS_V1, memosV1Version)
        }

        return ServerVersionInfo(UserData.AccountCase.ACCOUNT_NOT_SET, "")
    }

    private suspend fun fetchVersionForAccount(account: Account): ServerVersionInfo? {
        return when (account) {
            is Account.MemosV0 -> {
                val version = memosClientFactory.createV0Client(account.info.host, account.info.accessToken)
                    .second
                    .status()
                    .getOrNull()
                    ?.profile
                    ?.version
                    ?.trim()
                    .orEmpty()
                if (version.isBlank()) null else ServerVersionInfo(UserData.AccountCase.MEMOS_V0, version)
            }
            is Account.MemosV1 -> {
                val version = memosClientFactory.createV1Client(account.info.host, account.info.accessToken)
                    .second
                    .getProfile()
                    .getOrNull()
                    ?.version
                    ?.trim()
                    .orEmpty()
                if (version.isBlank()) null else ServerVersionInfo(UserData.AccountCase.MEMOS_V1, version)
            }
            else -> null
        }
    }

    private suspend fun awaitInitialization() {
        initialization.await()
    }

    private fun evaluateVersionPolicy(serverVersion: ServerVersionInfo): VersionPolicy {
        val versionName = serverVersion.version.trim()
        val version = SemVer.parseOrNull(versionName)
        return when (serverVersion.accountCase) {
            UserData.AccountCase.MEMOS_V0 -> {
                when {
                    versionName.isEmpty() -> VersionPolicy.TOO_LOW
                    version == null -> VersionPolicy.SUPPORTED
                    version < MEMOS_V0_MIN_VERSION -> VersionPolicy.TOO_LOW
                    else -> VersionPolicy.SUPPORTED
                }
            }
            UserData.AccountCase.MEMOS_V1 -> {
                when {
                    versionName.isEmpty() -> VersionPolicy.TOO_LOW
                    version == null -> VersionPolicy.V1_HIGHER
                    version < MEMOS_V1_MIN_VERSION -> VersionPolicy.TOO_LOW
                    version > MEMOS_V1_MAX_VERSION -> VersionPolicy.V1_HIGHER
                    else -> VersionPolicy.SUPPORTED
                }
            }
            else -> VersionPolicy.TOO_LOW
        }
    }
}
