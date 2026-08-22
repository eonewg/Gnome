package io.github.eonewg.gnome.ui.component

import android.text.TextUtils
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eonewg.gnome.feature.explore.ExploreMemo

@Composable
fun ExploreMemoCard(
    memo: ExploreMemo
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 15.dp, vertical = 10.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 15.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateUtils.getRelativeTimeSpanString(memo.date.toEpochMilli(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )

                if (!TextUtils.isEmpty(memo.creatorName)) {
                    Text(
                        "@${memo.creatorName}",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            MemoContent(memo, previewMode = false)
        }
    }
}