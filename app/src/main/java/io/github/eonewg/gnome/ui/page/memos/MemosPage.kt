package io.github.eonewg.gnome.ui.page.memos

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.feature.drawer.DrawerViewModel
import io.github.eonewg.gnome.ui.component.SideDrawer
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.net.URLEncoder
import java.time.LocalDate

@Composable
fun MemosPage(
    navController: NavHostController,
    quickMemoRequestId: Long = 0L,
    initialDate: LocalDate? = null,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val memosNavController = rememberNavController()
    val drawerViewModel: DrawerViewModel = hiltViewModel()
    val drawerUiState by drawerViewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by memosNavController.currentBackStackEntryAsState()
    val colors = GnomeDesign.colors
    var memoInputActive by rememberSaveable { mutableStateOf(false) }

    val selectedRoute = navBackStackEntry?.destination?.route
    val selectedTag = navBackStackEntry?.arguments?.getString("tag")?.let(Uri::decode)

    fun drawerNavigate(action: () -> Unit) {
        scope.launch {
            action()
            drawerState.close()
        }
    }

    val drawerContent: @Composable () -> Unit = {
        SideDrawer(
            uiState = drawerUiState,
            selectedRoute = selectedRoute,
            selectedTag = selectedTag,
            onStatsClick = {
                drawerNavigate {
                    navController.navigate(RouteName.STATS) {
                        launchSingleTop = true
                    }
                }
            },
            onMemosClick = {
                drawerNavigate {
                    memosNavController.navigate(RouteName.MEMOS) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            onExploreClick = {
                drawerNavigate {
                    memosNavController.navigate(RouteName.EXPLORE) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            onResourcesClick = {
                drawerNavigate {
                    navController.navigate(RouteName.RESOURCE)
                }
            },
            onArchivedClick = {
                drawerNavigate {
                    memosNavController.navigate(RouteName.ARCHIVED) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            onSettingsClick = {
                drawerNavigate {
                    navController.navigate(RouteName.SETTINGS)
                }
            },
            onTagClick = { tag ->
                drawerNavigate {
                    memosNavController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            onDateClick = { date ->
                drawerNavigate {
                    memosNavController.navigate("${RouteName.DATE}/$date") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
        )
    }

    LaunchedEffect(memoInputActive) {
        if (memoInputActive && drawerState.isOpen) {
            drawerState.close()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    drawerContainerColor = colors.cardBackground,
                ) {
                    drawerContent()
                }
            }
        ) {
            MemosNavigation(
                navController = memosNavController,
                quickMemoRequestId = quickMemoRequestId,
                initialDate = initialDate,
                onMemoInputActiveChange = { memoInputActive = it },
            )
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !memoInputActive,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxWidth(0.84f),
                    drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    drawerContainerColor = colors.cardBackground,
                    drawerTonalElevation = 0.dp,
                ) {
                    drawerContent()
                }
            }
        ) {
            MemosNavigation(
                drawerState = drawerState,
                navController = memosNavController,
                quickMemoRequestId = quickMemoRequestId,
                initialDate = initialDate,
                onMemoInputActiveChange = { memoInputActive = it },
            )
        }
    }
}