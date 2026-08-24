package io.github.eonewg.gnome.feature.timeline

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.feature.editor.EditorPresentation
import io.github.eonewg.gnome.feature.editor.EditorRoute
import io.github.eonewg.gnome.nav.GnomeNavigator
import io.github.eonewg.gnome.nav.EditorKey
import io.github.eonewg.gnome.nav.MemoDetailKey
import io.github.eonewg.gnome.nav.SearchKey
import io.github.eonewg.gnome.nav.TagKey
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.common.GnomeDrawerState
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun TimelineRoute(
    viewModelStoreOwner: ViewModelStoreOwner,
    drawerState: GnomeDrawerState? = null,
    navigator: GnomeNavigator,
    quickMemoRequestId: Long = 0L,
    onMemoInputActiveChange: (Boolean) -> Unit = {},
) {
    val timelineViewModel: TimelineViewModel = hiltViewModel(viewModelStoreOwner)
    val uiState by timelineViewModel.uiState.collectAsStateWithLifecycle()

    val timelineContentAlpha = remember {
        Animatable(if (uiState.isLoaded) 1f else 0f)
    }

    LaunchedEffect(uiState.isLoaded) {
        if (uiState.isLoaded && timelineContentAlpha.value < 1f) {
            timelineContentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(TimelineFirstContentEnterDurationMillis, easing = FastOutSlowInEasing),
            )
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val memoCardActions = MemoCardActions(
        onOpen = { memo ->
            navigator.navigate(MemoDetailKey(memo.id))
        },
        onEdit = { memoId ->
            navigator.navigate(EditorKey(memoId))
        },
        onTogglePin = { memoId, pinned ->
            scope.launch { timelineViewModel.updateMemoPinned(memoId, pinned) }
        },
        onArchive = { memoId ->
            scope.launch { timelineViewModel.archiveMemo(memoId) }
        },
        onDelete = { memoId ->
            scope.launch { timelineViewModel.deleteMemo(memoId) }
        },
        onUpdateContent = { memoId, content ->
            scope.launch { timelineViewModel.updateMemoContent(memoId, content) }
        },
        onCacheResource = { resourceId, uri ->
            scope.launch { timelineViewModel.cacheResourceFile(resourceId, uri) }
        },
        onDownloadAndCache = { resource ->
            timelineViewModel.downloadAndCacheResource(resource)
        },
    )

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
            contentAlpha = timelineContentAlpha.value,
            listState = listState,
            showNavigationMenu = drawerState != null,
            onMenuClick = {
                scope.launch { drawerState?.open() }
            },
            onSearchClick = {
                navigator.navigate(SearchKey)
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
                navigator.navigate(TagKey(tag), singleTop = true)
            },
            isRemoteAccount = !uiState.isLocalAccount,
            host = uiState.host,
            defaultVisibility = uiState.defaultVisibility,
            actions = memoCardActions,
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
private const val TimelineFirstContentEnterDurationMillis = 180
