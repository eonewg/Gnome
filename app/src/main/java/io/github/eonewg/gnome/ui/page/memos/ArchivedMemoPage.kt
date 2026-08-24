package io.github.eonewg.gnome.ui.page.memos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.eonewg.gnome.ui.page.common.drawerForegroundAlpha
import io.github.eonewg.gnome.ui.page.common.GnomeDrawerState
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMemoPage(
    drawerState: GnomeDrawerState? = null
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(
                        text = R.string.archived.string,
                        modifier = Modifier.drawerForegroundAlpha(),
                    )
                },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(
                            modifier = Modifier.drawerForegroundAlpha(),
                            onClick = { scope.launch { drawerState.open() } },
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                        }
                    }
                }
            )
        },

        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawerForegroundAlpha(),
            ) {
                ArchivedMemoList(
                    contentPadding = innerPadding
                )
            }
        }
    )
}
