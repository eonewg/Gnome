package io.github.eonewg.gnome.data.account

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.User
import io.github.eonewg.gnome.data.model.UserData
import io.github.eonewg.gnome.data.model.UserSettings
import io.github.eonewg.gnome.ext.settingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of account persistence: the DataStore users list plus the
 * encrypted access tokens held by [SecureTokenStorage]. Exposes fully
 * hydrated [Account]s (token merged back in) and per-account settings.
 * Building repositories, clients or sessions is deliberately not its job.
 */
@Singleton
class AccountStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureTokenStorage: SecureTokenStorage,
) {
    val accounts: Flow<List<Account>> = context.settingsDataStore.data.map { settings ->
        settings.usersList.mapNotNull(::parseAccountWithSecureToken)
    }

    val currentAccount: Flow<Account?> = context.settingsDataStore.data.map { settings ->
        settings.usersList.firstOrNull { it.accountKey == settings.currentUser }
            ?.let(::parseAccountWithSecureToken)
    }

    suspend fun currentAccountSnapshot(): Account? = currentAccount.first()

    suspend fun findAccount(accountKey: String): Account? =
        accounts.first().firstOrNull { it.accountKey() == accountKey }

    suspend fun setCurrentAccountKey(accountKey: String) {
        context.settingsDataStore.updateData { settings ->
            settings.copy(currentUser = accountKey)
        }
    }

    /**
     * Persists the account's token and entry and makes it current. An
     * existing entry with the same key is replaced, keeping its
     * per-account settings.
     */
    suspend fun addAccount(account: Account) {
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
    }

    /**
     * Removes the account's entry and encrypted token, re-pointing the
     * current-user marker at the first remaining account (empty when none
     * remain). Returns the account that should be current afterwards.
     */
    suspend fun removeAccount(accountKey: String): Account? {
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
            settings.copy(usersList = users, currentUser = newCurrentUser)
        }
        secureTokenStorage.removeToken(accountKey)
        return currentAccount.first()
    }

    /** Refreshes the persisted user info of [accountKey] from a server-synced [user]. */
    suspend fun updateAccountUser(accountKey: String, user: User) {
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

    /** Records that the user accepted syncing against an above-range server version. */
    suspend fun rememberAcceptedUnsupportedSyncVersion(accountKey: String, version: String) {
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

    suspend fun isUnsupportedSyncVersionAccepted(accountKey: String, version: String): Boolean {
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
}
