package io.github.eonewg.gnome.widget

import android.content.Context
import androidx.room.InvalidationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.eonewg.gnome.data.local.GnomeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single widget refresh trigger for the write paths: every Room write
 * to the memo or resource tables (local-first edits, SyncEngine pulls,
 * outbox drains) invalidates them and coalesces into one widget update.
 *
 * Account switches do not touch those tables and are covered separately by
 * [AccountRefreshListener] ([WidgetAccountRefreshListener]); [GnomeApp]
 * owns the lifecycle of this watcher.
 */
@Singleton
class MemoTableChangeWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GnomeDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingRefresh: Job? = null

    /** Coalesce window: bursts of writes (e.g. an outbox drain) yield one refresh. */
    private val observer = object : InvalidationTracker.Observer(MEMO_TABLES) {
        override fun onInvalidated(tables: Set<String>) {
            scheduleRefresh()
        }
    }

    fun start() {
        database.invalidationTracker.addObserver(observer)
    }

    private fun scheduleRefresh() {
        if (pendingRefresh?.isActive == true) {
            // Already coalescing this burst; the pending run covers this write.
            return
        }
        pendingRefresh = scope.launch {
            delay(REFRESH_COALESCE_MS)
            runCatching { WidgetUpdateScheduler.updateAllWidgets(context.applicationContext) }
        }
    }

    private companion object {
        const val REFRESH_COALESCE_MS = 2_000L
        val MEMO_TABLES = arrayOf("memos", "resources")
    }
}