package io.github.eonewg.gnome.ui.page.memos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import io.github.eonewg.gnome.ui.page.common.LocalDrawerContentHandoffActive
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorePage(
    drawerState: DrawerState? = null
) {
    val scope = rememberCoroutineScope()
    val drawerContentHandoffActive = LocalDrawerContentHandoffActive.current
    val destinationContainerColor = if (drawerContentHandoffActive) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    }

    Scaffold(
        containerColor = destinationContainerColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = destinationContainerColor,
                    scrolledContainerColor = destinationContainerColor,
                ),
                title = { Text(text = R.string.explore.string) },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                        }
                    }
                }
            )
        },

        content = { innerPadding ->
            ExploreList(
                contentPadding = innerPadding
            )
        }
    )
}
