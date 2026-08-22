package io.github.eonewg.gnome.data.account

import android.content.Context
import com.skydoves.sandwich.getOrNull
import com.skydoves.sandwich.getOrThrow
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.data.constant.MemosVersionSupport
import io.github.eonewg.gnome.data.constant.MemosVersionSupport.MEMOS_V0_MIN_VERSION
import io.github.eonewg.gnome.data.constant.MemosVersionSupport.MEMOS_V1_MAX_VERSION
import io.github.eonewg.gnome.data.constant.MemosVersionSupport.MEMOS_V1_MIN_VERSION
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.UserData
import io.github.eonewg.gnome.ext.string
import net.swiftzer.semver.SemVer
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * The only place that knows which Memos server versions Gnome supports and
 * how to detect the version a host runs. Probes the server through
 * [MemosClientFactory] and consults [AccountStore] for the user's accepted
 * above-range sync versions; produces login/sync compatibility verdicts.
 */
@Singleton
class ServerCompatibilityChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clientFactory: MemosClientFactory,
    private val accountStore: AccountStore,
) {
    internal data class ServerVersionInfo(
        val accountCase: UserData.AccountCase,
        val version: String,
    )

    private enum class VersionPolicy {
        SUPPORTED,
        TOO_LOW,
        V1_HIGHER,
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

    /**
     * Verdict for syncing [account] against its own server. Local accounts
     * and null accounts are always allowed (nothing to negotiate).
     */
    suspend fun checkAccountSyncCompatibility(
        account: Account,
        isAutomatic: Boolean,
        allowHigherV1Version: String? = null,
    ): SyncCompatibility {
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

    suspend fun detectAccountCase(host: String): UserData.AccountCase {
        return detectAccountCaseAndVersion(host).accountCase
    }

    private suspend fun detectAccountCaseAndVersion(host: String): ServerVersionInfo {
        val memosV0Status = clientFactory.createV0Client(host, null).second.status().getOrNull()
        val memosV0Version = memosV0Status?.profile?.version?.trim().orEmpty()
        if (memosV0Version.isNotEmpty()) {
            return ServerVersionInfo(UserData.AccountCase.MEMOS_V0, memosV0Version)
        }

        val memosV1Profile = clientFactory.createV1Client(host, null).second.getProfile().getOrThrow()
        val memosV1Version = memosV1Profile.version.trim()
        if (memosV1Version.isNotEmpty()) {
            return ServerVersionInfo(UserData.AccountCase.MEMOS_V1, memosV1Version)
        }

        return ServerVersionInfo(UserData.AccountCase.ACCOUNT_NOT_SET, "")
    }

    private suspend fun fetchVersionForAccount(account: Account): ServerVersionInfo? {
        return when (account) {
            is Account.MemosV0 -> {
                val version = clientFactory.createV0Client(account.info.host, account.info.accessToken)
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
                val version = clientFactory.createV1Client(account.info.host, account.info.accessToken)
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
