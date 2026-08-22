package io.github.eonewg.gnome.feature.tag

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Memos carrying one tag, streamed straight from the Room tag index instead
 * of filtering the timeline snapshot in memory. The tag arrives via
 * [setTag] whenever the destination is re-entered with a new argument.
 */
@HiltViewModel
class TagMemoViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    private val memoActions: MemoActions,
) : ViewModel() {

    private val tag = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TagMemoUiState> = combine(
        tag,
        accountService.currentAccount,
    ) { current, account ->
        current to account
    }
        .flatMapLatest { (current, account) ->
            if (current == null) {
                flowOf(TagMemoUiState().withAccount(account))
            } else {
                memoService.getMemoRepository().observeMemosByTag(current).map { memos ->
                    TagMemoUiState(tag = current, memos = memos).withAccount(account)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TagMemoUiState(),
        )

    fun setTag(tag: String) {
        this.tag.value = tag
    }

    // -----------------------------------------------------------------------
    // Single-memo operations; the Room tag flow re-emits after every write.
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

    private fun TagMemoUiState.withAccount(account: Account?): TagMemoUiState = copy(
        isRemoteAccount = account !is Account.Local,
        host = when (account) {
            is Account.MemosV0 -> account.info.host
            is Account.MemosV1 -> account.info.host
            else -> null
        },
        defaultVisibility = account?.toUser()?.defaultVisibility?.toCore(),
    )
}