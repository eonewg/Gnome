package io.github.eonewg.gnome.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eonewg.gnome.nav.EditorKey
import io.github.eonewg.gnome.nav.GnomeNavigator
import io.github.eonewg.gnome.nav.MemoDetailKey
import io.github.eonewg.gnome.nav.TagKey
import io.github.eonewg.gnome.ui.component.MemoCardActions
import kotlinx.coroutines.launch

/** Registers the search feature: ViewModel → UiState → Screen wiring. */
@Composable
fun SearchRoute(navigator: GnomeNavigator) {
    val searchViewModel: SearchViewModel = hiltViewModel()
    val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val memoCardActions = MemoCardActions(
        onOpen = { memo ->
            navigator.navigate(MemoDetailKey(memo.id))
        },
        onEdit = { memoId ->
            navigator.navigate(EditorKey(memoId))
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
        onBack = { navigator.goBack() },
        onTagClick = { tag ->
            navigator.navigate(TagKey(tag), singleTop = true)
        },
        actions = memoCardActions,
    )
}