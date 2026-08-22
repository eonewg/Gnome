package io.github.eonewg.gnome.feature.timeline

import io.github.eonewg.gnome.core.model.Memo
import java.time.LocalDate
import java.time.ZoneOffset

enum class MemoSortOrder {
    CreatedNewest,
    CreatedOldest,
    UpdatedNewest,
    UpdatedOldest,
}

/** Pinned memos first, then the requested ordering inside each group. */
fun orderMemosForTimeline(
    memos: List<Memo>,
    sortOrder: MemoSortOrder,
): List<Memo> {
    val comparator = when (sortOrder) {
        MemoSortOrder.CreatedNewest -> compareByDescending<Memo> { it.date }
        MemoSortOrder.CreatedOldest -> compareBy<Memo> { it.date }
        MemoSortOrder.UpdatedNewest -> compareByDescending<Memo> { it.lastModified }
        MemoSortOrder.UpdatedOldest -> compareBy<Memo> { it.lastModified }
    }
    val pinned = memos.filter { it.pinned }.sortedWith(comparator)
    val nonPinned = memos.filter { !it.pinned }.sortedWith(comparator)
    return pinned + nonPinned
}

fun memoMatchesDate(
    memo: Memo,
    date: LocalDate,
    offset: ZoneOffset,
): Boolean = memo.date.atOffset(offset).toLocalDate() == date
