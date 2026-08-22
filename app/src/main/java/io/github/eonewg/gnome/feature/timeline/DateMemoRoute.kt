package io.github.eonewg.gnome.feature.timeline

import android.net.Uri
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.common.RouteName
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate

/** Registers the per-date memo list: ViewModel → UiState → Screen wiring. */
@Composable
fun DateMemoRoute(
    drawerState: DrawerState? = null,
    date: LocalDate,
    navController: NavHostController,
) {
    val dateMemoViewModel: DateMemoViewModel = hiltViewModel()
    val uiState by dateMemoViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val memoCardActions = MemoCardActions(
        onOpen = { memo ->
            navController.navigate("${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(memo.id)}")
        },
        onEdit = { memoId ->
            navController.navigate("${RouteName.EDIT}?memoId=$memoId")
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
            navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                launchSingleTop = true
                restoreState = true
            }
        },
        actions = memoCardActions,
    )
}