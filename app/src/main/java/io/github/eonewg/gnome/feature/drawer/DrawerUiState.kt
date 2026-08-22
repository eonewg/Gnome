package io.github.eonewg.gnome.feature.drawer

import io.github.eonewg.gnome.data.model.DailyUsageStat

/** Immutable snapshot of everything the navigation drawer renders. */
data class DrawerUiState(
    val tags: List<String> = emptyList(),
    val memoCount: Int = 0,
    val matrix: List<DailyUsageStat> = emptyList(),
    val displayName: String = "",
    val isRemoteAccount: Boolean = false,
    val days: Int = 0,
)