package io.github.eonewg.gnome.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.eonewg.gnome.data.service.AccountRefreshListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the widgets after account switch/add/remove. The memo tables
 * are keyed by account, so those operations do not invalidate Room rows;
 * without this the widget would keep rendering the previous account's
 * memos until its next periodic refresh. On logout the rows are purged
 * before this fires, so the rerender re-reads only the new account's data.
 */
@Singleton
class WidgetAccountRefreshListener @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccountRefreshListener {
    override fun onAccountDataChanged() {
        CoroutineScope(Dispatchers.IO).launch {
            WidgetUpdateScheduler.updateAllWidgets(context.applicationContext)
        }
    }
}