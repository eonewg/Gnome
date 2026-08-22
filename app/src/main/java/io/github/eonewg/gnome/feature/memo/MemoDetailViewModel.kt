package io.github.eonewg.gnome.feature.memo

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The single-memo detail view: the memo is picked from the account's live
 * memo flow so edits and deletions propagate immediately (and a deleted memo
 * pops the page, as the legacy home snapshot did).
 *
 * The memo id is supplied by the route (typed navigation key), not by a
 * SavedStateHandle: the entry decorators still provide the handle for any
 * UI-scoped state, but navigation arguments travel through [setMemoId].
 */
@HiltViewModel
class MemoDetailViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    private val memoActions: MemoActions,
) : ViewModel() {

    private val memoId = MutableStateFlow<String?>(null)

    fun setMemoId(id: String) {
        memoId.value = id
    }

    val uiState: StateFlow<MemoDetailUiState> = combine(
        memoId,
        memoService.domainMemos,
        accountService.currentAccount,
    ) { id, memos, account ->
        MemoDetailUiState(
            memo = id?.let { memoId -> memos.firstOrNull { it.id == memoId } },
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

    private suspend fun currentMemoId(): String =
        memoId.value ?: throw IllegalStateException("No memo id")

    suspend fun updateMemoContent(content: String): ApiResponse<Memo> =
        memoActions.updateContent(currentMemoId(), content)

    suspend fun updateMemoPinned(pinned: Boolean): ApiResponse<Memo> =
        memoActions.updatePinned(currentMemoId(), pinned)

    suspend fun archiveMemo(): ApiResponse<Unit> =
        memoActions.archive(currentMemoId())

    suspend fun deleteMemo(): ApiResponse<Unit> =
        memoActions.delete(currentMemoId())

    suspend fun cacheResourceFile(resourceId: String, uri: Uri): ApiResponse<Unit> =
        memoActions.cacheResource(resourceId, uri)

    suspend fun downloadAndCacheResource(resource: Attachment): Uri? =
        memoActions.downloadAndCache(resource)
}