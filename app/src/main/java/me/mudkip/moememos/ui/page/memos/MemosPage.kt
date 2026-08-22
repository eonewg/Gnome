package me.mudkip.moememos.ui.page.memos

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
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import me.mudkip.moememos.ui.component.SideDrawer
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import java.time.LocalDate

@Composable
fun MemosPage(
    quickMemoRequestId: Long = 0L,
    initialDate: LocalDate? = null,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val memosNavController = rememberNavController()
    val colors = MoeMemosDesign.colors
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
                        loadTags = drawerState.currentValue == DrawerValue.Open,
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
