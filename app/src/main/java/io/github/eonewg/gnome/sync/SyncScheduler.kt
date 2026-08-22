package io.github.eonewg.gnome.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
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
    fun schedule(accountKey: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
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
