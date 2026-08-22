package io.github.eonewg.gnome.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gate between local writes and background synchronization. Every mutation path
 * persists its intent into the Room outbox first, then calls [schedule]; whether
 * the push happens immediately or after a process death is then WorkManager's
 * problem, not a coroutine's.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * APPEND_OR_REPLACE is what makes the write→schedule race safe: a run that
     * is already executing may have read the outbox before this write, so its
     * chain always gets one more run appended. The only case that can be
     * skipped is a chain still sitting in ENQUEUED — that worker has not read
     * anything yet, and this write was committed to Room before we looked, so
     * the queued run is guaranteed to observe it. Skipping keeps a burst of
     * writes from queueing a full reconcile per edit.
     */
    suspend fun schedule(accountKey: String) {
        val workManager = WorkManager.getInstance(context)
        val alreadyQueued = try {
            workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(accountKey))
                .first()
                .any { it.state == WorkInfo.State.ENQUEUED }
        } catch (_: Throwable) {
            false
        }

        if (alreadyQueued) return

        workManager.enqueueUniqueWork(
            uniqueWorkName(accountKey),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            buildRequest(accountKey)
        )
    }

    companion object {
        fun uniqueWorkName(accountKey: String) = "gnome-sync:$accountKey"

        private fun buildRequest(accountKey: String) =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(SyncWorker.KEY_ACCOUNT_KEY to accountKey))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
                .build()

        private const val BACKOFF_DELAY_SECONDS = 30L
    }
}
