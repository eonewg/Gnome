package io.github.eonewg.gnome.feature.search

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.common.RouteName
import kotlinx.coroutines.launch

/** Registers the search feature: ViewModel → UiState → Screen wiring. */
@Composable
fun SearchRoute(navController: NavHostController) {
    val searchViewModel: SearchViewModel = hiltViewModel()
    val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val memoCardActions = MemoCardActions(
        onOpen = { memo ->
            navController.navigate("${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(memo.id)}")
        },
        onEdit = { memoId ->
            navController.navigate("${RouteName.EDIT}?memoId=$memoId")
        },
        onTogglePin = { memoId, pinned ->
            scope.launch { searchViewModel.updateMemoPinned(memoId, pinned) }
        },
        onArchive = { memoId ->
            scope.launch { searchViewModel.archiveMemo(memoId) }
        },
        onDelete = { memoId ->
            scope.launch { searchViewModel.deleteMemo(memoId) }
        },
        onUpdateContent = { memoId, content ->
            scope.launch { searchViewModel.updateMemoContent(memoId, content) }
        },
        onCacheResource = { resourceId, uri ->
            scope.launch { searchViewModel.cacheResourceFile(resourceId, uri) }
        },
        onDownloadAndCache = { resource ->
            searchViewModel.downloadAndCacheResource(resource)
        },
    )

    SearchScreen(
        uiState = uiState,
        onQueryChange = searchViewModel::setQuery,
        onIncludeArchivedChange = searchViewModel::setIncludeArchived,
        navController = navController,
        actions = memoCardActions,
    )
}