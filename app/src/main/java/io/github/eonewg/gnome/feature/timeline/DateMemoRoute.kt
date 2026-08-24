package io.github.eonewg.gnome.feature.timeline

import io.github.eonewg.gnome.ui.page.common.GnomeDrawerState
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
import java.time.LocalDate

/** Registers the per-date memo list: ViewModel → UiState → Screen wiring. */
@Composable
fun DateMemoRoute(
    drawerState: GnomeDrawerState? = null,
    date: LocalDate,
    navigator: GnomeNavigator,
) {
    val dateMemoViewModel: DateMemoViewModel = hiltViewModel()
    val uiState by dateMemoViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val memoCardActions = MemoCardActions(
        onOpen = { memo ->
            navigator.navigate(MemoDetailKey(memo.id))
        },
        onEdit = { memoId ->
            navigator.navigate(EditorKey(memoId))
        },
        onTogglePin = { memoId, pinned ->
            scope.launch { dateMemoViewModel.updateMemoPinned(memoId, pinned) }
        },
        onArchive = { memoId ->
            scope.launch { dateMemoViewModel.archiveMemo(memoId) }
        },
        onDelete = { memoId ->
            scope.launch { dateMemoViewModel.deleteMemo(memoId) }
        },
        onUpdateContent = { memoId, content ->
            scope.launch { dateMemoViewModel.updateMemoContent(memoId, content) }
        },
        onCacheResource = { resourceId, uri ->
            scope.launch { dateMemoViewModel.cacheResourceFile(resourceId, uri) }
        },
        onDownloadAndCache = { resource ->
            dateMemoViewModel.downloadAndCacheResource(resource)
        },
    )

    DateMemoScreen(
        date = date,
        drawerState = drawerState,
        uiState = uiState,
        onTagClick = { tag ->
            navigator.navigate(TagKey(tag), singleTop = true)
        },
        actions = memoCardActions,
    )
}
