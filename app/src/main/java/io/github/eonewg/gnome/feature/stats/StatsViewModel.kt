package io.github.eonewg.gnome.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.data.service.MemoService
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Recomputes the pure [calculateMemoStats] snapshot from the timeline domain
 * stream (which is backed by the Room flow), so the stats pages never touch
 * the legacy in-memory MemosViewModel snapshot.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    memoService: MemoService,
) : ViewModel() {

    private val zoneId = ZoneId.systemDefault()

    val uiState: StateFlow<StatsUiState> = memoService.domainMemos
        .map { memos ->
            StatsUiState(snapshot = calculateMemoStats(memos.map { it.toStatsInput() }, zoneId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )
}