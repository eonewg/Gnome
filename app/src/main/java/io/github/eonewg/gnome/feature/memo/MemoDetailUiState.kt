package io.github.eonewg.gnome.feature.memo

import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.model.MemoVisibility

/** Immutable snapshot of the memo detail page. */
data class MemoDetailUiState(
    val memo: MemoEntity? = null,
    val isRemoteAccount: Boolean = false,
    val host: String? = null,
    val defaultVisibility: MemoVisibility? = null,
)