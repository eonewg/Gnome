package io.github.eonewg.gnome.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.eonewg.gnome.data.repository.SyncingRepository
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.widget.WidgetUpdateScheduler

/**
 * Executes one full SyncEngine reconcile for an account. Persisted by
 * WorkManager, so pending outbox work survives process death and retries with
 * backoff on transient network failures.
 *
 * Process recovery: the account is rebuilt purely from persisted state — the
 * accountKey input, DataStore account config, the encrypted token store and
 * Room. Nothing from a previous Activity session or an in-memory repository
 * is required. The active account reuses its live repository (so its mutex
 * and status updates apply); other accounts get a transient repository that
 * is closed after the run. Local-only and unknown accounts are skipped.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val accountKey = inputData.getString(KEY_ACCOUNT_KEY)
            ?: return Result.failure()

        val accountService = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        ).accountService()

        val handle = try {
            accountService.getSyncingRepository(accountKey) ?: return Result.success()
        } catch (e: Throwable) {
            return if (SyncRetryPolicy.isRetryable(e)) {
                Result.retry()
            } else {
                Result.failure(errorData(e.message))
            }
        }

        return try {
            executeSync(handle.repository)
        } finally {
            if (handle.ownsLifecycle) {
                handle.repository.close()
            }
        }
    }

    private suspend fun executeSync(repository: SyncingRepository): Result {
        return when (val result = repository.sync()) {
            is ApiResponse.Success -> {
                try {
                    WidgetUpdateScheduler.updateAllWidgets(applicationContext)
                } catch (_: Throwable) {
                    // Widget refresh is best-effort; never fail a successful sync over it.
                }
                Result.success()
            }
            is ApiResponse.Failure.Error -> {
                val code = result.rawStatusCode()
                if (code != null && SyncRetryPolicy.isRetryableStatusCode(code)) {
                    Result.retry()
                } else {
                    Result.failure(errorData(code?.toString() ?: "unknown"))
                }
            }
            is ApiResponse.Failure.Exception -> {
                val throwable = result.throwable
                if (SyncRetryPolicy.isRetryable(throwable)) Result.retry()
                else Result.failure(errorData(throwable.message))
            }
        }
    }

    private fun errorData(message: String?) = workDataOf(KEY_ERROR to (message ?: "unknown"))

    companion object {
        const val KEY_ACCOUNT_KEY = "accountKey"
        const val KEY_ERROR = "error"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun accountService(): AccountService
}
