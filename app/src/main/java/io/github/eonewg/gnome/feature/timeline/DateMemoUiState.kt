package io.github.eonewg.gnome.feature.timeline

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility

/** Immutable snapshot of the per-date memo list; the date filter lives in the UI. */
data class DateMemoUiState(
    val memos: List<Memo> = emptyList(),
    val isRemoteAccount: Boolean = false,
    val host: String? = null,
    val defaultVisibility: MemoVisibility? = null,
)