package io.github.eonewg.gnome.data.model

import java.time.Instant

data class Resource(
    val remoteId: String,
    val date: Instant,
    val filename: String,
    val mimeType: String? = null,
    val uri: String,
    val localUri: String? = null,
)
