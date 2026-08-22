package io.github.eonewg.gnome.feature.tag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    private val tag = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TagMemoUiState> = tag
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(TagMemoUiState())
            } else {
                memoService.getMemoRepository().observeMemosByTag(current).map { memos ->
                    TagMemoUiState(tag = current, memos = memos)
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
}