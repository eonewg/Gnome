package io.github.eonewg.gnome.feature.memo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.toCore
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoActions
import io.github.eonewg.gnome.data.service.MemoService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The single-memo detail view: the memo is picked from the account's live
 * memo flow so edits and deletions propagate immediately (and a deleted memo
 * pops the page, as the legacy home snapshot did).
 *
 * The memo id is supplied once by the typed navigation key through Hilt's
 * assisted factory, so the first collected state already targets that memo.
 */
@HiltViewModel(assistedFactory = MemoDetailViewModel.Factory::class)
class MemoDetailViewModel @AssistedInject constructor(
    @Assisted private val memoId: String,
    private val memoService: MemoService,
    private val accountService: AccountService,
    private val memoActions: MemoActions,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(memoId: String): MemoDetailViewModel
    }

    val uiState: StateFlow<MemoDetailUiState> = combine(
        memoService.domainMemos,
        accountService.currentAccount,
    ) { memos, account ->
        MemoDetailUiState(
            memo = memos.firstOrNull { it.id == memoId },
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

    suspend fun updateMemoContent(content: String): ApiResponse<Memo> =
        memoActions.updateContent(memoId, content)

    suspend fun updateMemoPinned(pinned: Boolean): ApiResponse<Memo> =
        memoActions.updatePinned(memoId, pinned)

    suspend fun archiveMemo(): ApiResponse<Unit> =
        memoActions.archive(memoId)

    suspend fun deleteMemo(): ApiResponse<Unit> =
        memoActions.delete(memoId)

    suspend fun cacheResourceFile(resourceId: String, uri: Uri): ApiResponse<Unit> =
        memoActions.cacheResource(resourceId, uri)

    suspend fun downloadAndCacheResource(resource: Attachment): Uri? =
        memoActions.downloadAndCache(resource)
}