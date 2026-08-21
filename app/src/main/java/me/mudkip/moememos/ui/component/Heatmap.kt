package me.mudkip.moememos.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import me.mudkip.moememos.data.model.DailyUsageStat
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import me.mudkip.moememos.viewmodel.LocalMemos
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun Heatmap(
    modifier: Modifier = Modifier,
    onDateClick: (LocalDate) -> Unit = {},
) {
    val memosViewModel = LocalMemos.current
    val matrix = memosViewModel.matrix
    val colors = MoeMemosDesign.colors
    val density = LocalDensity.current
    val gapPx = with(density) { 3.dp.toPx() }
    val cornerRadiusPx = with(density) { 3.dp.toPx() }
    val todayBorderWidthPx = with(density) { 1.5.dp.toPx() }
    val today = remember { LocalDate.now() }

    BoxWithConstraints(modifier = modifier) {
        val visibleDays = remember(matrix, constraints) {
            matrix.takeLast(countHeatmap(constraints))
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visibleDays, gapPx) {
                    detectTapGestures { tapOffset ->
                        val cellSize = ((size.height - gapPx * 6) / 7).coerceAtLeast(0f)
                        if (cellSize <= 0f || visibleDays.isEmpty()) return@detectTapGestures

                        val columnCount = (visibleDays.size + 6) / 7
                        val contentWidth = columnCount * cellSize + (columnCount - 1) * gapPx
                        val startX = (size.width - contentWidth).coerceAtLeast(0f)
                        val localX = tapOffset.x - startX
                        if (localX < 0f || tapOffset.y < 0f) return@detectTapGestures

                        val column = floor(localX / (cellSize + gapPx)).toInt()
                        val row = floor(tapOffset.y / (cellSize + gapPx)).toInt()
                        if (column !in 0 until columnCount || row !in 0..6) {
                            return@detectTapGestures
                        }

                        val withinCellX = localX - column * (cellSize + gapPx)
                        val withinCellY = tapOffset.y - row * (cellSize + gapPx)
                        if (withinCellX > cellSize || withinCellY > cellSize) {
                            return@detectTapGestures
                        }

                        visibleDays.getOrNull(column * 7 + row)?.let { day ->
                            onDateClick(day.date)
                        }
                    }
                },
        ) {
            if (visibleDays.isEmpty()) return@Canvas

            val cellSize = ((size.height - gapPx * 6) / 7).coerceAtLeast(0f)
            if (cellSize <= 0f) return@Canvas

            val columnCount = (visibleDays.size + 6) / 7
            val contentWidth = columnCount * cellSize + (columnCount - 1) * gapPx
            val startX = (size.width - contentWidth).coerceAtLeast(0f)
            val cellDrawSize = Size(cellSize, cellSize)
            val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)

            visibleDays.forEachIndexed { index, day ->
                val column = index / 7
                val row = index % 7
                val topLeft = Offset(
                    x = startX + column * (cellSize + gapPx),
                    y = row * (cellSize + gapPx),
                )

                drawRoundRect(
                    color = heatmapColor(day, colors.accent, colors.divider),
                    topLeft = topLeft,
                    size = cellDrawSize,
                    cornerRadius = cornerRadius,
                )

                if (day.date == today) {
                    drawRoundRect(
                        color = colors.textPrimary,
                        topLeft = topLeft,
                        size = cellDrawSize,
                        cornerRadius = cornerRadius,
                        style = Stroke(width = todayBorderWidthPx),
                    )
                }
            }
        }
    }
}

private fun heatmapColor(day: DailyUsageStat, accent: Color, empty: Color) = when (day.count) {
    0 -> empty.copy(alpha = 0.58f)
    1 -> accent.copy(alpha = 0.24f)
    2 -> accent.copy(alpha = 0.46f)
    in 3..4 -> accent.copy(alpha = 0.72f)
    else -> accent
}

fun countHeatmap(constraints: Constraints): Int {
    val cellSize = ceil(constraints.maxHeight.toDouble() / 7).toInt()
    if (cellSize <= 0) return 0

    val columns = constraints.maxWidth / cellSize
    val fullCells = columns * 7
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val firstDayOfThisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val lastColumn = ChronoUnit.DAYS.between(firstDayOfThisWeek, LocalDate.now()).toInt() + 1
    return if (lastColumn % 7 == 0) fullCells else fullCells - 7 + lastColumn
}
