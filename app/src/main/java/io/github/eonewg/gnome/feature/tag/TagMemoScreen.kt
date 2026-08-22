package io.github.eonewg.gnome.feature.tag

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.memos.MemosList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagMemoScreen(
    tag: String,
    drawerState: DrawerState? = null,
    uiState: TagMemoUiState,
    onTagClick: (String) -> Unit,
    actions: MemoCardActions,
) {
    val scope = rememberCoroutineScope()
    val normalizedCurrentTag = remember(tag) { tag.removePrefix("#") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tag) },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                        }
                    }
                },
            )
        },

        content = { innerPadding ->
            MemosList(
                memos = uiState.memos,
                contentPadding = innerPadding,
                loadOnStart = false,
                onTagClick = { clickedTag ->
                    if (clickedTag.removePrefix("#") == normalizedCurrentTag) {
                        return@MemosList
                    }
                    onTagClick(clickedTag)
                },
                isRemoteAccount = uiState.isRemoteAccount,
                host = uiState.host,
                defaultVisibility = uiState.defaultVisibility,
                actions = actions,
            )
        }
    )
}