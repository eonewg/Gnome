package io.github.eonewg.gnome.data.model

import java.time.Instant

interface ResourceRepresentable {
    /** Local identifier — the stable key for caching and attachment ops. */
    val identifier: String
    val remoteId: String?
    val date: Instant
    val filename: String
    val mimeType: String?
    val uri: String
    val localUri: String?
}

interface MemoRepresentable {
    val remoteId: String?
    val content: String
    val date: Instant
    val pinned: Boolean
    val visibility: MemoVisibility
    val resources: List<ResourceRepresentable>
    val archived: Boolean
}
