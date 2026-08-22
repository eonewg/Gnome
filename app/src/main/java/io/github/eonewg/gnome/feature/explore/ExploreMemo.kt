package io.github.eonewg.gnome.feature.explore

import io.github.eonewg.gnome.data.model.MemoRepresentable
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.data.model.ResourceRepresentable
import java.time.Instant

/**
 * The explore feed's presentation model — the minimal projection of a remote
 * workspace memo the explore card renders. The paging layer keeps the wire
 * types ([io.github.eonewg.gnome.data.model.Memo]) behind this boundary.
 */
data class ExploreMemo(
    override val remoteId: String,
    override val content: String,
    override val date: Instant,
    override val pinned: Boolean,
    override val visibility: MemoVisibility,
    override val resources: List<ResourceRepresentable>,
    override val archived: Boolean,
    val creatorName: String?,
) : MemoRepresentable

/** Converts the wire workspace memo into the explore presentation model. */
fun io.github.eonewg.gnome.data.model.Memo.toExploreMemo(): ExploreMemo = ExploreMemo(
    remoteId = remoteId,
    content = content,
    date = date,
    pinned = pinned,
    visibility = visibility,
    resources = resources,
    archived = archived,
    creatorName = creator?.name?.takeIf { it.isNotBlank() },
)