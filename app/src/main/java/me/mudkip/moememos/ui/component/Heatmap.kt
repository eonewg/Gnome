package me.mudkip.moememos.ui.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import me.mudkip.moememos.viewmodel.LocalMemos
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil

@Composable
fun Heatmap() {
    val memosViewModel = LocalMemos.current
    val matrix = memosViewModel.matrix
    val density = LocalDensity.current
    val gapPx = with(density) { 2.dp.toPx() }
    val cornerRadiusPx = with(density) { 2.dp.toPx() }
    val todayBorderWidthPx = with(density) { 1.dp.toPx() }
    val today = remember { LocalDate.now() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val visibleDays = remember(matrix, constraints) {
            matrix.takeLast(countHeatmap(constraints))
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
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
                val color = when (day.count) {
                    0 -> Color(0xffeaeaea)
                    1 -> Color(0xff9be9a8)
                    2 -> Color(0xff40c463)
                    in 3..4 -> Color(0xff30a14e)
                    else -> Color(0xff216e39)
                }

                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = cellDrawSize,
                    cornerRadius = cornerRadius,
                )

                if (day.date == today) {
                    drawRoundRect(
                        color = Color(0xff333333),
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

fun countHeatmap(constraints: Constraints): Int {
    val cellSize = ceil(constraints.maxHeight.toDouble() / 7).toInt()
    if (cellSize <= 0) {
        return 0
    }
    val columns = constraints.maxWidth / cellSize
    val fullCells = columns * 7

    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val firstDayOfThisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val lastColumn = ChronoUnit.DAYS.between(firstDayOfThisWeek, LocalDate.now()).toInt() + 1
    if (lastColumn % 7 == 0) {
        return fullCells
    }
    return fullCells - 7 + lastColumn
}
