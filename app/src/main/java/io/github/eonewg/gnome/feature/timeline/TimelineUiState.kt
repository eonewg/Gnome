package io.github.eonewg.gnome.feature.timeline

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.model.SyncStatus

/** Immutable snapshot of everything the timeline screen renders. */
data class TimelineUiState(
    val memos: List<Memo> = emptyList(),
    val tags: List<String> = emptyList(),
    val sortOrder: MemoSortOrder = MemoSortOrder.CreatedNewest,
    val selectionMode: Boolean = false,
    val selectedMemoIds: Set<String> = emptySet(),
    val batchRunning: Boolean = false,
    val showAddTagDialog: Boolean = false,
    val showBatchDeleteDialog: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus(),
    val isLocalAccount: Boolean = false,
    val errorMessage: String? = null,
    val syncAlert: TimelineSyncAlert? = null,
)

/** Version-compatibility problems surfaced while syncing manually. */
sealed class TimelineSyncAlert {
    data class Blocked(val message: String) : TimelineSyncAlert()
    data class RequiresConfirmation(val version: String, val message: String) : TimelineSyncAlert()
    data class Failed(val message: String) : TimelineSyncAlert()
}

/** One-shot snackbar messages emitted after batch operations. */
sealed class TimelineMessage {
    data class MemosCopied(val count: Int) : TimelineMessage()
    data class TagAdded(val count: Int) : TimelineMessage()
    data class MemosDeleted(val count: Int) : TimelineMessage()
    data class BatchFailed(val count: Int) : TimelineMessage()
}
