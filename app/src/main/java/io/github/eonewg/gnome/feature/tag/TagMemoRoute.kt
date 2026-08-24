package io.github.eonewg.gnome.feature.tag

import io.github.eonewg.gnome.ui.page.common.GnomeDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** Registers the per-tag memo list: ViewModel → UiState → Screen wiring. */
@Composable
fun TagMemoRoute(
    drawerState: GnomeDrawerState? = null,
    tag: String,
    navigator: GnomeNavigator,
) {
    val tagViewModel: TagMemoViewModel = hiltViewModel()
    val uiState by tagViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(tag) {
        tagViewModel.setTag(tag)
    }

    val memoCardActions = MemoCardActions(
        onOpen = { memo ->
            navigator.navigate(MemoDetailKey(memo.id))
        },
        onEdit = { memoId ->
            navigator.navigate(EditorKey(memoId))
        },
        onTogglePin = { memoId, pinned ->
            scope.launch { tagViewModel.updateMemoPinned(memoId, pinned) }
        },
        onArchive = { memoId ->
            scope.launch { tagViewModel.archiveMemo(memoId) }
        },
        onDelete = { memoId ->
            scope.launch { tagViewModel.deleteMemo(memoId) }
        },
        onUpdateContent = { memoId, content ->
            scope.launch { tagViewModel.updateMemoContent(memoId, content) }
        },
        onCacheResource = { resourceId, uri ->
            scope.launch { tagViewModel.cacheResourceFile(resourceId, uri) }
        },
        onDownloadAndCache = { resource ->
            tagViewModel.downloadAndCacheResource(resource)
        },
    )

    TagMemoScreen(
        tag = tag,
        drawerState = drawerState,
        uiState = uiState,
        onTagClick = { clickedTag ->
            navigator.switchDrawerDestination(TagKey(clickedTag))
        },
        actions = memoCardActions,
    )
}
