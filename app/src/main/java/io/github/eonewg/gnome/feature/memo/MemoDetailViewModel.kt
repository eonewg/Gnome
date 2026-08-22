package io.github.eonewg.gnome.feature.memo

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.toCore
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.ResourceRepresentable
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoActions
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The single-memo detail view: the memo is picked from the account's live
 * memo flow so edits and deletions propagate immediately (and a deleted memo
 * pops the page, as the legacy home snapshot did).
 */
@HiltViewModel
class MemoDetailViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    private val memoActions: MemoActions,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val memoId: String? = savedStateHandle["memoId"]

    val uiState: StateFlow<MemoDetailUiState> = combine(
        memoService.domainMemos,
        accountService.currentAccount,
    ) { memos, account ->
        MemoDetailUiState(
            memo = memoId?.let { id -> memos.firstOrNull { it.id == id } },
            isRemoteAccount = account !is Account.Local,
            host = when (account) {
                is Account.MemosV0 -> account.info.host
                is Account.MemosV1 -> account.info.host
                else -> null
            },
            defaultVisibility = account?.toUser()?.defaultVisibility?.toCore(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoDetailUiState(),
    )

    // -----------------------------------------------------------------------
    // Single-memo operations; the live memo flow re-emits after every write.
    // -----------------------------------------------------------------------

    suspend fun updateMemoContent(content: String): ApiResponse<Memo> {
        val id = memoId ?: return ApiResponse.exception(IllegalStateException("No memo id"))
        return memoActions.updateContent(id, content)
    }

    suspend fun updateMemoPinned(pinned: Boolean): ApiResponse<Memo> {
        val id = memoId ?: return ApiResponse.exception(IllegalStateException("No memo id"))
        return memoActions.updatePinned(id, pinned)
    }

    suspend fun archiveMemo(): ApiResponse<Unit> {
        val id = memoId ?: return ApiResponse.exception(IllegalStateException("No memo id"))
        return memoActions.archive(id)
    }

    suspend fun deleteMemo(): ApiResponse<Unit> {
        val id = memoId ?: return ApiResponse.exception(IllegalStateException("No memo id"))
        return memoActions.delete(id)
    }

    suspend fun cacheResourceFile(resourceId: String, uri: Uri): ApiResponse<Unit> =
        memoActions.cacheResource(resourceId, uri)

    suspend fun downloadAndCacheResource(resource: ResourceRepresentable): Uri? =
        memoActions.downloadAndCache(resource)
}