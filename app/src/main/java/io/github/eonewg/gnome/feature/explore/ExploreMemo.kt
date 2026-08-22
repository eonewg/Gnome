package io.github.eonewg.gnome.feature.explore

import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.core.model.toCore
import java.time.Instant

/**
 * The explore feed's presentation model — the minimal projection of a remote
 * workspace memo the explore card renders. The paging layer keeps the wire
 * types ([io.github.eonewg.gnome.data.model.Memo]) behind this boundary;
 * attachments are projected into domain [Attachment]s.
 */
data class ExploreMemo(
    val remoteId: String,
    val content: String,
    val date: Instant,
    val pinned: Boolean,
    val visibility: MemoVisibility,
    val resources: List<Attachment>,
    val archived: Boolean,
    val creatorName: String?,
)

/** Converts the wire workspace memo into the explore presentation model. */
fun io.github.eonewg.gnome.data.model.Memo.toExploreMemo(): ExploreMemo = ExploreMemo(
    remoteId = remoteId,
    content = content,
    date = date,
    pinned = pinned,
    visibility = visibility.toCore(),
    resources = resources.map { resource ->
        Attachment(
            id = resource.remoteId,
            remoteId = resource.remoteId,
            filename = resource.filename,
            uri = resource.uri,
            localUri = resource.localUri,
            mimeType = resource.mimeType,
            date = resource.date,
        )
    },
    archived = archived,
    creatorName = creator?.name?.takeIf { it.isNotBlank() },
)

/** Projects the explore presentation model into the memo card renderer. */
fun ExploreMemo.toContentMemo(): Memo = Memo(
    id = remoteId,
    remoteId = remoteId,
    content = content,
    date = date,
    pinned = pinned,
    visibility = visibility,
    archived = archived,
    attachments = resources,
)