package io.github.eonewg.gnome.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.data.service.AccountService
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
            flowOf(SearchUiState(query = queryText, includeArchived = includeArchived))
        } else {
            memoService.getMemoRepository()
                .observeSearch(query = trimmed, includeArchived = includeArchived)
                .map { memos ->
                    SearchUiState(
                        query = queryText,
                        includeArchived = includeArchived,
                        results = memos,
                        hasSearched = true,
                    )
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
}