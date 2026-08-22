package io.github.eonewg.gnome.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.StatusCode
import com.skydoves.sandwich.retrofit.statusCode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.eonewg.gnome.data.constant.GnomeException
import io.github.eonewg.gnome.data.repository.SyncingRepository
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.widget.WidgetUpdateScheduler
import java.io.IOException

/**
 * Executes one full SyncEngine reconcile for an account. Persisted by
 * WorkManager, so pending outbox work survives process death and retries with
 * backoff on transient network failures.
 *
 * Only the currently active account is synchronized (matching the app's
 * single-active-repository model); outbox rows of other accounts drain when
 * their account becomes active again.
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

        val repository = try {
            accountService.getRepository()
        } catch (e: Throwable) {
            return if (isRetryable(e)) Result.retry() else Result.failure(errorData(e.message))
        }

        if (repository !is SyncingRepository || repository.accountKeyValue != accountKey) {
            return Result.success()
        }

        return when (val result = repository.sync()) {
            is ApiResponse.Success -> {
                try {
                    WidgetUpdateScheduler.updateAllWidgets(applicationContext)
                } catch (_: Throwable) {
                    // Widget refresh is best-effort; never fail a successful sync over it.
                }
                Result.success()
            }
            is ApiResponse.Failure.Error ->
                if (isRetryableStatus(result.statusCode)) {
                    Result.retry()
                } else {
                    Result.failure(errorData(result.statusCode.toString()))
                }
            is ApiResponse.Failure.Exception -> {
                val throwable = result.throwable
                if (isRetryable(throwable)) Result.retry()
                else Result.failure(errorData(throwable.message))
            }
        }
    }

    private fun errorData(message: String?) = workDataOf(KEY_ERROR to (message ?: "unknown"))

    private fun isRetryable(throwable: Throwable): Boolean {
        if (throwable is GnomeException) return false
        return throwable is IOException
    }

    private fun isRetryableStatus(statusCode: StatusCode): Boolean {
        return statusCode == StatusCode.RequestTimeout ||
            statusCode == StatusCode.TooManyRequests ||
            statusCode.code in 500..599
    }

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
