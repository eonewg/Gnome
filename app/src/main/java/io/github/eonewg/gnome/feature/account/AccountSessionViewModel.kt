package io.github.eonewg.gnome.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.LocalAccount
import io.github.eonewg.gnome.data.account.LoginCompatibility as ServiceLoginCompatibility
import io.github.eonewg.gnome.data.service.AccountService
import okhttp3.OkHttpClient
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * The account session behind the login/settings pages: the account list, the
 * active account and the session operations, all funneled through
 * [AccountService] so the UI never touches the store directly.
 */
@HiltViewModel
class AccountSessionViewModel @Inject constructor(
    private val accountService: AccountService,
) : ViewModel() {

    val accounts: StateFlow<List<Account>> =
        accountService.accounts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentAccount: StateFlow<Account?> =
        accountService.currentAccount.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val okHttpClient: OkHttpClient get() = accountService.httpClient

    suspend fun hasAnyAccount(): Boolean = accountService.accounts.first().isNotEmpty()

    suspend fun switchAccount(accountKey: String): ApiResponse<Unit> =
        withContext(viewModelScope.coroutineContext) {
            try {
                accountService.switchAccount(accountKey)
                ApiResponse.Success(Unit)
            } catch (e: Throwable) {
                ApiResponse.exception(e)
            }
        }

    suspend fun logout(accountKey: String) {
        accountService.removeAccount(accountKey)
    }

    suspend fun addLocalAccount(): ApiResponse<Unit> = withContext(viewModelScope.coroutineContext) {
        try {
            accountService.addAccount(
                Account.Local(
                    LocalAccount(startDateEpochSecond = Instant.now().epochSecond)
                )
            )
            ApiResponse.Success(Unit)
        } catch (e: Throwable) {
            ApiResponse.exception(e)
        }
    }

    suspend fun checkLoginCompatibility(host: String): LoginCompatibility =
        withContext(viewModelScope.coroutineContext) {
            try {
                when (val compatibility = accountService.checkLoginCompatibility(host)) {
                    is ServiceLoginCompatibility.Supported -> LoginCompatibility.Supported
                    is ServiceLoginCompatibility.Unsupported -> LoginCompatibility.Unsupported(compatibility.message)
                    is ServiceLoginCompatibility.RequiresConfirmation -> LoginCompatibility.RequiresConfirmation(compatibility.message)
                }
            } catch (e: Throwable) {
                LoginCompatibility.Unsupported(e.localizedMessage ?: e.message ?: "")
            }
        }

    suspend fun loginMemosWithAccessToken(
        host: String,
        accessToken: String,
        accountLabel: String = "",
        allowHigherV1Version: Boolean = false,
    ): ApiResponse<Unit> = accountService.loginMemosWithAccessToken(
        host = host,
        accessToken = accessToken,
        accountLabel = accountLabel,
        allowHigherV1Version = allowHigherV1Version,
    )
}

sealed class LoginCompatibility {
    object Supported : LoginCompatibility()
    data class Unsupported(val message: String) : LoginCompatibility()
    data class RequiresConfirmation(val message: String) : LoginCompatibility()
}