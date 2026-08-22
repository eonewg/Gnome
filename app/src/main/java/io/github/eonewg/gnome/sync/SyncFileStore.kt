package io.github.eonewg.gnome.sync

/**
 * Subset of file storage the sync engine needs; takes URI strings so the
 * engine stays JVM-unit-testable. Production wiring converts to android.net.Uri.
 */
fun interface SyncFileStore {
    fun deleteFile(uri: String)
}
