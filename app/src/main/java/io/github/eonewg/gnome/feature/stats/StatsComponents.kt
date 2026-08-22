package io.github.eonewg.gnome.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun StatsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = GnomeDesign.colors.cardBackground,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            content = content,
        )
    }
}

@Composable
internal fun MonthCalendar(
    stats: MonthMemoStats,
    onDateClick: (java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val weekFields = remember(locale) { WeekFields.of(locale) }
    val firstWeekday = weekFields.firstDayOfWeek
    val weekdays = remember(locale, firstWeekday) {
        List(7) { index ->
            firstWeekday.plus(index.toLong())
                .getDisplayName(TextStyle.NARROW, locale)
        }
    }
    val leadingCells = weekdayIndex(stats.month.atDay(1).dayOfWeek, firstWeekday)
    val cellCount = leadingCells + stats.month.lengthOfMonth()
    val rowCount = (cellCount + 6) / 7
    val colors = GnomeDesign.colors

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { weekday ->
                Text(
                    text = weekday,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        repeat(rowCount) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayOfMonth = cellIndex - leadingCells + 1
                    val day = dayOfMonth.takeIf { it in 1..stats.month.lengthOfMonth() }?.let(stats::day)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (day != null) Modifier.clickable(role = Role.Button) { onDateClick(day.date) }
                                else Modifier,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (day != null) {
                            Text(
                                text = dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(
                                        color = heatColor(day.memoCount, colors.accent, colors.divider),
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            )
                        } else {
                            Text(
                                text = "",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatsBarChart(
    values: List<Int>,
    labels: List<String>,
    color: Color,
    description: String,
    selectionLabels: List<String> = labels,
    unitLabel: String = "",
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cornerRadius = with(density) { 4.dp.toPx() }
    val colors = GnomeDesign.colors
    val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val axisTicks = chartAxisTicks(values)
    var selectedIndex by remember(values) { mutableStateOf<Int?>(null) }
    var tooltipSize by remember(values) { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = modifier.semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(values) {
                            detectTapGestures { offset ->
                                if (values.isNotEmpty() && size.width > 0) {
                                    selectedIndex = ((offset.x / size.width) * values.size)
                                        .toInt()
                                        .coerceIn(values.indices)
                                }
                            }
                        },
                ) {
                    if (values.isEmpty()) return@Canvas
                    val slotWidth = size.width / values.size
                    val barWidth = slotWidth * 0.58f
                    selectedIndex?.let { index ->
                        drawRoundRect(
                            color = colors.divider.copy(alpha = 0.2f),
                            topLeft = Offset(index * slotWidth, 0f),
                            size = Size(slotWidth, size.height),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        )
                    }
                    values.forEachIndexed { index, value ->
                        val ratio = value.toFloat() / maximum
                        val barHeight = if (value == 0) 2f else (size.height * ratio).coerceAtLeast(4f)
                        drawRoundRect(
                            color = if (value == 0) colors.divider.copy(alpha = 0.7f) else color,
                            topLeft = Offset(
                                x = index * slotWidth + (slotWidth - barWidth) / 2f,
                                y = size.height - barHeight,
                            ),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        )
                    }
                }

                selectedIndex?.let { index ->
                    val tooltipWidth = with(density) { tooltipSize.width.toDp() }
                    val tooltipHeight = with(density) { tooltipSize.height.toDp() }
                    val slotWidth = maxWidth / values.size.toFloat()
                    val anchorX = slotWidth * (index + 0.5f)
                    val tooltipX = (anchorX - tooltipWidth / 2f).coerceIn(
                        0.dp,
                        (maxWidth - tooltipWidth).coerceAtLeast(0.dp),
                    )
                    val ratio = values[index].toFloat() / maximum
                    val barTop = maxHeight * (1f - ratio)
                    val tooltipY = (barTop - tooltipHeight / 2f).coerceIn(
                        0.dp,
                        (maxHeight - tooltipHeight).coerceAtLeast(0.dp),
                    )
                    Surface(
                        modifier = Modifier
                            .offset(x = tooltipX, y = tooltipY)
                            .widthIn(max = minOf(180.dp, maxWidth))
                            .onGloballyPositioned { coordinates ->
                                if (tooltipSize != coordinates.size) {
                                    tooltipSize = coordinates.size
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.cardBackground,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = selectionLabels.getOrElse(index) { (index + 1).toString() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary.copy(alpha = 0.78f),
                                maxLines = 1,
                            )
                            Text(
                                text = buildString {
                                    append(values[index].compactNumber())
                                    if (unitLabel.isNotBlank()) {
                                        append(" ")
                                        append(unitLabel)
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textSecondary.copy(alpha = 0.78f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                axisTicks.forEach { tick ->
                    Text(
                        text = tick.compactNumber(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.weight(1f)) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(42.dp))
        }
    }
}

internal fun chartAxisTicks(values: List<Int>): List<Int> {
    val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    return listOf(maximum, (maximum + 1) / 2, 0).distinct()
}

@Composable
internal fun YearMetricCard(
    value: String,
    label: String,
    monthlyValues: List<Int>,
    color: Color,
    selectionLabels: List<String>,
    unitLabel: String,
    modifier: Modifier = Modifier,
) {
    StatsCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = GnomeDesign.colors.textPrimary,
            )
            Text(
                text = label,
                modifier = Modifier.padding(bottom = 5.dp),
                style = MaterialTheme.typography.titleMedium,
                color = GnomeDesign.colors.textPrimary,
            )
        }
        StatsBarChart(
            values = monthlyValues,
            labels = (1..12).map(Int::toString),
            color = color,
            description = "$value $label",
            selectionLabels = selectionLabels,
            unitLabel = unitLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .padding(top = 24.dp),
        )
    }
}

@Composable
internal fun Metric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = GnomeDesign.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = GnomeDesign.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun HistoryHeatmap(
    months: List<MonthMemoStats>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(months.size) {
        if (months.isNotEmpty()) {
            listState.scrollToItem(months.lastIndex)
        }
    }

    LazyRow(
        modifier = modifier.semantics { contentDescription = description },
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            items = months,
            key = { _, month -> month.month.toString() },
        ) { index, month ->
            HistoryMonthHeatmap(
                month = month,
                showYear = index == 0 || month.month.monthValue == 1,
            )
        }
    }
}

@Composable
private fun HistoryMonthHeatmap(
    month: MonthMemoStats,
    showYear: Boolean,
) {
    val colors = GnomeDesign.colors
    val locale = Locale.getDefault()
    val density = LocalDensity.current
    val firstWeekday = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val leadingCells = weekdayIndex(month.month.atDay(1).dayOfWeek, firstWeekday)
    val columns = (leadingCells + month.month.lengthOfMonth() + 6) / 7
    val cellSize = 10.dp
    val gap = 3.dp
    val width = cellSize * columns + gap * (columns - 1)
    val height = cellSize * 7 + gap * 6
    val formatter = remember(locale, showYear) {
        DateTimeFormatter.ofPattern(if (showYear) "yyyy MMM" else "MMM", locale)
    }
    var selectedDayOfMonth by remember(month.month) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(month.month) {
                        val stride = with(density) { (cellSize + gap).toPx() }
                        detectTapGestures { offset ->
                            val column = (offset.x / stride).toInt()
                            val row = (offset.y / stride).toInt()
                            val dayOfMonth = column * 7 + row - leadingCells + 1
                            selectedDayOfMonth = dayOfMonth.takeIf {
                                it in 1..month.month.lengthOfMonth()
                            }
                        }
                    },
            ) {
                val cellPx = cellSize.toPx()
                val gapPx = gap.toPx()
                val radius = 3.dp.toPx()
                repeat(month.month.lengthOfMonth()) { index ->
                    val position = leadingCells + index
                    val column = position / 7
                    val row = position % 7
                    val day = month.day(index + 1)
                    val topLeft = Offset(
                        x = column * (cellPx + gapPx),
                        y = row * (cellPx + gapPx),
                    )
                    drawRoundRect(
                        color = heatColor(day.memoCount, colors.accent, colors.divider),
                        topLeft = topLeft,
                        size = Size(cellPx, cellPx),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    if (selectedDayOfMonth == index + 1) {
                        drawRoundRect(
                            color = colors.textSecondary,
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }

            selectedDayOfMonth?.let { dayOfMonth ->
                val position = leadingCells + dayOfMonth - 1
                val column = position / 7
                val row = position % 7
                val popupOffset = with(density) {
                    IntOffset(
                        x = ((cellSize + gap) * column + cellSize).roundToPx(),
                        y = ((cellSize + gap) * row - 42.dp).roundToPx(),
                    )
                }
                val day = month.day(dayOfMonth)
                Popup(
                    alignment = Alignment.TopStart,
                    offset = popupOffset,
                    onDismissRequest = { selectedDayOfMonth = null },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.cardBackground,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.stats_heatmap_day_tooltip,
                                day.memoCount,
                                day.date.toString(),
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            text = month.month.format(formatter),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

private fun weekdayIndex(day: DayOfWeek, firstDay: DayOfWeek): Int =
    Math.floorMod(day.value - firstDay.value, 7)

private fun heatColor(count: Int, accent: Color, empty: Color): Color = when (count) {
    0 -> empty.copy(alpha = 0.58f)
    1 -> accent.copy(alpha = 0.26f)
    2 -> accent.copy(alpha = 0.48f)
    in 3..4 -> accent.copy(alpha = 0.74f)
    else -> accent
}
