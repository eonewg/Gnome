package io.github.eonewg.gnome.ui.component

import android.net.Uri
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo

/**
 * The mutable operations a memo card can trigger, supplied by the hosting
 * screen's ViewModel. Kept as one bundle so the card components stay free of
 * CompositionLocals and view-model lookups.
 */
data class MemoCardActions(
    /** Opens the memo (card tap with the default edit gesture). */
    val onOpen: (Memo) -> Unit = {},
    /** Opens the editor for the memo id (edit gesture and edit action). */
    val onEdit: (memoId: String) -> Unit = {},
    /** Pins or unpins the memo id to the given state. */
    val onTogglePin: (memoId: String, pinned: Boolean) -> Unit = { _, _ -> },
    /** Archives the memo id. */
    val onArchive: (memoId: String) -> Unit = {},
    /** Deletes the memo id (after the caller's own confirmation). */
    val onDelete: (memoId: String) -> Unit = {},
    /** Content write from checkbox toggles inside the rendered memo. */
    val onUpdateContent: (memoId: String, content: String) -> Unit = { _, _ -> },
    /** Persists a downloaded attachment file into the local cache. */
    val onCacheResource: (resourceId: String, uri: Uri) -> Unit = { _, _ -> },
    /** Downloads and caches an HTTP attachment; null leaves the fetch disabled. */
    val onDownloadAndCache: (suspend (Attachment) -> Uri?)? = null,
)