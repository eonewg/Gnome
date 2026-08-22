package io.github.eonewg.gnome.data.model

data class SyncStatus(
    val syncing: Boolean = false,
    val unsyncedCount: Int = 0,
    val errorMessage: String? = null
)
