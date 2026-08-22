package io.github.eonewg.gnome.feature.stats

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatsPeriod {
    MONTH,
    YEAR,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    navController: NavHostController,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = GnomeDesign.colors
    val zoneId = remember { ZoneId.systemDefault() }
    val currentMonth = remember(zoneId) { YearMonth.now(zoneId) }
    val snapshot = uiState.snapshot
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
                    color = GnomeDesign.colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.stats_month_summary,
                        month.memoCount,
                        month.characterCount.compactNumber(),
                        month.activeDayCount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = GnomeDesign.colors.textSecondary,
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
                    color = GnomeDesign.colors.textPrimary,
                )
            }
            item(key = "memos-${year.year}") {
                YearMetricCard(
                    value = year.memoCount.toString(),
                    label = memoLabel,
                    monthlyValues = year.months.map { it.memoCount },
                    color = GnomeDesign.colors.accent,
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
                    color = androidx.compose.ui.graphics.Color(0xFF668BF2),
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
                    color = androidx.compose.ui.graphics.Color(0xFFE47B7B),
                    selectionLabels = monthLabels,
                    unitLabel = activeDayLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}