package io.github.eonewg.gnome.ui.page.memos

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemoSortOrderTest {
    private val memos = listOf(
        memo("pinned-old", created = 1, updated = 4, pinned = true),
        memo("normal-old", created = 2, updated = 3),
        memo("normal-new", created = 3, updated = 2),
        memo("pinned-new", created = 4, updated = 1, pinned = true),
    )

    @Test
    fun `created newest keeps pinned memos first and sorts each group`() {
        assertOrder(
            MemoSortOrder.CreatedNewest,
            "pinned-new", "pinned-old", "normal-new", "normal-old",
        )
    }

    @Test
    fun `created oldest keeps pinned memos first and sorts each group`() {
        assertOrder(
            MemoSortOrder.CreatedOldest,
            "pinned-old", "pinned-new", "normal-old", "normal-new",
        )
    }

    @Test
    fun `updated newest uses last modified time`() {
        assertOrder(
            MemoSortOrder.UpdatedNewest,
            "pinned-old", "pinned-new", "normal-old", "normal-new",
        )
    }

    @Test
    fun `updated oldest uses last modified time`() {
        assertOrder(
            MemoSortOrder.UpdatedOldest,
            "pinned-new", "pinned-old", "normal-new", "normal-old",
        )
    }

    private fun assertOrder(sortOrder: MemoSortOrder, vararg identifiers: String) {
        assertEquals(
            identifiers.toList(),
            orderMemosForTimeline(memos, sortOrder).map { it.id },
        )
    }

    private fun memo(
        identifier: String,
        created: Long,
        updated: Long,
        pinned: Boolean = false,
    ) = Memo(
        id = identifier,
        content = identifier,
        date = Instant.ofEpochSecond(created),
        visibility = MemoVisibility.PRIVATE,
        pinned = pinned,
        lastModified = Instant.ofEpochSecond(updated),
    )
}
