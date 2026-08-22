package io.github.eonewg.gnome.feature.tag

import android.net.Uri
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.common.RouteName
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Registers the per-tag memo list: ViewModel → UiState → Screen wiring. */
@Composable
fun TagMemoRoute(
    drawerState: DrawerState? = null,
    tag: String,
    navController: NavHostController,
) {
    val tagViewModel: TagMemoViewModel = hiltViewModel()
    val uiState by tagViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(tag) {
        tagViewModel.setTag(tag)
    }

    val memoCardActions = MemoCardActions(
        onOpen = { memo ->
            navController.navigate("${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(memo.id)}")
        },
        onEdit = { memoId ->
            navController.navigate("${RouteName.EDIT}?memoId=$memoId")
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
            navController.navigate("${RouteName.TAG}/${URLEncoder.encode(clickedTag, "UTF-8")}") {
                launchSingleTop = true
                restoreState = true
            }
        },
        actions = memoCardActions,
    )
}