package me.mudkip.moememos.ui.page.memos

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.SyncStatusBadge
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.page.memoinput.MemoInputPage
import me.mudkip.moememos.ui.page.memoinput.MemoInputPresentation
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.ManualSyncResult
import me.mudkip.moememos.util.extractCustomTags
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosHomePage(
    drawerState: DrawerState? = null,
    navController: NavHostController,
    quickMemoRequestId: Long = 0L,
    onMemoInputActiveChange: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val syncStatus by memosViewModel.syncStatus.collectAsState()
    val colors = MoeMemosDesign.colors
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val snackbarState = remember { SnackbarHostState() }

    var syncAlert by remember { mutableStateOf<HomeSyncAlert?>(null) }
    var showMemoInput by rememberSaveable { mutableStateOf(false) }
    var homeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortOrder by rememberSaveable { mutableStateOf(MemoSortOrder.CreatedNewest) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedMemoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var batchOperationRunning by remember { mutableStateOf(false) }
    val currentShowMemoInput by rememberUpdatedState(showMemoInput)
    val selectedMemos = remember(memosViewModel.memos, selectedMemoIds, sortOrder) {
        orderMemosForTimeline(memosViewModel.memos, sortOrder)
            .filter { it.identifier in selectedMemoIds }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedMemoIds = emptySet()
        showAddTagDialog = false
        showBatchDeleteDialog = false
    }

    fun toggleMemoSelection(memo: MemoEntity) {
        selectedMemoIds = if (memo.identifier in selectedMemoIds) {
            selectedMemoIds - memo.identifier
        } else {
            selectedMemoIds + memo.identifier
        }
    }

    fun openMemoInput() {
        exitSelectionMode()
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

    fun addTagToSelectedMemos(tag: String) {
        val targets = selectedMemos
        if (targets.isEmpty()) return
        scope.launch {
            batchOperationRunning = true
            var failedCount = 0
            targets.forEach { memo ->
                if (tag !in extractCustomTags(memo.content)) {
                    val updatedContent = if (memo.content.isBlank()) {
                        "#$tag"
                    } else {
                        "#$tag ${memo.content}"
                    }
                    val response = memosViewModel.editMemo(
                        memoIdentifier = memo.identifier,
                        content = updatedContent,
                        resourceList = memo.resources,
                        visibility = memo.visibility,
                    )
                    if (response !is ApiResponse.Success) {
                        failedCount++
                    }
                }
            }
            batchOperationRunning = false
            showAddTagDialog = false
            if (failedCount == 0) {
                exitSelectionMode()
                snackbarState.showSnackbar(context.getString(R.string.tag_added_to_memos, targets.size))
            } else {
                snackbarState.showSnackbar(
                    context.getString(R.string.batch_operation_failed, failedCount)
                )
            }
        }
    }

    fun deleteSelectedMemos() {
        val targets = selectedMemos
        if (targets.isEmpty()) return
        scope.launch {
            batchOperationRunning = true
            var failedCount = 0
            targets.forEach { memo ->
                if (memosViewModel.deleteMemo(memo.identifier) !is ApiResponse.Success) {
                    failedCount++
                }
            }
            batchOperationRunning = false
            showBatchDeleteDialog = false
            selectedMemoIds = selectedMemoIds.filterTo(mutableSetOf()) { identifier ->
                memosViewModel.memos.any { it.identifier == identifier }
            }
            if (failedCount == 0) {
                exitSelectionMode()
                snackbarState.showSnackbar(context.getString(R.string.memos_deleted, targets.size))
            } else {
                snackbarState.showSnackbar(
                    context.getString(R.string.batch_operation_failed, failedCount)
                )
            }
        }
    }

    suspend fun requestManualSync(allowHigherV1Version: String? = null) {
        when (val result = memosViewModel.refreshMemos(allowHigherV1Version)) {
            ManualSyncResult.Completed -> Unit
            is ManualSyncResult.Blocked -> {
                syncAlert = HomeSyncAlert.Blocked(result.message)
            }
            is ManualSyncResult.RequiresConfirmation -> {
                syncAlert = HomeSyncAlert.RequiresConfirmation(result.version, result.message)
            }
            is ManualSyncResult.Failed -> {
                syncAlert = HomeSyncAlert.Failed(result.message)
            }
        }
    }

    BackHandler(enabled = showMemoInput) {
        closeMemoInput()
    }

    BackHandler(enabled = selectionMode && !showMemoInput) {
        exitSelectionMode()
    }

    LaunchedEffect(quickMemoRequestId) {
        if (quickMemoRequestId > 0L) {
            exitSelectionMode()
            openMemoInput()
        }
    }

    LaunchedEffect(showMemoInput, selectionMode) {
        onMemoInputActiveChange(showMemoInput || selectionMode)
    }

    // The home page handles IME placement itself. Set this once for the lifetime of the page;
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
        Scaffold(
            containerColor = colors.appBackground,
            topBar = {
                TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appBackground,
                    scrolledContainerColor = colors.appBackground,
                ),
                title = {
                    if (selectionMode) {
                        Text(
                            text = stringResource(R.string.selected_memos_count, selectedMemoIds.size),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                        )
                    } else {
                        HomeTitleMenu(
                            menuExpanded = homeMenuExpanded,
                            sortMenuExpanded = sortMenuExpanded,
                            sortOrder = sortOrder,
                            onMenuExpandedChange = { homeMenuExpanded = it },
                            onSortMenuExpandedChange = { sortMenuExpanded = it },
                            onEnterSelectionMode = {
                                homeMenuExpanded = false
                                selectionMode = true
                            },
                            onSortOrderSelected = { selectedOrder ->
                                sortOrder = selectedOrder
                                sortMenuExpanded = false
                            },
                        )
                    }
                },
                navigationIcon = {
                    if (!selectionMode && drawerState != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = R.string.menu.string,
                                tint = colors.textSecondary,
                            )
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        TextButton(
                            enabled = !batchOperationRunning,
                            onClick = { exitSelectionMode() },
                        ) {
                            Text(
                                text = stringResource(R.string.done),
                                color = colors.textSecondary,
                            )
                        }
                    } else {
                        if (currentAccount !is Account.Local) {
                            SyncStatusBadge(
                                syncing = syncStatus.syncing,
                                unsyncedCount = syncStatus.unsyncedCount,
                                errorMessage = syncStatus.errorMessage,
                                onSync = {
                                    scope.launch {
                                        requestManualSync()
                                    }
                                }
                            )
                        }
                        IconButton(onClick = {
                            navController.navigate(RouteName.SEARCH)
                        }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = R.string.search.string,
                                tint = colors.textSecondary,
                            )
                        }
                    }
                }
                )
            },
            bottomBar = {
                if (selectionMode) {
                    MemoSelectionBottomBar(
                        selectionCount = selectedMemoIds.size,
                        enabled = !batchOperationRunning,
                        onAddTag = { showAddTagDialog = true },
                        onCopyAll = { copySelectedMemos() },
                        onDelete = { showBatchDeleteDialog = true },
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarState)
            },
            floatingActionButtonPosition = FabPosition.Center,

            floatingActionButton = {
                if (!selectionMode) {
                    Surface(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .size(60.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = colors.accent,
                        contentColor = colors.cardBackground,
                        shadowElevation = 3.dp,
                        onClick = { openMemoInput() },
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = R.string.compose.string,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            },

            content = { innerPadding ->
                MemosList(
                    lazyListState = listState,
                    contentPadding = innerPadding,
                    additionalBottomPadding = if (selectionMode) 8.dp else MemoListFabAvoidancePadding,
                    sortOrder = sortOrder,
                    selectionMode = selectionMode,
                    selectedMemoIds = selectedMemoIds,
                    onSelectionToggle = { memo -> toggleMemoSelection(memo) },
                    onRefresh = { requestManualSync() },
                    onTagClick = { tag ->
                        navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        )

        // Keep the editor composed from the initial home composition. Doing this behind the
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
                    MemoInputPage(
                        presentation = MemoInputPresentation.BottomSheet,
                        onFinished = { closeMemoInput() },
                        active = showMemoInput,
                    )
                }
        }
    }

    if (showAddTagDialog) {
        AddTagToMemosDialog(
            existingTags = memosViewModel.tags,
            enabled = !batchOperationRunning,
            onDismiss = { showAddTagDialog = false },
            onConfirm = { tag -> addTagToSelectedMemos(tag) },
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!batchOperationRunning) {
                    showBatchDeleteDialog = false
                }
            },
            title = {
                Text(stringResource(R.string.delete_selected_memos, selectedMemoIds.size))
            },
            text = {
                Text(stringResource(R.string.delete_selected_memos_message))
            },
            confirmButton = {
                TextButton(
                    enabled = !batchOperationRunning,
                    onClick = { deleteSelectedMemos() },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !batchOperationRunning,
                    onClick = { showBatchDeleteDialog = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    when (val alert = syncAlert) {
        null -> Unit
        is HomeSyncAlert.Blocked -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.close.string)
                    }
                }
            )
        }
        is HomeSyncAlert.RequiresConfirmation -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            syncAlert = null
                            scope.launch {
                                requestManualSync(allowHigherV1Version = alert.version)
                            }
                        }
                    ) {
                        Text(R.string.still_sync.string)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.cancel.string)
                    }
                }
            )
        }
        is HomeSyncAlert.Failed -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.sync_failed.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.close.string)
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeTitleMenu(
    menuExpanded: Boolean,
    sortMenuExpanded: Boolean,
    sortOrder: MemoSortOrder,
    onMenuExpandedChange: (Boolean) -> Unit,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onSortOrderSelected: (MemoSortOrder) -> Unit,
) {
    val colors = MoeMemosDesign.colors
    val popupOffset = with(LocalDensity.current) {
        IntOffset(x = (-16).dp.roundToPx(), y = 32.dp.roundToPx())
    }

    fun dismissMenus() {
        onMenuExpandedChange(false)
        onSortMenuExpandedChange(false)
    }

    Box {
        Row(
            modifier = Modifier.clickable {
                if (menuExpanded) dismissMenus() else onMenuExpandedChange(true)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.memos),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.more_options),
                modifier = Modifier.size(20.dp),
                tint = colors.textSecondary,
            )
        }

        if (menuExpanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = popupOffset,
                onDismissRequest = ::dismissMenus,
                properties = PopupProperties(
                    focusable = true,
                    clippingEnabled = false,
                ),
            ) {
                Column(
                    modifier = Modifier.width(216.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = colors.cardBackground,
                        contentColor = colors.textPrimary,
                        tonalElevation = 0.dp,
                        shadowElevation = 14.dp,
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            ModernHomeMenuRow(
                                title = stringResource(R.string.select_memos),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                    )
                                },
                                onClick = onEnterSelectionMode,
                            )
                            ModernHomeMenuRow(
                                title = stringResource(R.string.sort_order),
                                subtitle = sortOrderLabel(sortOrder),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapVert,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                    )
                                },
                                trailing = {
                                    Icon(
                                        imageVector = if (sortMenuExpanded) {
                                            Icons.Filled.KeyboardArrowDown
                                        } else {
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                                        },
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                    )
                                },
                                onClick = {
                                    onSortMenuExpandedChange(!sortMenuExpanded)
                                },
                            )
                        }
                    }

                    if (sortMenuExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = colors.cardBackground,
                            contentColor = colors.textPrimary,
                            tonalElevation = 0.dp,
                            shadowElevation = 14.dp,
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                MemoSortOrder.entries.forEach { order ->
                                    ModernSortMenuRow(
                                        label = sortOrderLabel(order),
                                        selected = order == sortOrder,
                                        onClick = {
                                            onSortOrderSelected(order)
                                            dismissMenus()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernHomeMenuRow(
    title: String,
    subtitle: String? = null,
    icon: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MoeMemosDesign.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}

@Composable
private fun ModernSortMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MoeMemosDesign.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) colors.accent.copy(alpha = 0.10f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.accent else colors.textPrimary,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.accent,
            )
        }
    }
}

@Composable
private fun sortOrderLabel(sortOrder: MemoSortOrder): String {
    return stringResource(
        when (sortOrder) {
            MemoSortOrder.CreatedNewest -> R.string.sort_created_newest
            MemoSortOrder.CreatedOldest -> R.string.sort_created_oldest
            MemoSortOrder.UpdatedNewest -> R.string.sort_updated_newest
            MemoSortOrder.UpdatedOldest -> R.string.sort_updated_oldest
        }
    )
}

@Composable
private fun MemoSelectionBottomBar(
    selectionCount: Int,
    enabled: Boolean,
    onAddTag: () -> Unit,
    onCopyAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MoeMemosDesign.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = colors.cardBackground,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemoSelectionAction(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.add_tag),
                enabled = enabled && selectionCount > 0,
                icon = {
                    Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                },
                onClick = onAddTag,
            )
            MemoSelectionAction(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.copy_all),
                enabled = enabled && selectionCount > 0,
                icon = {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                },
                onClick = onCopyAll,
            )
            MemoSelectionAction(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.delete),
                enabled = enabled && selectionCount > 0,
                contentColor = MaterialTheme.colorScheme.error,
                icon = {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun MemoSelectionAction(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean,
    contentColor: Color = MoeMemosDesign.colors.textPrimary,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val effectiveContentColor = if (enabled) {
        contentColor
    } else {
        MoeMemosDesign.colors.textSecondary.copy(alpha = 0.35f)
    }
    TextButton(
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(vertical = 6.dp),
        onClick = onClick,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides effectiveContentColor,
            ) {
                icon()
            }
            Text(
                text = label,
                color = effectiveContentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AddTagToMemosDialog(
    existingTags: List<String>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val normalizedTag = input.trim().removePrefix("#")
    val hasInvalidCharacter = normalizedTag.any { it.isWhitespace() || it == '#' }
    val canConfirm = normalizedTag.isNotEmpty() && !hasInvalidCharacter && enabled

    AlertDialog(
        onDismissRequest = {
            if (enabled) onDismiss()
        },
        title = { Text(stringResource(R.string.add_tag)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tag_name)) },
                    placeholder = { Text(stringResource(R.string.tag_name_hint)) },
                    isError = input.isNotEmpty() && !canConfirm,
                    supportingText = {
                        if (input.isNotEmpty() && !canConfirm) {
                            Text(stringResource(R.string.invalid_tag_name))
                        }
                    },
                )
                if (existingTags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.existing_tags),
                        style = MaterialTheme.typography.labelLarge,
                        color = MoeMemosDesign.colors.textSecondary,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(existingTags, key = { it }) { tag ->
                            Text(
                                text = "#$tag",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { input = tag }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                color = MoeMemosDesign.colors.tagForeground,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(normalizedTag) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                enabled = enabled,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private val MemoListFabAvoidancePadding = 96.dp
private val HiddenMemoInputOffset = 1_000.dp

private sealed class HomeSyncAlert {
    data class Blocked(val message: String) : HomeSyncAlert()
    data class RequiresConfirmation(val version: String, val message: String) : HomeSyncAlert()
    data class Failed(val message: String) : HomeSyncAlert()
}
