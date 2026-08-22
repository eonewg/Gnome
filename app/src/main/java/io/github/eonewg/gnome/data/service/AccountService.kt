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
import io.github.eonewg.gnome.data.model.UserSettings
import io.github.eonewg.gnome.data.repository.AbstractMemoRepository
import io.github.eonewg.gnome.data.remote.RemoteDataSource
import io.github.eonewg.gnome.data.repository.MemoRepository
import io.github.eonewg.gnome.ext.settingsDataStore
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
    private val secureTokenStorage: SecureTokenStorage,
    private val syncScheduler: SyncScheduler,
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

    val accounts = context.settingsDataStore.data.map { settings ->
        settings.usersList.mapNotNull(::parseAccountWithSecureToken)
    }

    val currentAccount = context.settingsDataStore.data.map { settings ->
        settings.usersList.firstOrNull { it.accountKey == settings.currentUser }
            ?.let(::parseAccountWithSecureToken)
    }

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
            updateAccountFromSyncedUser(account.accountKey(), user)
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
            val account = accounts.first().firstOrNull { it.accountKey() == accountKey }
            context.settingsDataStore.updateData { settings ->
                settings.copy(currentUser = accountKey)
            }
            updateCurrentAccount(account)
        }
    }

    suspend fun addAccount(account: Account) {
        awaitInitialization()
        mutex.withLock {
            persistAccessToken(account)
            context.settingsDataStore.updateData { settings ->
                val users = settings.usersList.toMutableList()
                val index = users.indexOfFirst { it.accountKey == account.accountKey() }
                val currentSettings = users.getOrNull(index)?.settings ?: UserSettings()
                if (index != -1) {
                    users.removeAt(index)
                }
                users.add(account.toPersistedUserData(currentSettings))
                settings.copy(
                    usersList = users,
                    currentUser = account.accountKey(),
                )
            }
            updateCurrentAccount(account)
        }
    }

    suspend fun removeAccount(accountKey: String) {
        awaitInitialization()
        mutex.withLock {
            context.settingsDataStore.updateData { settings ->
                val users = settings.usersList.toMutableList()
                val index = users.indexOfFirst { it.accountKey == accountKey }
                if (index != -1) {
                    users.removeAt(index)
                }
                val newCurrentUser = if (settings.currentUser == accountKey) {
                    users.firstOrNull()?.accountKey ?: ""
                } else {
                    settings.currentUser
                }
                settings.copy(
                    usersList = users,
                    currentUser = newCurrentUser,
                )
            }
            updateCurrentAccount(currentAccount.first())
            purgeAccountData(accountKey)
            secureTokenStorage.removeToken(accountKey)
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

    private suspend fun updateAccountFromSyncedUser(accountKey: String, user: User) {
        mutex.withLock {
            context.settingsDataStore.updateData { settings ->
                val index = settings.usersList.indexOfFirst { it.accountKey == accountKey }
                if (index == -1) {
                    return@updateData settings
                }
                val existingUser = settings.usersList[index]
                val current = parseAccountWithSecureToken(existingUser) ?: return@updateData settings
                val updated = current.withUser(user)
                val users = settings.usersList.toMutableList()
                users[index] = updated.toPersistedUserData(existingUser.settings)
                settings.copy(usersList = users)
            }
        }
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
                val accepted = isUnsupportedSyncVersionAccepted(account.accountKey(), serverVersion.version)
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
        mutex.withLock {
            context.settingsDataStore.updateData { settings ->
                val users = settings.usersList.toMutableList()
                val index = users.indexOfFirst { it.accountKey == accountKey }
                if (index == -1) {
                    return@updateData settings
                }
                val user = users[index]
                val versions = (user.settings.acceptedUnsupportedSyncVersions + version).distinct()
                users[index] = user.copy(
                    settings = user.settings.copy(acceptedUnsupportedSyncVersions = versions)
                )
                settings.copy(usersList = users)
            }
        }
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
                val version = createMemosV0Client(account.info.host, account.info.accessToken)
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
                val version = createMemosV1Client(account.info.host, account.info.accessToken)
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

    private suspend fun isUnsupportedSyncVersionAccepted(accountKey: String, version: String): Boolean {
        val userData = context.settingsDataStore.data.first()
            .usersList
            .firstOrNull { it.accountKey == accountKey }
            ?: return false
        return userData.settings.acceptedUnsupportedSyncVersions.contains(version)
    }

    private fun parseAccountWithSecureToken(userData: UserData): Account? {
        val account = Account.parseUserData(userData) ?: return null
        val token = secureTokenStorage.getToken(userData.accountKey)
            .orEmpty()
        return when (account) {
            is Account.MemosV0 -> Account.MemosV0(account.info.copy(accessToken = token))
            is Account.MemosV1 -> Account.MemosV1(account.info.copy(accessToken = token))
            is Account.Local -> account
        }
    }

    private fun Account.toPersistedUserData(settings: UserSettings): UserData {
        return when (this) {
            is Account.MemosV0 -> UserData(
                settings = settings,
                accountKey = accountKey(),
                memosV0 = info.copy(accessToken = "")
            )
            is Account.MemosV1 -> UserData(
                settings = settings,
                accountKey = accountKey(),
                memosV1 = info.copy(accessToken = "")
            )
            is Account.Local -> UserData(
                settings = settings,
                accountKey = accountKey(),
                local = info
            )
        }
    }

    private fun persistAccessToken(account: Account) {
        when (account) {
            is Account.MemosV0 -> secureTokenStorage.saveToken(account.accountKey(), account.info.accessToken)
            is Account.MemosV1 -> secureTokenStorage.saveToken(account.accountKey(), account.info.accessToken)
            is Account.Local -> Unit
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
