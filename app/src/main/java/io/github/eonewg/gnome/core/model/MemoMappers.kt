package io.github.eonewg.gnome.core.model

import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.Memo as RemoteMemo
import io.github.eonewg.gnome.data.model.MemoVisibility as DataVisibility
import io.github.eonewg.gnome.data.model.Resource as RemoteResource

/**
 * Bridges between persistence (Room entities), the wire format (data.model
 * remote snapshots) and the domain model. Callers migrate gradually; nothing
 * here mutates anything.
 */

// ---------------------------------------------------------------------------
// Entity ↔ Domain
// ---------------------------------------------------------------------------

fun MemoEntity.toDomain(attachments: List<ResourceEntity> = emptyList()): Memo = Memo(
    id = identifier,
    remoteId = remoteId,
    content = content,
    date = date,
    visibility = visibility.toCore(),
    pinned = pinned,
    archived = archived,
    attachments = attachments.map { it.toDomain() },
    syncState = syncState(),
    lastModified = lastModified,
    lastSyncedAt = lastSyncedAt,
)

fun Memo.toEntity(accountKey: String): MemoEntity {
    val pending = syncState != SyncState.SYNCED
    return MemoEntity(
        identifier = id,
        remoteId = remoteId,
        accountKey = accountKey,
        content = content,
        date = date,
        visibility = visibility.toData(),
        pinned = pinned,
        archived = archived,
        needsSync = pending,
        isDeleted = syncState == SyncState.PENDING_DELETE,
        lastModified = lastModified,
        lastSyncedAt = lastSyncedAt,
    )
}

fun ResourceEntity.toDomain(): Attachment = Attachment(
    id = identifier,
    remoteId = remoteId,
    memoId = memoId,
    filename = filename,
    uri = uri,
    localUri = localUri,
    mimeType = mimeType,
    date = date,
)

fun Attachment.toEntity(accountKey: String): ResourceEntity = ResourceEntity(
    identifier = id,
    remoteId = remoteId,
    accountKey = accountKey,
    date = date,
    filename = filename,
    uri = uri,
    localUri = localUri,
    mimeType = mimeType,
    memoId = memoId,
)

fun MemoEntity.syncState(): SyncState = when {
    isDeleted -> SyncState.PENDING_DELETE
    needsSync && remoteId == null -> SyncState.PENDING_CREATE
    needsSync -> SyncState.PENDING_UPDATE
    else -> SyncState.SYNCED
}

// ---------------------------------------------------------------------------
// Remote snapshot → Domain
// ---------------------------------------------------------------------------

/**
 * Maps a server memo into the domain. Remote snapshots have no local
 * identifier; pass [localIdentifier] when the matching Room row is known so
 * the domain keeps a stable id across syncs.
 */
fun RemoteMemo.toDomain(localIdentifier: String? = null): Memo = Memo(
    id = localIdentifier ?: remoteId,
    remoteId = remoteId,
    content = content,
    date = date,
    visibility = visibility.toCore(),
    pinned = pinned,
    archived = archived,
    attachments = resources.map { it.toDomain() },
    syncState = SyncState.SYNCED,
    lastModified = updatedAt ?: date,
    lastSyncedAt = updatedAt ?: date,
)

fun RemoteResource.toDomain(): Attachment = Attachment(
    id = remoteId,
    remoteId = remoteId,
    memoId = null,
    filename = filename,
    uri = uri,
    localUri = localUri,
    mimeType = mimeType,
    date = date,
)

// ---------------------------------------------------------------------------
// Visibility bridge
// ---------------------------------------------------------------------------

fun DataVisibility.toCore(): MemoVisibility = when (this) {
    DataVisibility.PRIVATE -> MemoVisibility.PRIVATE
    DataVisibility.PROTECTED -> MemoVisibility.PROTECTED
    DataVisibility.PUBLIC -> MemoVisibility.PUBLIC
}

fun MemoVisibility.toData(): DataVisibility = when (this) {
    MemoVisibility.PRIVATE -> DataVisibility.PRIVATE
    MemoVisibility.PROTECTED -> DataVisibility.PROTECTED
    MemoVisibility.PUBLIC -> DataVisibility.PUBLIC
}
