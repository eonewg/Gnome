package io.github.eonewg.gnome.core.model

import java.time.Instant

/**
 * Sync state of a memo's local row, derived from Room flags.
 *
 * PENDING_* rows are covered by an outbox operation and will be pushed by the
 * SyncEngine on the next run; transitions back to SYNCED happen only after the
 * server confirmed the change.
 */
enum class SyncState {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
}

/**
 * Gnome's domain memo — what UI layers are allowed to see. Local identifier is
 * the stable key (survives offline creation); [remoteId] is set once the
 * server knows the memo.
 */
data class Memo(
    val id: String,
    val remoteId: String? = null,
    val content: String,
    val date: Instant,
    val visibility: MemoVisibility = MemoVisibility.PRIVATE,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val syncState: SyncState = SyncState.PENDING_CREATE,
    val lastModified: Instant = Instant.now(),
    val lastSyncedAt: Instant? = null,
)
