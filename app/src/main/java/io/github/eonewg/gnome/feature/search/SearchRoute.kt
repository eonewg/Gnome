package io.github.eonewg.gnome.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

/** Registers the search feature: ViewModel → UiState → Screen wiring. */
@Composable
fun SearchRoute(navController: NavHostController) {
    val searchViewModel: SearchViewModel = hiltViewModel()
    val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        onQueryChange = searchViewModel::setQuery,
        onIncludeArchivedChange = searchViewModel::setIncludeArchived,
        navController = navController,
    )
}