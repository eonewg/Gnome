package io.github.eonewg.gnome.feature.search

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.model.MemoVisibility

/** Immutable snapshot of the search screen state. */
data class SearchUiState(
    val query: String = "",
    val includeArchived: Boolean = false,
    val results: List<Memo> = emptyList(),
    /** True once a non-blank query has run, so the UI can distinguish
     *  "no results" from "type something first". */
    val hasSearched: Boolean = false,
    val isRemoteAccount: Boolean = false,
    val host: String? = null,
    val defaultVisibility: MemoVisibility? = null,
)