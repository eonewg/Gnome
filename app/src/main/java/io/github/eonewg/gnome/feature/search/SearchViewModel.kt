package io.github.eonewg.gnome.feature.search

import android.net.Uri
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the database-backed search: every change of the query, the archived
 * toggle or the current account re-subscribes to the Room LIKE search of the
 * active account's repository, so results flow in without touching the
 * timeline snapshot or parsing memo content in memory.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    private val memoActions: MemoActions,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val includeArchived = MutableStateFlow(false)

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        includeArchived,
        accountService.currentAccount,
    ) { query, includeArchived, account ->
        Triple(query, includeArchived, account)
    }.flatMapLatest { (queryText, includeArchived, account) ->
        val trimmed = queryText.trim()
        if (account == null || trimmed.isEmpty()) {
            flowOf(SearchUiState(query = queryText, includeArchived = includeArchived).withAccount(account))
        } else {
            memoService.getMemoRepository()
                .observeSearch(query = trimmed, includeArchived = includeArchived)
                .map { memos ->
                    SearchUiState(
                        query = queryText,
                        includeArchived = includeArchived,
                        results = memos,
                        hasSearched = true,
                    ).withAccount(account)
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setIncludeArchived(value: Boolean) {
        includeArchived.value = value
    }

    // -----------------------------------------------------------------------
    // Single-memo operations; the Room search flow re-emits after every write.
    // -----------------------------------------------------------------------

    suspend fun updateMemoContent(memoId: String, content: String): ApiResponse<Memo> =
        memoActions.updateContent(memoId, content)

    suspend fun updateMemoPinned(memoId: String, pinned: Boolean): ApiResponse<Memo> =
        memoActions.updatePinned(memoId, pinned)

    suspend fun archiveMemo(memoId: String): ApiResponse<Unit> = memoActions.archive(memoId)

    suspend fun deleteMemo(memoId: String): ApiResponse<Unit> = memoActions.delete(memoId)

    suspend fun cacheResourceFile(resourceId: String, uri: Uri): ApiResponse<Unit> =
        memoActions.cacheResource(resourceId, uri)

    suspend fun downloadAndCacheResource(resource: ResourceRepresentable): Uri? =
        memoActions.downloadAndCache(resource)

    private fun SearchUiState.withAccount(account: Account?): SearchUiState = copy(
        isRemoteAccount = account !is Account.Local,
        host = when (account) {
            is Account.MemosV0 -> account.info.host
            is Account.MemosV1 -> account.info.host
            else -> null
        },
        defaultVisibility = account?.toUser()?.defaultVisibility?.toCore(),
    )
}