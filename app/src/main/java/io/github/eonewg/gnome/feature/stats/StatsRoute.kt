package io.github.eonewg.gnome.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eonewg.gnome.nav.DateKey
import io.github.eonewg.gnome.nav.GnomeNavigator
import io.github.eonewg.gnome.nav.StatsDetailKey
import io.github.eonewg.gnome.nav.StatsKey
import java.time.LocalDate

/** Registers the stats overview: ViewModel → UiState → Screen wiring. */
@Composable
fun StatsRoute(navigator: GnomeNavigator) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(
        uiState = uiState,
        onBack = { navigator.goBack() },
        onShowDetail = { navigator.navigate(StatsDetailKey) },
        onDateClick = { date: LocalDate ->
            navigator.popUpTo(StatsKey, inclusive = true)
            navigator.navigate(DateKey(date.toString()))
        },
    )
}

/** Registers the monthly/detailed stats view. */
@Composable
fun StatsDetailRoute(navigator: GnomeNavigator) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()

    StatsDetailScreen(
        uiState = uiState,
        onBack = { navigator.goBack() },
    )
}