package io.github.eonewg.gnome.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.SyncState
import io.github.eonewg.gnome.data.model.MemoEditGesture
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.ext.icon
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ext.titleResource
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MemosCard(
    memo: Memo,
    editGesture: MemoEditGesture = MemoEditGesture.NONE,
    previewMode: Boolean = false,
    showSyncStatus: Boolean = false,
    onTagClick: ((String) -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: ((Memo) -> Unit)? = null,
    isRemoteAccount: Boolean = false,
    host: String? = null,
    defaultVisibility: MemoVisibility? = null,
    actions: MemoCardActions = MemoCardActions(),
) {
    val scope = rememberCoroutineScope()
    val colors = GnomeDesign.colors
    val representable = remember(memo) { memo.toRepresentable() }
    var previewExpanded by rememberSaveable(memo.id) { mutableStateOf(false) }

    val cardModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (selectionMode) {
                    onSelectionToggle?.invoke(memo)
                } else if (editGesture == MemoEditGesture.SINGLE) {
                    actions.onEdit(memo.id)
                } else {
                    actions.onOpen(memo)
                }
            },
            onLongClick = if (editGesture == MemoEditGesture.LONG) {
                { actions.onEdit(memo.id) }
            } else {
                null
            },
            onDoubleClick = if (editGesture == MemoEditGesture.DOUBLE) {
                { actions.onEdit(memo.id) }
            } else {
                null
            }
        )

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.cardBackground,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = when {
            selectionMode && selected -> BorderStroke(1.dp, colors.accent)
            memo.pinned -> BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f))
            else -> null
        },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(start = 18.dp, top = 6.dp, end = 6.dp)
                    .heightIn(min = 40.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    MemoSelectionIndicator(selected = selected)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    memo.date.toMemoTimestamp(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                if (showSyncStatus && memo.syncState != SyncState.SYNCED) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = R.string.memo_sync_pending.string,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (defaultVisibility != representable.visibility) {
                    Icon(
                        representable.visibility.icon,
                        contentDescription = stringResource(representable.visibility.titleResource),
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(18.dp),
                        tint = colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!selectionMode) {
                    MemosCardActionButton(
                        memo,
                        isRemoteAccount = isRemoteAccount,
                        host = host,
                        actions = actions,
                    )
                }
            }

            MemoContent(
                representable,
                previewMode = previewMode,
                imageBaseUrl = host,
                actions = actions,
                checkboxChange = { checked, startOffset, endOffset ->
                    if (selectionMode) {
                        onSelectionToggle?.invoke(memo)
                        return@MemoContent
                    }
                    scope.launch {
                        var text = memo.content.substring(startOffset, endOffset)
                        text = if (checked) {
                            text.replace("[ ]", "[x]")
                        } else {
                            text.replace("[x]", "[ ]")
                        }
                        actions.onUpdateContent(
                            memo.id,
                            memo.content.replaceRange(startOffset, endOffset, text),
                        )
                    }
                },
                isPreviewExpanded = previewExpanded,
                onPreviewExpandedChange = {
                    if (selectionMode) {
                        onSelectionToggle?.invoke(memo)
                    } else {
                        previewExpanded = it
                    }
                },
                onTagClick = if (selectionMode) null else onTagClick,
            )
        }
    }
}

@Composable
private fun MemoSelectionIndicator(
    selected: Boolean,
) {
    val colors = GnomeDesign.colors
    Box(
        modifier = Modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(colors.accent, CircleShape)
                } else {
                    Modifier.border(1.5.dp, colors.divider, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = colors.cardBackground,
            )
        }
    }
}

@Composable
fun MemosCardActionButton(
    memo: Memo,
    isRemoteAccount: Boolean = false,
    host: String? = null,
    actions: MemoCardActions = MemoCardActions(),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    var showDeleteDialog by remember { mutableStateOf(false) }
    val memoLabel = stringResource(R.string.memo)
    val colors = GnomeDesign.colors

    Box {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.width(216.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = colors.cardBackground,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MemoQuickAction(
                    icon = Icons.Outlined.Share,
                    label = R.string.share.string,
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, memo.content)
                            type = "text/plain"
                        }
                        menuExpanded = false
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                )
                MemoQuickAction(
                    icon = Icons.Outlined.Edit,
                    label = R.string.edit.string,
                    onClick = {
                        menuExpanded = false
                        actions.onEdit(memo.id)
                    },
                )
                MemoQuickAction(
                    icon = Icons.Outlined.ContentCopy,
                    label = R.string.copy.string,
                    onClick = {
                        clipboardManager?.setPrimaryClip(
                            ClipData.newPlainText(memoLabel, memo.content)
                        )
                        menuExpanded = false
                    },
                )
            }

            HorizontalDivider(color = colors.divider.copy(alpha = 0.7f))

            DropdownMenuItem(
                text = {
                    Text(
                        if (memo.pinned) R.string.unpin.string else R.string.pin.string,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                onClick = {
                    menuExpanded = false
                    actions.onTogglePin(memo.id, !memo.pinned)
                },
                contentPadding = MemoActionMenuPadding,
            )

            if (isRemoteAccount) {
                DropdownMenuItem(
                    text = {
                        Text(
                            R.string.copy_link.string,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        host?.let { host ->
                            val memoUrl = "$host/${memo.remoteId ?: memo.id}"
                            clipboardManager?.setPrimaryClip(
                                ClipData.newPlainText(R.string.copy_link.string, memoUrl)
                            )
                        }
                        menuExpanded = false
                    },
                    contentPadding = MemoActionMenuPadding,
                )
            }

            DropdownMenuItem(
                text = {
                    Text(
                        R.string.archive.string,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                onClick = {
                    menuExpanded = false
                    actions.onArchive(memo.id)
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = MemoActionMenuPadding,
            )

            DropdownMenuItem(
                text = {
                    Text(
                        R.string.delete.string,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                onClick = {
                    showDeleteDialog = true
                    menuExpanded = false
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                ),
                contentPadding = MemoActionMenuPadding,
            )

            HorizontalDivider(color = colors.divider.copy(alpha = 0.7f))
            MemoActionMetadata(memo = memo)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(R.string.delete_this_memo.string) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        actions.onDelete(memo.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(R.string.confirm.string)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text(R.string.cancel.string)
                }
            }
        )
    }
}

private val MemoActionMenuPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)

@Composable
private fun MemoQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = GnomeDesign.colors
    Column(
        modifier = Modifier
            .width(54.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}
@Composable
private fun MemoActionMetadata(
    memo: Memo,
) {
    val colors = GnomeDesign.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = stringResource(
                R.string.memo_character_count,
                memo.content.memoCharacterCount(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(
                R.string.memo_created_time,
                memo.date.toMemoTimestamp(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(
                R.string.memo_last_edited,
                memo.lastModified.toMemoTimestamp(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

private val MemoTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal fun String.memoCharacterCount(): Int {
    return codePointCount(0, length)
}

internal fun java.time.Instant.toMemoTimestamp(): String {
    return MemoTimestampFormatter.format(atZone(ZoneId.systemDefault()))
}
