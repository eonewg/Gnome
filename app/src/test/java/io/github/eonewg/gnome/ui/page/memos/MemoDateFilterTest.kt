package io.github.eonewg.gnome.ui.page.memos

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MemoDateFilterTest {
    private val memo = Memo(
        id = "memo",
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
