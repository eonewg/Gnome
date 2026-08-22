package io.github.eonewg.gnome.ui.page.memos

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
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.feature.drawer.DrawerViewModel
import io.github.eonewg.gnome.ui.component.SideDrawer
import io.github.eonewg.gnome.ui.theme.GnomeDesign
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
    val colors = GnomeDesign.colors
    var memoInputActive by rememberSaveable { mutableStateOf(false) }

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
                    SideDrawer(
                        memosNavController = memosNavController,
                        uiState = drawerUiState,
                        rootNavController = navController,
                    )
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
                    SideDrawer(
                        memosNavController = memosNavController,
                        drawerState = drawerState,
                        uiState = drawerUiState,
                        rootNavController = navController,
                    )
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
