package io.github.eonewg.gnome.sync

import com.skydoves.sandwich.ApiResponse
import io.github.eonewg.gnome.data.constant.GnomeException
import java.io.IOException

/**
 * Classifies sync failures for WorkManager retry decisions.
 *
 * Retriable: transient conditions the next attempt may survive — network loss
 * (IOException) and 408 / 429 / 5xx responses.
 *
 * Terminal: anything the app cannot fix by trying again — auth failures
 * (GnomeException incl. accessTokenInvalid, 401 / 403 / other 4xx). Terminal
 * failures stop the worker; pending outbox rows stay parked with their
 * lastError until the user acts (re-login, edit) and a new run is scheduled.
 *
 * Uses raw HTTP codes rather than sandwich's [com.skydoves.sandwich.StatusCode]
 * because that enum throws for unmapped codes.
 */
internal object SyncRetryPolicy {

    fun isRetryable(throwable: Throwable): Boolean {
        if (throwable is GnomeException) return false
        return throwable is IOException
    }

    fun isRetryableStatusCode(code: Int): Boolean {
        return code == 408 || code == 429 || code in 500..599
    }
}

/**
 * Raw HTTP status of a sandwich error. `payload` is `Any?`; with the retrofit
 * call adapter it always holds a `Response`, whose plain code never throws —
 * unlike sandwich's [com.skydoves.sandwich.StatusCode] mapping.
 */
internal fun ApiResponse.Failure.Error.rawStatusCode(): Int? =
    (payload as? retrofit2.Response<*>)?.code()
