package me.mudkip.moememos.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.theme.MoeMemosDesign

private const val SYNCED_STATE_DURATION_MS = 2000L

@Composable
fun SyncStatusBadge(
    syncing: Boolean,
    unsyncedCount: Int,
    errorMessage: String? = null,
    onSync: () -> Unit
) {
    val tint = MoeMemosDesign.colors.textSecondary
    var showSynced by remember { mutableStateOf(false) }
    var wasSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(syncing, unsyncedCount, errorMessage) {
        if (syncing || errorMessage != null || unsyncedCount > 0) {
            showSynced = false
        } else if (wasSyncing) {
            showSynced = true
            delay(SYNCED_STATE_DURATION_MS)
            showSynced = false
        }
        wasSyncing = syncing
    }

    IconButton(onClick = onSync, enabled = !syncing) {
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
        } else if (showSynced) {
            Icon(
                Icons.Filled.CloudDone,
                contentDescription = R.string.sync_status_synced.string,
                tint = MoeMemosDesign.colors.accent,
            )
        } else if (unsyncedCount > 0) {
            BadgedBox(
                badge = {
                    Badge {
                        Text(unsyncedCount.toString())
                    }
                }
            ) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = R.string.sync_status_unsynced.string,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Icon(
                Icons.Filled.Sync,
                contentDescription = R.string.sync_status_sync_now.string,
                tint = tint,
            )
        }
    }
}
