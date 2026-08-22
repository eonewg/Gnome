package io.github.eonewg.gnome.ui.component

import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.toData
import io.github.eonewg.gnome.data.model.MemoRepresentable
import io.github.eonewg.gnome.data.model.ResourceRepresentable

/**
 * Bridges domain models into the UI layer's [MemoRepresentable] /
 * [ResourceRepresentable] contracts while components migrate off Room
 * entities. Shrinks away as components take domain types directly.
 */
fun Memo.toRepresentable(): MemoRepresentable = object : MemoRepresentable {
    override val remoteId: String? = this@toRepresentable.remoteId
    override val content: String = this@toRepresentable.content
    override val date: java.time.Instant = this@toRepresentable.date
    override val pinned: Boolean = this@toRepresentable.pinned
    override val visibility = this@toRepresentable.visibility.toData()
    override val resources: List<ResourceRepresentable> =
        this@toRepresentable.attachments.map { it.toResourceRepresentable() }
    override val archived: Boolean = this@toRepresentable.archived
}

fun Attachment.toResourceRepresentable(): ResourceRepresentable = object : ResourceRepresentable {
    override val identifier: String = this@toResourceRepresentable.id
    override val remoteId: String? = this@toResourceRepresentable.remoteId
    override val date: java.time.Instant = this@toResourceRepresentable.date
    override val filename: String = this@toResourceRepresentable.filename
    override val mimeType: String? = this@toResourceRepresentable.mimeType
    override val uri: String = this@toResourceRepresentable.uri
    override val localUri: String? = this@toResourceRepresentable.localUri
}
