package io.github.eonewg.gnome.ui.page.memos

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.feature.timeline.DateMemoViewModel
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateMemoPage(
    drawerState: DrawerState? = null,
    date: LocalDate,
    navController: NavHostController,
) {
    val scope = rememberCoroutineScope()
    val colors = GnomeDesign.colors
    val dateMemoViewModel: DateMemoViewModel = hiltViewModel()
    val uiState by dateMemoViewModel.uiState.collectAsStateWithLifecycle()
    val title = remember(date, Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.getDefault())
            .format(date)
    }

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

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, color = colors.textPrimary) },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = R.string.menu.string,
                                tint = colors.textSecondary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appBackground,
                    scrolledContainerColor = colors.appBackground,
                ),
            )
        },
    ) { innerPadding ->
        MemosList(
            memos = uiState.memos,
            contentPadding = innerPadding,
            date = date,
            onTagClick = { tag ->
                navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            isRemoteAccount = uiState.isRemoteAccount,
            host = uiState.host,
            defaultVisibility = uiState.defaultVisibility,
            actions = memoCardActions,
        )
    }
}