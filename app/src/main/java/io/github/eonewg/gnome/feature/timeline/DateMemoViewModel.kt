package io.github.eonewg.gnome.feature.timeline

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.toCore
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoActions
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Memos for one calendar day, streamed from the account's live memo flow;
 * the date filter itself stays in [io.github.eonewg.gnome.ui.page.memos.MemosList].
 */
@HiltViewModel
class DateMemoViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    private val memoActions: MemoActions,
) : ViewModel() {

    val uiState: StateFlow<DateMemoUiState> = combine(
        memoService.domainMemos,
        accountService.currentAccount,
    ) { memos, account ->
        memos to account
    }
        .map { (memos, account) ->
            DateMemoUiState(memos = memos).withAccount(account)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DateMemoUiState(),
        )

    // -----------------------------------------------------------------------
    // Single-memo operations; the live memo flow re-emits after every write.
    // -----------------------------------------------------------------------

    suspend fun updateMemoContent(memoId: String, content: String): ApiResponse<Memo> =
        memoActions.updateContent(memoId, content)

    suspend fun updateMemoPinned(memoId: String, pinned: Boolean): ApiResponse<Memo> =
        memoActions.updatePinned(memoId, pinned)

    suspend fun archiveMemo(memoId: String): ApiResponse<Unit> = memoActions.archive(memoId)

    suspend fun deleteMemo(memoId: String): ApiResponse<Unit> = memoActions.delete(memoId)

    suspend fun cacheResourceFile(resourceId: String, uri: Uri): ApiResponse<Unit> =
        memoActions.cacheResource(resourceId, uri)

    suspend fun downloadAndCacheResource(resource: Attachment): Uri? =
        memoActions.downloadAndCache(resource)

    private fun DateMemoUiState.withAccount(account: Account?): DateMemoUiState = copy(
        isRemoteAccount = account !is Account.Local,
        host = when (account) {
            is Account.MemosV0 -> account.info.host
            is Account.MemosV1 -> account.info.host
            else -> null
        },
        defaultVisibility = account?.toUser()?.defaultVisibility?.toCore(),
    )
}