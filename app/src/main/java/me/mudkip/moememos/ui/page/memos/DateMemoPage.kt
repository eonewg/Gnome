package me.mudkip.moememos.ui.page.memos

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateMemoPage(
    drawerState: DrawerState? = null,
    date: LocalDate,
    navController: NavHostController,
) {
    val scope = rememberCoroutineScope()
    val colors = MoeMemosDesign.colors
    val title = remember(date, Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.getDefault())
            .format(date)
    }

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
            contentPadding = innerPadding,
            date = date,
            onTagClick = { tag ->
                navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}
