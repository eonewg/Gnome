package io.github.eonewg.gnome.widget

import io.github.eonewg.gnome.core.model.Memo

/**
 * Pure widget-side selection logic over the domain timeline snapshot
 * (already excludes archived/deleted rows further down the stack).
 *
 * Kept free of Android types so the widget data path (selection, cap,
 * memory pick) is unit-testable and identical for every widget instance.
 */
fun selectWidgetMemos(
    memos: List<Memo>,
    filterTag: String? = null,
    pinnedOnly: Boolean = false,
    maxItems: Int = 10,
): List<Memo> {
    val filtered = memos.filter { memo ->
        val matchesTag = filterTag == null || memo.content.contains("#$filterTag")
        val matchesPinned = !pinnedOnly || memo.pinned
        matchesTag && matchesPinned
    }
    return filtered
        .sortedWith(compareByDescending<Memo> { it.pinned }.thenByDescending { it.date })
        .take(maxItems)
}

/** The memory widget's single random pick; null on an empty timeline. */
fun pickMemoryMemo(memos: List<Memo>): Memo? = memos.randomOrNull()