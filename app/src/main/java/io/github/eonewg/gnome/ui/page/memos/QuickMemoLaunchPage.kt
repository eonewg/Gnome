package io.github.eonewg.gnome.ui.page.memos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.component.toMemoTimestamp
import io.github.eonewg.gnome.feature.account.AccountSessionViewModel
import io.github.eonewg.gnome.feature.editor.EditorPresentation
import io.github.eonewg.gnome.feature.memo.QuickMemoViewModel
import io.github.eonewg.gnome.feature.editor.EditorRoute
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.ui.theme.GnomeTheme

/** Minimal first composition used only when Quick Settings starts a cold app process. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoLaunchPage(onFinished: () -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val accountSessionViewModel: AccountSessionViewModel = hiltViewModel()
    val quickMemoViewModel: QuickMemoViewModel = hiltViewModel()
    val memos by quickMemoViewModel.memos.collectAsState()
    val imeInsets = WindowInsets.ime
    val imeBottom = imeInsets.getBottom(LocalDensity.current)
    val editorRevealed = remember { mutableStateOf(false) }

    fun finishCapture() {
        keyboardController?.hide()
        focusManager.clearFocus()
        onFinished()
    }

    BackHandler { finishCapture() }

    GnomeTheme {
        val colors = GnomeDesign.colors
        val scrimInteractionSource = remember { MutableInteractionSource() }
        val inputInteractionSource = remember { MutableInteractionSource() }
        Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = colors.appBackground,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colors.appBackground,
                                scrolledContainerColor = colors.appBackground,
                            ),
                            title = {
                                Text(
                                    text = R.string.memos.string,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.textPrimary,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = {}) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = {}) {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                    )
                                }
                                IconButton(onClick = {}) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                    )
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = memos
                                .sortedByDescending { it.pinned }
                                .take(6),
                            key = { it.id },
                        ) { memo ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = colors.cardBackground,
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = memo.date.toMemoTimestamp(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.textSecondary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                        )
                                    }
                                    Text(
                                        text = buildAnnotatedString {
                                            val content = memo.content.take(240)
                                            val matches = Regex("""#[^\s#]+""")
                                                .findAll(content)
                                                .toList()
                                            var cursor = 0
                                            for (match in matches) {
                                                append(content.substring(cursor, match.range.first))
                                                withStyle(
                                                    SpanStyle(
                                                        color = colors.tagForeground,
                                                        background = colors.tagBackground,
                                                    ),
                                                ) {
                                                    append(match.value)
                                                }
                                                cursor = match.range.last + 1
                                            }
                                            append(content.substring(cursor))
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textPrimary,
                                        maxLines = 6,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.56f))
                        .clickable(
                            interactionSource = scrimInteractionSource,
                            indication = null,
                            onClick = { finishCapture() },
                        ),
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset {
                            IntOffset(x = 0, y = -imeInsets.getBottom(this))
                        }
                        .alpha(if (editorRevealed.value) 1f else 0f)
                        .clickable(
                            interactionSource = inputInteractionSource,
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = colors.cardBackground,
                    contentColor = colors.textPrimary,
                    shadowElevation = 8.dp,
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .background(
                                        color = colors.divider,
                                        shape = RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                        EditorRoute(
                            presentation = EditorPresentation.BottomSheet,
                            onFinished = { finishCapture() },
                            active = true,
                        )
                    }
                }
            }
    }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            editorRevealed.value = true
        } else if (!editorRevealed.value) {
            delay(500)
            editorRevealed.value = true
        }
    }

    LaunchedEffect(Unit) {
        if (!accountSessionViewModel.hasAnyAccount()) {
            finishCapture()
        }
        // The editor loads its own tag suggestions from the Room tag index,
        // so the old MemosViewModel warm-up is no longer needed here.
    }
}
