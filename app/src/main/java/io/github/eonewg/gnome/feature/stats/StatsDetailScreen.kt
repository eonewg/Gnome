package io.github.eonewg.gnome.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailScreen(
    uiState: StatsUiState,
    navController: NavHostController,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = GnomeDesign.colors
    val zoneId = remember { ZoneId.systemDefault() }
    val currentMonth = remember(zoneId) { YearMonth.now(zoneId) }
    val snapshot = uiState.snapshot
    var selectedMonthText by rememberSaveable { mutableStateOf(currentMonth.toString()) }
    val selectedMonth = remember(selectedMonthText) { YearMonth.parse(selectedMonthText) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    val month = remember(snapshot, selectedMonth) { snapshot.month(selectedMonth) }
    val locale = Locale.getDefault()
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("yyyy-MM", locale)
    }
    val historyMonths = remember(snapshot, currentMonth) {
        snapshot.monthsThrough(currentMonth).asReversed()
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            selectedMonth = selectedMonth,
            currentMonth = currentMonth,
            onMonthSelected = { selected ->
                selectedMonthText = selected.toString()
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_record_statistics)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appBackground,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textSecondary,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    StatsCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = {
                                    selectedMonthText = selectedMonth.minusMonths(1).toString()
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.stats_previous_month),
                                    tint = colors.textSecondary,
                                )
                            }
                            Row(
                                modifier = Modifier.clickable { showMonthPicker = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selectedMonth.format(monthFormatter),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = stringResource(R.string.stats_select_month),
                                    tint = colors.textSecondary,
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (selectedMonth < currentMonth) {
                                        selectedMonthText = selectedMonth.plusMonths(1).toString()
                                    }
                                },
                                enabled = selectedMonth < currentMonth,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.stats_next_month),
                                    tint = if (selectedMonth < currentMonth) {
                                        colors.textSecondary
                                    } else {
                                        colors.divider
                                    },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Metric(
                                value = month.memoCount.toString(),
                                label = stringResource(R.string.stats_memo_count),
                                modifier = Modifier.weight(1f),
                            )
                            Metric(
                                value = month.characterCount.compactNumber(),
                                label = stringResource(R.string.stats_character_count),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Metric(
                                value = month.maxDailyMemoCount.toString(),
                                label = stringResource(R.string.stats_max_daily_memos),
                                modifier = Modifier.weight(1f),
                            )
                            Metric(
                                value = month.maxDailyCharacterCount.compactNumber(),
                                label = stringResource(R.string.stats_max_daily_characters),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Metric(
                            value = month.activeDayCount.toString(),
                            label = stringResource(R.string.stats_active_days),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 22.dp),
                        )
                        StatsBarChart(
                            values = (1..selectedMonth.lengthOfMonth()).map { month.day(it).memoCount },
                            labels = (1..selectedMonth.lengthOfMonth()).map { day ->
                                when {
                                    day == 1 || day == selectedMonth.lengthOfMonth() -> day.toString()
                                    (day - 1) % 7 == 0 -> day.toString()
                                    else -> ""
                                }
                            },
                            selectionLabels = (1..selectedMonth.lengthOfMonth()).map { day ->
                                selectedMonth.atDay(day).toString()
                            },
                            color = colors.accent,
                            unitLabel = stringResource(R.string.stats_memo_count),
                            description = stringResource(
                                R.string.stats_month_summary,
                                month.memoCount,
                                month.characterCount.compactNumber(),
                                month.activeDayCount,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .padding(top = 30.dp),
                        )
                    }
                }

                item {
                    StatsCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.stats_all_records) + " " +
                                snapshot.totalMemoCount.toString() + " " +
                                stringResource(R.string.stats_memo_count),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                        )
                        HistoryHeatmap(
                            months = historyMonths,
                            description = stringResource(R.string.stats_all_records),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .padding(top = 18.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MonthYearPickerDialog(
    selectedMonth: YearMonth,
    currentMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GnomeDesign.colors
    val locale = Locale.getDefault()
    var displayedYear by rememberSaveable(selectedMonth) { mutableStateOf(selectedMonth.year) }
    var choosingYear by rememberSaveable { mutableStateOf(false) }
    var decadeStart by rememberSaveable(displayedYear) {
        mutableStateOf((displayedYear / 10) * 10)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.cardBackground,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (choosingYear) {
                                decadeStart -= 10
                            } else {
                                displayedYear -= 1
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.stats_previous_year),
                            tint = colors.textSecondary,
                        )
                    }
                    Text(
                        text = if (choosingYear) {
                            "$decadeStart–${decadeStart + 9}"
                        } else {
                            stringResource(R.string.stats_year_number, displayedYear)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !choosingYear) {
                                decadeStart = (displayedYear / 10) * 10
                                choosingYear = true
                            },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (choosingYear) colors.accent else colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    val canMoveForward = if (choosingYear) {
                        decadeStart + 10 <= currentMonth.year
                    } else {
                        displayedYear < currentMonth.year
                    }
                    IconButton(
                        onClick = {
                            if (choosingYear) {
                                decadeStart += 10
                            } else {
                                displayedYear += 1
                            }
                        },
                        enabled = canMoveForward,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.stats_next_year),
                            tint = if (canMoveForward) colors.textSecondary else colors.divider,
                        )
                    }
                }

                if (choosingYear) {
                    (decadeStart..decadeStart + 9).chunked(4).forEach { years ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            years.forEach { year ->
                                PickerCell(
                                    text = year.toString(),
                                    selected = year == displayedYear,
                                    enabled = year <= currentMonth.year,
                                    onClick = {
                                        displayedYear = year
                                        choosingYear = false
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - years.size) {
                                Box(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    (1..12).chunked(4).forEach { months ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            months.forEach { monthNumber ->
                                val candidate = YearMonth.of(displayedYear, monthNumber)
                                PickerCell(
                                    text = Month.of(monthNumber)
                                        .getDisplayName(TextStyle.FULL, locale),
                                    selected = candidate == selectedMonth,
                                    enabled = candidate <= currentMonth,
                                    onClick = { onMonthSelected(candidate) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PickerCell(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GnomeDesign.colors
    Box(
        modifier = modifier
            .height(56.dp)
            .background(
                color = if (selected) colors.accent else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                selected -> Color.White
                enabled -> colors.textPrimary
                else -> colors.divider
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}