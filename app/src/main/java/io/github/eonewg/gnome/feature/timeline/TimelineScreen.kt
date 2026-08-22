package io.github.eonewg.gnome.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.component.SyncStatusBadge
import io.github.eonewg.gnome.ui.page.memos.MemosList
import io.github.eonewg.gnome.ui.theme.GnomeDesign

/**
 * Stateful visuals of the timeline. Renders [TimelineUiState] and forwards
 * every interaction upward; owns nothing but transient menu/dialog input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    uiState: TimelineUiState,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    showNavigationMenu: Boolean,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onSortOrderSelected: (MemoSortOrder) -> Unit,
    onToggleSelection: (Memo) -> Unit,
    onRequestAddTag: () -> Unit,
    onAddTag: (String) -> Unit,
    onDismissAddTag: () -> Unit,
    onRequestBatchDelete: () -> Unit,
    onDeleteSelection: () -> Unit,
    onDismissBatchDelete: () -> Unit,
    onCopySelection: () -> Unit,
    onSync: () -> Unit,
    onCompose: () -> Unit,
    onRefresh: suspend () -> Unit,
    onDismissSyncAlert: () -> Unit,
    onConfirmSyncWithVersion: (String) -> Unit,
    onTagClick: (String) -> Unit,
) {
    val colors = GnomeDesign.colors
    var homeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appBackground,
                    scrolledContainerColor = colors.appBackground,
                ),
                title = {
                    if (uiState.selectionMode) {
                        Text(
                            text = stringResource(R.string.selected_memos_count, uiState.selectedMemoIds.size),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                        )
                    } else {
                        HomeTitleMenu(
                            menuExpanded = homeMenuExpanded,
                            sortMenuExpanded = sortMenuExpanded,
                            sortOrder = uiState.sortOrder,
                            onMenuExpandedChange = { homeMenuExpanded = it },
                            onSortMenuExpandedChange = { sortMenuExpanded = it },
                            onEnterSelectionMode = {
                                homeMenuExpanded = false
                                onEnterSelectionMode()
                            },
                            onSortOrderSelected = { selectedOrder ->
                                onSortOrderSelected(selectedOrder)
                                sortMenuExpanded = false
                            },
                        )
                    }
                },
                navigationIcon = {
                    if (!uiState.selectionMode && showNavigationMenu) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = R.string.menu.string,
                                tint = colors.textSecondary,
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.selectionMode) {
                        TextButton(
                            enabled = !uiState.batchRunning,
                            onClick = onExitSelectionMode,
                        ) {
                            Text(
                                text = stringResource(R.string.done),
                                color = colors.textSecondary,
                            )
                        }
                    } else {
                        if (!uiState.isLocalAccount) {
                            SyncStatusBadge(
                                syncing = uiState.syncStatus.syncing,
                                unsyncedCount = uiState.syncStatus.unsyncedCount,
                                errorMessage = uiState.syncStatus.errorMessage,
                                onSync = onSync,
                            )
                        }
                        IconButton(onClick = onSearchClick) {
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
            if (uiState.selectionMode) {
                MemoSelectionBottomBar(
                    selectionCount = uiState.selectedMemoIds.size,
                    enabled = !uiState.batchRunning,
                    onAddTag = onRequestAddTag,
                    onCopyAll = onCopySelection,
                    onDelete = onRequestBatchDelete,
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButtonPosition = FabPosition.Center,

        floatingActionButton = {
            if (!uiState.selectionMode) {
                Surface(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .size(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.accent,
                    contentColor = colors.cardBackground,
                    shadowElevation = 3.dp,
                    onClick = onCompose,
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
                memos = uiState.memos,
                lazyListState = listState,
                contentPadding = innerPadding,
                additionalBottomPadding = if (uiState.selectionMode) 8.dp else TimelineFabAvoidancePadding,
                sortOrder = uiState.sortOrder,
                selectionMode = uiState.selectionMode,
                selectedMemoIds = uiState.selectedMemoIds,
                onSelectionToggle = onToggleSelection,
                onRefresh = onRefresh,
                onTagClick = onTagClick,
                loadOnStart = false,
            )
        }
    )

    if (uiState.showAddTagDialog) {
        AddTagToMemosDialog(
            existingTags = uiState.tags,
            enabled = !uiState.batchRunning,
            onDismiss = onDismissAddTag,
            onConfirm = onAddTag,
        )
    }

    if (uiState.showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissBatchDelete,
            title = {
                Text(stringResource(R.string.delete_selected_memos, uiState.selectedMemoIds.size))
            },
            text = {
                Text(stringResource(R.string.delete_selected_memos_message))
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.batchRunning,
                    onClick = onDeleteSelection,
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.batchRunning,
                    onClick = onDismissBatchDelete,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    when (val alert = uiState.syncAlert) {
        null -> Unit
        is TimelineSyncAlert.Blocked -> {
            AlertDialog(
                onDismissRequest = onDismissSyncAlert,
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = onDismissSyncAlert) {
                        Text(R.string.close.string)
                    }
                }
            )
        }
        is TimelineSyncAlert.RequiresConfirmation -> {
            AlertDialog(
                onDismissRequest = onDismissSyncAlert,
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { onConfirmSyncWithVersion(alert.version) }) {
                        Text(R.string.still_sync.string)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissSyncAlert) {
                        Text(R.string.cancel.string)
                    }
                }
            )
        }
        is TimelineSyncAlert.Failed -> {
            AlertDialog(
                onDismissRequest = onDismissSyncAlert,
                title = { Text(R.string.sync_failed.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = onDismissSyncAlert) {
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
    val colors = GnomeDesign.colors
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
    val colors = GnomeDesign.colors
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
    val colors = GnomeDesign.colors
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
    val colors = GnomeDesign.colors
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
    contentColor: Color = GnomeDesign.colors.textPrimary,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val effectiveContentColor = if (enabled) {
        contentColor
    } else {
        GnomeDesign.colors.textSecondary.copy(alpha = 0.35f)
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
                        color = GnomeDesign.colors.textSecondary,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(existingTags, key = { it }) { tag ->
                            Text(
                                text = "#$tag",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { input = tag }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                color = GnomeDesign.colors.tagForeground,
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

private val TimelineFabAvoidancePadding = 96.dp
