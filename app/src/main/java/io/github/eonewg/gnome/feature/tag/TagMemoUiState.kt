package io.github.eonewg.gnome.feature.tag

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility

/** Immutable snapshot of the per-tag memo list. */
data class TagMemoUiState(
    val tag: String? = null,
    val memos: List<Memo> = emptyList(),
    val isRemoteAccount: Boolean = false,
    val host: String? = null,
    val defaultVisibility: MemoVisibility? = null,
)