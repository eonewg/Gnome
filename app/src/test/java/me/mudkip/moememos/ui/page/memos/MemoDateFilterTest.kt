package me.mudkip.moememos.ui.page.memos

import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.MemoVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MemoDateFilterTest {
    private val memo = MemoEntity(
        identifier = "memo",
        accountKey = "test",
        content = "content",
        date = Instant.parse("2026-08-20T16:30:00Z"),
        visibility = MemoVisibility.PRIVATE,
        pinned = false,
    )

    @Test
    fun `memo matches the local calendar day`() {
        assertTrue(
            memoMatchesDate(
                memo = memo,
                date = LocalDate.of(2026, 8, 21),
                offset = ZoneOffset.ofHours(8),
            )
        )
    }

    @Test
    fun `memo does not match the previous local calendar day`() {
        assertFalse(
            memoMatchesDate(
                memo = memo,
                date = LocalDate.of(2026, 8, 20),
                offset = ZoneOffset.ofHours(8),
            )
        )
    }
}
