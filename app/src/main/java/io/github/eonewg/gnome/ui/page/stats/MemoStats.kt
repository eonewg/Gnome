package io.github.eonewg.gnome.ui.page.stats

import io.github.eonewg.gnome.data.local.entity.MemoEntity
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

data class DayMemoStats(
    val date: LocalDate,
    val memoCount: Int = 0,
    val characterCount: Int = 0,
)

data class MonthMemoStats(
    val month: YearMonth,
    val days: List<DayMemoStats> = emptyList(),
) {
    val memoCount: Int = days.sumOf { it.memoCount }
    val characterCount: Int = days.sumOf { it.characterCount }
    val activeDayCount: Int = days.count { it.memoCount > 0 }
    val maxDailyMemoCount: Int = days.maxOfOrNull { it.memoCount } ?: 0
    val maxDailyCharacterCount: Int = days.maxOfOrNull { it.characterCount } ?: 0

    fun day(dayOfMonth: Int): DayMemoStats =
        days.firstOrNull { it.date.dayOfMonth == dayOfMonth }
            ?: DayMemoStats(month.atDay(dayOfMonth))
}

data class YearMemoStats(
    val year: Year,
    val months: List<MonthMemoStats>,
) {
    val memoCount: Int = months.sumOf { it.memoCount }
    val characterCount: Int = months.sumOf { it.characterCount }
    val activeDayCount: Int = months.sumOf { it.activeDayCount }
}

data class MemoStatsSnapshot(
    private val dayStats: Map<LocalDate, DayMemoStats>,
) {
    val totalMemoCount: Int = dayStats.values.sumOf { it.memoCount }
    val totalCharacterCount: Int = dayStats.values.sumOf { it.characterCount }
    val activeDayCount: Int = dayStats.values.count { it.memoCount > 0 }
    val earliestDate: LocalDate? = dayStats.keys.minOrNull()

    fun day(date: LocalDate): DayMemoStats = dayStats[date] ?: DayMemoStats(date)

    fun month(month: YearMonth): MonthMemoStats {
        val days = dayStats.values
            .asSequence()
            .filter { YearMonth.from(it.date) == month }
            .sortedBy { it.date }
            .toList()
        return MonthMemoStats(month = month, days = days)
    }

    fun year(year: Year): YearMemoStats = YearMemoStats(
        year = year,
        months = (1..12).map { month -> month(YearMonth.of(year.value, month)) },
    )

    fun monthsThrough(currentMonth: YearMonth): List<MonthMemoStats> {
        val firstMonth = earliestDate?.let(YearMonth::from) ?: currentMonth
        if (firstMonth > currentMonth) return listOf(month(currentMonth))

        return generateSequence(currentMonth) { previous ->
            previous.minusMonths(1).takeIf { it >= firstMonth }
        }.map(::month).toList()
    }

    fun yearsThrough(currentYear: Year): List<YearMemoStats> {
        val firstYear = earliestDate?.let { Year.from(it) } ?: currentYear
        if (firstYear > currentYear) return listOf(year(currentYear))

        return (currentYear.value downTo firstYear.value).map { year(Year.of(it)) }
    }

    fun daysBetween(start: LocalDate, endInclusive: LocalDate): List<DayMemoStats> {
        if (endInclusive < start) return emptyList()
        return generateSequence(start) { date ->
            date.plusDays(1).takeIf { it <= endInclusive }
        }.map(::day).toList()
    }
}

fun calculateMemoStats(
    memos: List<MemoEntity>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MemoStatsSnapshot {
    val days = memos
        .groupBy { memo -> memo.date.atZone(zoneId).toLocalDate() }
        .mapValues { (date, dailyMemos) ->
            DayMemoStats(
                date = date,
                memoCount = dailyMemos.size,
                characterCount = dailyMemos.sumOf { memo -> memo.content.codePointCount() },
            )
        }
    return MemoStatsSnapshot(days)
}

private fun String.codePointCount(): Int = codePointCount(0, length)

fun Int.compactNumber(): String = when {
    this >= 1_000_000 -> formatCompact(this / 1_000_000.0, "M")
    this >= 1_000 -> formatCompact(this / 1_000.0, "K")
    else -> toString()
}

private fun formatCompact(value: Double, suffix: String): String {
    val displayed = if (value >= 100 || value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.1f", value)
    }
    return displayed + suffix
}
