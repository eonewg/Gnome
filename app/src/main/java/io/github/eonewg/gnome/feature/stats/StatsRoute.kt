package io.github.eonewg.gnome.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

/** Registers the stats overview: ViewModel → UiState → Screen wiring. */
@Composable
fun StatsRoute(navController: NavHostController) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()

    StatsScreen(uiState = uiState, navController = navController)
}

/** Registers the monthly/detailed stats view. */
@Composable
fun StatsDetailRoute(navController: NavHostController) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()

    StatsDetailScreen(uiState = uiState, navController = navController)
}