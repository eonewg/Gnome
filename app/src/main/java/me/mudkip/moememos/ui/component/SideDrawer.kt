package me.mudkip.moememos.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun SideDrawer(
    memosNavController: NavHostController,
    drawerState: DrawerState? = null,
    loadTags: Boolean = true,
) {
    val weekDays = remember {
        val day = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        List(DayOfWeek.entries.size) { index ->
            day.plus(index.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }
    val scope = rememberCoroutineScope()
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val hasExplore = currentAccount !is Account.Local
    val rootNavController = LocalRootNavController.current
    val navBackStackEntry by memosNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val colors = MoeMemosDesign.colors
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = colors.tagBackground,
        selectedIconColor = colors.tagForeground,
        selectedTextColor = colors.textPrimary,
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = colors.textSecondary,
        unselectedTextColor = colors.textPrimary,
    )
    val drawerItemShape = RoundedCornerShape(14.dp)

    fun isSelected(route: String): Boolean {
        return currentDestination?.hierarchy?.any { it.route == route } == true
    }

    fun isTagSelected(tag: String): Boolean {
        if (!isSelected("${RouteName.TAG}/{tag}")) return false

        val currentTag = navBackStackEntry?.arguments?.getString("tag")
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        return currentTag == tag || currentTag == encodedTag
    }

    LazyColumn(modifier = Modifier.background(colors.cardBackground)) {
        item {
            Stats()
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 5.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(weekDays[0],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text(weekDays[3],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text(weekDays[6],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
                Heatmap()
            }
        }

        item {
            Text(
                R.string.moe_memos.string,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(20.dp)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.memos.string) },
                icon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
                selected = isSelected(RouteName.MEMOS),
                onClick = {
                    scope.launch {
                        memosNavController.navigate(RouteName.MEMOS) {
                            launchSingleTop = true
                            restoreState = true
                        }
                        drawerState?.close()
                    }
                },
                shape = drawerItemShape,
                colors = drawerItemColors,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        if (hasExplore) {
            item {
                NavigationDrawerItem(
                    label = { Text(R.string.explore.string) },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    selected = isSelected(RouteName.EXPLORE),
                    onClick = {
                        scope.launch {
                            memosNavController.navigate(RouteName.EXPLORE) {
                                launchSingleTop = true
                                restoreState = true
                            }
                            drawerState?.close()
                        }
                    },
                    shape = drawerItemShape,
                    colors = drawerItemColors,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.resources.string) },
                icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                selected = false,
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.RESOURCE)
                    }
                },
                shape = drawerItemShape,
                colors = drawerItemColors,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.archived.string) },
                icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                selected = isSelected(RouteName.ARCHIVED),
                onClick = {
                    scope.launch {
                        memosNavController.navigate(RouteName.ARCHIVED) {
                            launchSingleTop = true
                            restoreState = true
                        }
                        drawerState?.close()
                    }
                },
                shape = drawerItemShape,
                colors = drawerItemColors,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        item {
            NavigationDrawerItem(
                label = { Text(R.string.settings.string) },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = {
                    scope.launch {
                        drawerState?.close()
                        rootNavController.navigate(RouteName.SETTINGS)
                    }
                },
                shape = drawerItemShape,
                colors = drawerItemColors,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = colors.divider,
            )
        }

        item {
            Text(
                R.string.tags.string,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(20.dp)
            )
        }

        items(
            items = memosViewModel.tags,
            key = { it },
        ) { tag ->
            TagDrawerItem(
                tag = tag,
                selected = isTagSelected(tag),
                memosNavController = memosNavController,
                drawerState = drawerState
            )
        }
    }

    LaunchedEffect(memosViewModel, loadTags) {
        if (loadTags) {
            memosViewModel.loadTags()
        }
    }
}
