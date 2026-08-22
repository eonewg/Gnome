package io.github.eonewg.gnome.feature.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The recent-memo backdrop behind the Quick Settings capture editor. Room
 * streams it, so no snapshot management is needed.
 */
@HiltViewModel
class QuickMemoViewModel @Inject constructor(
    private val memoService: MemoService,
) : ViewModel() {

    val memos: StateFlow<List<Memo>> = memoService.domainMemos
        .map { it.sortedByDescending { memo -> memo.pinned } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}