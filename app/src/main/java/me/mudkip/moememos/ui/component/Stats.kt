package me.mudkip.moememos.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Composable
fun Stats(modifier: Modifier = Modifier) {
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val days = remember(userStateViewModel.currentUser, LocalDate.now()) {
        userStateViewModel.currentUser?.let { currentUser ->
            ChronoUnit.DAYS.between(
                currentUser.startDate.atZone(OffsetDateTime.now().offset).toLocalDate(),
                LocalDate.now(),
            )
        } ?: 0
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DrawerStat(memosViewModel.memos.size.toString(), R.string.memo.string, Modifier.weight(1f))
        DrawerStat(memosViewModel.tags.size.toString(), R.string.tag.string, Modifier.weight(1f))
        DrawerStat(days.toString(), R.string.day.string, Modifier.weight(1f))
    }
}

@Composable
private fun DrawerStat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MoeMemosDesign.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
    }
}
