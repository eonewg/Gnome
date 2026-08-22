package me.mudkip.moememos.ui.page.stats

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import me.mudkip.moememos.viewmodel.LocalMemos
import java.time.Month
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class StatsPeriod {
    MONTH,
    YEAR,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPage(navController: NavHostController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = MoeMemosDesign.colors
    val memos = LocalMemos.current.memos
    val zoneId = remember { ZoneId.systemDefault() }
    val currentMonth = remember(zoneId) { YearMonth.now(zoneId) }
    val snapshot = remember(memos, zoneId) { calculateMemoStats(memos, zoneId) }
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            Surface(color = colors.appBackground) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(72.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = colors.textSecondary,
                        )
                    }
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        StatsPeriod.entries.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = period == item,
                                onClick = { period = item },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = StatsPeriod.entries.size,
                                ),
                                label = {
                                    Text(
                                        if (item == StatsPeriod.MONTH) {
                                            stringResource(R.string.stats_month)
                                        } else {
                                            stringResource(R.string.stats_year)
                                        }
                                    )
                                },
                            )
                        }
                    }
                    TextButton(
                        onClick = { navController.navigate(RouteName.STATS_DETAIL) },
                    ) {
                        Text(
                            text = stringResource(R.string.stats_more),
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (period) {
                StatsPeriod.MONTH -> MonthStatsList(
                    months = snapshot.monthsThrough(currentMonth),
                    onDateClick = { date ->
                        navController.navigate("${RouteName.MEMOS}/${RouteName.DATE}/$date") {
                            popUpTo(RouteName.STATS) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
                StatsPeriod.YEAR -> YearStatsList(
                    years = snapshot.yearsThrough(Year.from(currentMonth)),
                )
            }
        }
    }
}

@Composable
private fun MonthStatsList(
    months: List<MonthMemoStats>,
    onDateClick: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("yyyy-MM", locale) }

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(items = months, key = { it.month.toString() }) { month ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = month.month.format(formatter),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MoeMemosDesign.colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.stats_month_summary,
                        month.memoCount,
                        month.characterCount.compactNumber(),
                        month.activeDayCount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MoeMemosDesign.colors.textSecondary,
                )
                StatsCard(modifier = Modifier.fillMaxWidth()) {
                    MonthCalendar(
                        stats = month,
                        onDateClick = onDateClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun YearStatsList(years: List<YearMemoStats>) {
    val monthLabels = (1..12).map { month ->
        stringResource(R.string.stats_month_number, month)
    }
    val memoLabel = stringResource(R.string.stats_memo_count)
    val characterLabel = stringResource(R.string.stats_character_count)
    val activeDayLabel = stringResource(R.string.stats_active_days)

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        years.forEach { year ->
            item(key = "year-${year.year}") {
                Text(
                    text = year.year.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MoeMemosDesign.colors.textPrimary,
                )
            }
            item(key = "memos-${year.year}") {
                YearMetricCard(
                    value = year.memoCount.toString(),
                    label = memoLabel,
                    monthlyValues = year.months.map { it.memoCount },
                    color = MoeMemosDesign.colors.accent,
                    selectionLabels = monthLabels,
                    unitLabel = memoLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "characters-${year.year}") {
                YearMetricCard(
                    value = year.characterCount.compactNumber(),
                    label = characterLabel,
                    monthlyValues = year.months.map { it.characterCount },
                    color = Color(0xFF668BF2),
                    selectionLabels = monthLabels,
                    unitLabel = characterLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "days-${year.year}") {
                YearMetricCard(
                    value = year.activeDayCount.toString(),
                    label = activeDayLabel,
                    monthlyValues = year.months.map { it.activeDayCount },
                    color = Color(0xFFE47B7B),
                    selectionLabels = monthLabels,
                    unitLabel = activeDayLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailPage(navController: NavHostController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = MoeMemosDesign.colors
    val memos = LocalMemos.current.memos
    val zoneId = remember { ZoneId.systemDefault() }
    val currentMonth = remember(zoneId) { YearMonth.now(zoneId) }
    val snapshot = remember(memos, zoneId) { calculateMemoStats(memos, zoneId) }
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
private fun MonthYearPickerDialog(
    selectedMonth: YearMonth,
    currentMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MoeMemosDesign.colors
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
private fun PickerCell(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MoeMemosDesign.colors
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
