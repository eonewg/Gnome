package io.github.eonewg.gnome.feature.tag

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
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

    // Never show the previous tag snapshot under the new title while the Room flow switches.
    val matchingUiState = if (uiState.tag == tag) {
        uiState
    } else {
        uiState.copy(tag = tag, memos = emptyList())
    }
    val contentState = TagContentState(tag = tag, uiState = matchingUiState)
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
            AnimatedContent(
                targetState = contentState,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = TagEnterDurationMillis,
                            easing = FastOutSlowInEasing,
                        )
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = TagExitDurationMillis,
                            easing = FastOutSlowInEasing,
                        )
                    ) using SizeTransform(
                        clip = false,
                        sizeAnimationSpec = { _, _ -> snap() },
                    )
                },
                // Only a real tag change animates; same-tag Room emissions update in place.
                contentKey = { it.tag },
                label = "TagMemoContent",
            ) { state ->
                MemosList(
                    memos = state.uiState.memos,
                    contentPadding = innerPadding,
                    loadOnStart = false,
                    onTagClick = { clickedTag ->
                        if (clickedTag.removePrefix("#") == normalizedCurrentTag) {
                            return@MemosList
                        }
                        onTagClick(clickedTag)
                    },
                    isRemoteAccount = state.uiState.isRemoteAccount,
                    host = state.uiState.host,
                    defaultVisibility = state.uiState.defaultVisibility,
                    actions = actions,
                )
            }
        }
    )
}

private data class TagContentState(
    val tag: String,
    val uiState: TagMemoUiState,
)

private const val TagExitDurationMillis = 100
private const val TagEnterDurationMillis = 170
