package io.github.eonewg.gnome.ui.page.memos

import android.net.Uri
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.viewmodel.LocalUserState
import java.time.LocalDate

@Composable
fun MemosNavigation(
    drawerState: DrawerState? = null,
    navController: NavHostController,
    quickMemoRequestId: Long = 0L,
    onMemoInputActiveChange: (Boolean) -> Unit = {},
    initialDate: LocalDate? = null,
) {
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val hasExplore = currentAccount !is Account.Local

    NavHost(
        navController = navController,
        startDestination = RouteName.MEMOS
    ) {
        composable(
            RouteName.MEMOS,
        ) {
            MemosHomePage(
                drawerState = drawerState,
                navController = navController,
                quickMemoRequestId = quickMemoRequestId,
                onMemoInputActiveChange = onMemoInputActiveChange,
            )
        }

        composable(
            RouteName.ARCHIVED
        ) {
            ArchivedMemoPage(
                drawerState = drawerState
            )
        }

        composable(
            "${RouteName.TAG}/{tag}"
        ) { entry ->
            TagMemoPage(
                drawerState = drawerState,
                tag = entry.arguments?.getString("tag")?.let(Uri::decode) ?: "",
                navController = navController
            )
        }

        composable(
            "${RouteName.DATE}/{date}"
        ) { entry ->
            val date = entry.arguments?.getString("date")
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now()
            DateMemoPage(
                drawerState = drawerState,
                date = date,
                navController = navController,
            )
        }

        composable(
            RouteName.EXPLORE
        ) {
            if (hasExplore) {
                ExplorePage(
                    drawerState = drawerState
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(RouteName.MEMOS) {
                        popUpTo(RouteName.EXPLORE) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }
        }

        composable(RouteName.SEARCH) {
            SearchPage(navController = navController)
        }
    }

    LaunchedEffect(quickMemoRequestId) {
        if (quickMemoRequestId > 0L && navController.currentDestination?.route != RouteName.MEMOS) {
            navController.navigate(RouteName.MEMOS) {
                popUpTo(RouteName.MEMOS) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            navController.navigate("${RouteName.DATE}/$initialDate") {
                launchSingleTop = true
            }
        }
    }
}
