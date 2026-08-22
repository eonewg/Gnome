package io.github.eonewg.gnome.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ui.page.common.RouteName
import java.time.LocalDate

/** Registers the stats overview: ViewModel → UiState → Screen wiring. */
@Composable
fun StatsRoute(navController: NavHostController) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    StatsScreen(
        uiState = uiState,
        onBack = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) },
        onShowDetail = { navController.navigate(RouteName.STATS_DETAIL) },
        onDateClick = { date: LocalDate ->
            navController.navigate("${RouteName.MEMOS}/${RouteName.DATE}/$date") {
                popUpTo(RouteName.STATS) { inclusive = true }
                launchSingleTop = true
            }
        },
    )
}

/** Registers the monthly/detailed stats view. */
@Composable
fun StatsDetailRoute(navController: NavHostController) {
    val statsViewModel: StatsViewModel = hiltViewModel()
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    StatsDetailScreen(
        uiState = uiState,
        onBack = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) },
    )
}