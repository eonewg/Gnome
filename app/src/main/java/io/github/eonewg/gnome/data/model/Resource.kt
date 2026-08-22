package io.github.eonewg.gnome.data.model

import java.time.Instant

data class Resource(
    override val remoteId: String,
    override val date: Instant,
    override val filename: String,
    override val mimeType: String? = null,
    override val uri: String,
    override val localUri: String? = null,
) : ResourceRepresentable {
    /** Wire resources have no local row yet; the remote id stands in. */
    override val identifier: String get() = remoteId
}
