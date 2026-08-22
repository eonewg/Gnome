package io.github.eonewg.gnome.sync

import io.github.eonewg.gnome.data.constant.GnomeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * WorkManager retry classification: transient conditions retry with backoff;
 * anything the app cannot fix by trying again (auth, validation, other 4xx)
 * fails fast so pending outbox rows stay parked instead of burning retries.
 */
class SyncRetryPolicyTest {

    @Test
    fun `network loss is retryable`() {
        assertTrue(SyncRetryPolicy.isRetryable(IOException("connection reset")))
    }

    @Test
    fun `auth and domain exceptions are terminal`() {
        assertFalse(SyncRetryPolicy.isRetryable(GnomeException.accessTokenInvalid))
        assertFalse(SyncRetryPolicy.isRetryable(IllegalStateException("bad state")))
    }

    @Test
    fun `timeout rate limit and server errors are retryable`() {
        assertTrue(SyncRetryPolicy.isRetryableStatusCode(408))
        assertTrue(SyncRetryPolicy.isRetryableStatusCode(429))
        assertTrue(SyncRetryPolicy.isRetryableStatusCode(500))
        assertTrue(SyncRetryPolicy.isRetryableStatusCode(503))
        assertTrue(SyncRetryPolicy.isRetryableStatusCode(507))
    }

    @Test
    fun `client errors are terminal`() {
        assertFalse(SyncRetryPolicy.isRetryableStatusCode(400))
        assertFalse(SyncRetryPolicy.isRetryableStatusCode(401))
        assertFalse(SyncRetryPolicy.isRetryableStatusCode(403))
        assertFalse(SyncRetryPolicy.isRetryableStatusCode(404))
    }
}
