package io.github.eonewg.gnome.core.model

import java.time.Instant

/**
 * A file attached to a memo. [uri] is the display/source URI (remote or
 * local); [localUri] points at the cached copy in app storage when present.
 */
data class Attachment(
    val id: String,
    val remoteId: String? = null,
    val memoId: String? = null,
    val filename: String,
    val uri: String,
    val localUri: String? = null,
    val mimeType: String? = null,
    val date: Instant = Instant.now(),
)
