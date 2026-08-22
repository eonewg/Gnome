package io.github.eonewg.gnome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Archived memos streamed from the account's Room flow; restore/delete go
 * through the same local-first pipeline, and the flow re-emits on its own.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedMemoListViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
) : ViewModel() {

    val memos: StateFlow<List<Memo>> = accountService.currentAccount
        .flatMapLatest {
            memoService.getMemoRepository().observeArchived()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    suspend fun restoreMemo(identifier: String): ApiResponse<Unit> =
        memoService.getMemoRepository().restoreMemo(identifier)

    suspend fun deleteMemo(identifier: String): ApiResponse<Unit> =
        memoService.getMemoRepository().deleteMemo(identifier)
}