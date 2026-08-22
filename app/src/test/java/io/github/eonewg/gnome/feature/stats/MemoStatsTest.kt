package io.github.eonewg.gnome.feature.stats

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.model.MemoVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

class MemoStatsTest {
    private val zone = ZoneId.of("Asia/Singapore")

    @Test
    fun `groups memos by local creation date and counts code points`() {
        val snapshot = calculateMemoStats(
            memos = listOf(
                memo("a", "你好🙂", "2026-08-20T16:30:00Z"),
                memo("b", "ab", "2026-08-21T02:00:00Z"),
            ),
            zoneId = zone,
        )

        val day = snapshot.day(LocalDate.of(2026, 8, 21))
        assertEquals(2, day.memoCount)
        assertEquals(5, day.characterCount)
        assertEquals(2, snapshot.totalMemoCount)
        assertEquals(1, snapshot.activeDayCount)
    }

    @Test
    fun `builds complete month and year summaries`() {
        val snapshot = calculateMemoStats(
            memos = listOf(
                memo("july", "1234", "2026-07-02T01:00:00Z"),
                memo("august-a", "123", "2026-08-01T01:00:00Z"),
                memo("august-b", "12", "2026-08-01T02:00:00Z"),
                memo("august-c", "1", "2026-08-03T01:00:00Z"),
            ),
            zoneId = zone,
        )

        val august = snapshot.month(YearMonth.of(2026, 8))
        assertEquals(3, august.memoCount)
        assertEquals(6, august.characterCount)
        assertEquals(2, august.activeDayCount)
        assertEquals(2, august.maxDailyMemoCount)
        assertEquals(5, august.maxDailyCharacterCount)

        val year = snapshot.year(Year.of(2026))
        assertEquals(12, year.months.size)
        assertEquals(4, year.memoCount)
        assertEquals(10, year.characterCount)
        assertEquals(3, year.activeDayCount)
    }

    @Test
    fun `fills empty periods used by calendar and heatmap charts`() {
        val snapshot = calculateMemoStats(
            memos = listOf(memo("one", "x", "2026-07-02T01:00:00Z")),
            zoneId = zone,
        )

        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7)),
            snapshot.monthsThrough(YearMonth.of(2026, 8)).map { it.month },
        )
        assertEquals(
            listOf(0, 1, 0),
            snapshot.daysBetween(
                start = LocalDate.of(2026, 7, 1),
                endInclusive = LocalDate.of(2026, 7, 3),
            ).map { it.memoCount },
        )
    }

    @Test
    fun buildsSimpleChartAxisTicks() {
        assertEquals(listOf(8, 4, 0), chartAxisTicks(listOf(1, 8, 3)))
        assertEquals(listOf(5, 3, 0), chartAxisTicks(listOf(5)))
        assertEquals(listOf(1, 0), chartAxisTicks(emptyList()))
    }

    private fun memo(id: String, content: String, instant: String) = MemoStatsInput(
        date = Instant.parse(instant),
        content = content,
    )
}
