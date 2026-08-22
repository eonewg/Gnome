package io.github.eonewg.gnome.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.viewmodel.LocalMemos
import io.github.eonewg.gnome.viewmodel.LocalUserState
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Composable
fun Stats(modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        DrawerStat(
            value = memosViewModel.memos.size.toString(),
            label = R.string.memo.string,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
        DrawerStat(
            value = memosViewModel.tags.size.toString(),
            label = R.string.tag.string,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
        DrawerStat(
            value = days.toString(),
            label = R.string.day.string,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DrawerStat(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GnomeDesign.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
    }
}
