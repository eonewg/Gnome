package io.github.eonewg.gnome.feature.stats

/** Immutable snapshot of everything the stats screens render. */
data class StatsUiState(
    val snapshot: MemoStatsSnapshot = MemoStatsSnapshot(emptyMap()),
)