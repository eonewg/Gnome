package io.github.eonewg.gnome.feature.timeline

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.feature.editor.EditorPresentation
import io.github.eonewg.gnome.feature.editor.EditorRoute
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLEncoder

@Composable
fun TimelineRoute(
    drawerState: DrawerState? = null,
    navController: NavHostController,
    quickMemoRequestId: Long = 0L,
    onMemoInputActiveChange: (Boolean) -> Unit = {},
) {
    val timelineViewModel: TimelineViewModel = hiltViewModel()
    val uiState by timelineViewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = GnomeDesign.colors
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val snackbarState = remember { SnackbarHostState() }

    var showMemoInput by rememberSaveable { mutableStateOf(false) }
    val currentShowMemoInput by rememberUpdatedState(showMemoInput)

    fun openMemoInput() {
        timelineViewModel.exitSelectionMode()
        showMemoInput = true
        onMemoInputActiveChange(true)
    }

    fun closeMemoInput() {
        keyboardController?.hide()
        focusManager.clearFocus()
        showMemoInput = false
        onMemoInputActiveChange(false)
    }

    fun copySelectedMemos() {
        val selectedMemos = timelineViewModel.selectedMemos
        if (selectedMemos.isEmpty()) return
        clipboardManager?.setPrimaryClip(
            ClipData.newPlainText(
                context.getString(R.string.copy_all),
                selectedMemos.joinToString(separator = "\n\n") { it.content },
            )
        )
        scope.launch {
            snackbarState.showSnackbar(context.getString(R.string.memos_copied, selectedMemos.size))
        }
    }

    BackHandler(enabled = showMemoInput) {
        closeMemoInput()
    }

    BackHandler(enabled = uiState.selectionMode && !showMemoInput) {
        timelineViewModel.exitSelectionMode()
    }

    LaunchedEffect(quickMemoRequestId) {
        if (quickMemoRequestId > 0L) {
            timelineViewModel.exitSelectionMode()
            openMemoInput()
        }
    }

    LaunchedEffect(showMemoInput, uiState.selectionMode) {
        onMemoInputActiveChange(showMemoInput || uiState.selectionMode)
    }

    LaunchedEffect(Unit) {
        timelineViewModel.loadMemos()
    }

    LaunchedEffect(Unit) {
        timelineViewModel.messages.collect { message ->
            snackbarState.showSnackbar(
                when (message) {
                    is TimelineMessage.MemosCopied -> context.getString(R.string.memos_copied, message.count)
                    is TimelineMessage.TagAdded -> context.getString(R.string.tag_added_to_memos, message.count)
                    is TimelineMessage.MemosDeleted -> context.getString(R.string.memos_deleted, message.count)
                    is TimelineMessage.BatchFailed -> context.getString(R.string.batch_operation_failed, message.count)
                }
            )
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { Timber.d(it) }
    }

    // The timeline handles IME placement itself. Set this once for the lifetime of the page;
    // changing the soft-input mode on every open forces an extra window traversal.
    DisposableEffect(activity, lifecycleOwner) {
        val originalSoftInputMode = activity?.window?.attributes?.softInputMode
        fun enforceStableImeWindow() {
            val window = activity?.window ?: return
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            ViewCompat.requestApplyInsets(window.decorView)
        }

        enforceStableImeWindow()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Some vendor window managers restore adjust-pan while returning from another
                // app. Reassert our manual IME placement after the window is attached again.
                enforceStableImeWindow()
                activity?.window?.decorView?.post {
                    enforceStableImeWindow()
                    if (currentShowMemoInput) {
                        keyboardController?.show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onMemoInputActiveChange(false)
            if (originalSoftInputMode != null) {
                activity.window.setSoftInputMode(originalSoftInputMode)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TimelineScreen(
            uiState = uiState,
            snackbarHostState = snackbarState,
            listState = listState,
            showNavigationMenu = drawerState != null,
            onMenuClick = {
                scope.launch { drawerState?.open() }
            },
            onSearchClick = {
                navController.navigate(RouteName.SEARCH)
            },
            onEnterSelectionMode = timelineViewModel::enterSelectionMode,
            onExitSelectionMode = timelineViewModel::exitSelectionMode,
            onSortOrderSelected = timelineViewModel::setSortOrder,
            onToggleSelection = timelineViewModel::toggleSelection,
            onRequestAddTag = timelineViewModel::requestAddTagDialog,
            onAddTag = timelineViewModel::addTagToSelection,
            onDismissAddTag = timelineViewModel::dismissAddTagDialog,
            onRequestBatchDelete = timelineViewModel::requestBatchDeleteDialog,
            onDeleteSelection = timelineViewModel::deleteSelection,
            onDismissBatchDelete = timelineViewModel::dismissBatchDeleteDialog,
            onCopySelection = { copySelectedMemos() },
            onSync = {
                scope.launch { timelineViewModel.syncNow() }
            },
            onCompose = { openMemoInput() },
            onRefresh = { timelineViewModel.syncNow() },
            onDismissSyncAlert = timelineViewModel::dismissSyncAlert,
            onConfirmSyncWithVersion = { version ->
                scope.launch { timelineViewModel.syncNow(allowHigherV1Version = version) }
            },
            onTagClick = { tag ->
                navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )

        // Keep the editor composed from the initial timeline composition. Doing this behind the
        // launch frame avoids a large first-tap composition without adding a visible idle hitch.
        val imeInsets = WindowInsets.ime
        val scrimInteractionSource = remember { MutableInteractionSource() }
        val inputInteractionSource = remember { MutableInteractionSource() }

        if (showMemoInput) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.56f))
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                        onClick = { closeMemoInput() },
                    )
            )
        }

        Surface(
                modifier = if (showMemoInput) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // Read the animated IME inset during placement. Unlike padding, this does
                        // not remeasure the editor (or its toolbar) for every keyboard frame.
                        .offset {
                            IntOffset(
                                x = 0,
                                y = -imeInsets.getBottom(this),
                            )
                        }
                        .clickable(
                            interactionSource = inputInteractionSource,
                            indication = null,
                            onClick = {},
                        )
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset(y = HiddenMemoInputOffset)
                },
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
                                )
                        )
                    }
                    EditorRoute(
                        presentation = EditorPresentation.BottomSheet,
                        onFinished = { closeMemoInput() },
                        active = showMemoInput,
                    )
                }
        }
    }
}

private val HiddenMemoInputOffset = 1_000.dp
