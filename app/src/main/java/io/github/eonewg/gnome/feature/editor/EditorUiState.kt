package io.github.eonewg.gnome.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.MemoVisibility

/** How the editor is presented; both share the same core and visuals. */
enum class EditorPresentation {
    FullScreen,
    BottomSheet,
}

/** Immutable snapshot of everything the editor screen renders. */
data class EditorUiState(
    val memoIdentifier: String? = null,
    val text: TextFieldValue = TextFieldValue(""),
    val visibility: MemoVisibility = MemoVisibility.PRIVATE,
    val attachments: List<Attachment> = emptyList(),
    val tags: List<String> = emptyList(),
    val initialContent: String = "",
    val initialAttachmentCount: Int = 0,
    val submitting: Boolean = false,
    val initialized: Boolean = false,
    val isLocalAccount: Boolean = false,
) {
    val isEditMode: Boolean get() = memoIdentifier != null
    val canSubmit: Boolean get() = text.text.isNotEmpty() || attachments.isNotEmpty()
    val hasUnsavedChanges: Boolean
        get() = text.text != initialContent || attachments.size != initialAttachmentCount
}

/** One-shot signals from the editor core to the hosting route. */
sealed class EditorEvent {
    /** A memo was persisted locally; the host may close the editor now. */
    object Submitted : EditorEvent()

    /** Re-assert editor focus (e.g. after a batch of attachment uploads). */
    object Refocus : EditorEvent()

    data class ShowMessage(val message: String) : EditorEvent()
}

internal fun newEditorText(content: String): TextFieldValue =
    TextFieldValue(content, TextRange(content.length))
