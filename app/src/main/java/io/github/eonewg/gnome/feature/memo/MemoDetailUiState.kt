package io.github.eonewg.gnome.feature.memo

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility

/** Immutable snapshot of the memo detail page. */
data class MemoDetailUiState(
    val memo: Memo? = null,
    val isRemoteAccount: Boolean = false,
    val host: String? = null,
    val defaultVisibility: MemoVisibility? = null,
)