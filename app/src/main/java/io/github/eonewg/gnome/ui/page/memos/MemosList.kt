package io.github.eonewg.gnome.ui.page.memos

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.data.model.MemoEditGesture
import io.github.eonewg.gnome.data.model.Settings
import io.github.eonewg.gnome.ext.settingsDataStore
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.feature.timeline.MemoSortOrder
import io.github.eonewg.gnome.feature.timeline.memoMatchesDate
import io.github.eonewg.gnome.feature.timeline.orderMemosForTimeline
import io.github.eonewg.gnome.ui.component.MemosCard
import io.github.eonewg.gnome.ui.util.edgeToEdgeContentPadding
import java.time.LocalDate
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosList(
    memos: List<Memo>,
    contentPadding: PaddingValues,
    lazyListState: LazyListState = rememberLazyListState(),
    date: LocalDate? = null,
    additionalBottomPadding: Dp = 16.dp,
    onRefresh: (suspend () -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    sortOrder: MemoSortOrder = MemoSortOrder.CreatedNewest,
    selectionMode: Boolean = false,
    selectedMemoIds: Set<String> = emptySet(),
    onSelectionToggle: ((Memo) -> Unit)? = null,
    loadOnStart: Boolean = true,
    isRemoteAccount: Boolean = false,
    host: String? = null,
    defaultVisibility: MemoVisibility? = null,
    actions: MemoCardActions = MemoCardActions(),
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val settings by context.settingsDataStore.data.collectAsState(initial = Settings())
    val editGesture = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
        ?.editGesture
    val refreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val localOffset = remember { OffsetDateTime.now().offset }
    val filteredMemos by remember(memos, date, sortOrder, localOffset) {
        derivedStateOf {
            var fullList = orderMemosForTimeline(memos, sortOrder)

            date?.let { selectedDate ->
                fullList = fullList.filter { memo ->
                    memoMatchesDate(memo, selectedDate, localOffset)
                }
            }

            fullList
        }
    }
    var listTopId: String? by rememberSaveable {
        mutableStateOf(null)
    }
    val edgeToEdgePadding = edgeToEdgeContentPadding(
        contentPadding,
        additionalBottomPadding
    )
    val listContentPadding = PaddingValues(
        start = edgeToEdgePadding.calculateStartPadding(layoutDirection) + 14.dp,
        top = edgeToEdgePadding.calculateTopPadding() + 8.dp,
        end = edgeToEdgePadding.calculateEndPadding(layoutDirection) + 14.dp,
        bottom = edgeToEdgePadding.calculateBottomPadding() + 12.dp,
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                onRefresh?.invoke()
                isRefreshing = false
            }
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = lazyListState,
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = filteredMemos,
                key = { it.id },
                contentType = { "memo" }
            ) { memo ->
                MemosCard(
                    memo = memo,
                    editGesture = if (selectionMode) MemoEditGesture.NONE else editGesture ?: MemoEditGesture.NONE,
                    previewMode = true,
                    showSyncStatus = isRemoteAccount,
                    isRemoteAccount = isRemoteAccount,
                    host = host,
                    defaultVisibility = defaultVisibility,
                    actions = actions,
                    onTagClick = if (selectionMode) null else onTagClick,
                    selectionMode = selectionMode,
                    selected = memo.id in selectedMemoIds,
                    onSelectionToggle = onSelectionToggle,
                )
            }
        }
    }

    if (loadOnStart) {
        LaunchedEffect(Unit) {
            onRefresh?.invoke()
        }
    }

    LaunchedEffect(filteredMemos.firstOrNull()?.id) {
        if (listTopId != null && filteredMemos.isNotEmpty() && listTopId != filteredMemos.first().id) {
            lazyListState.scrollToItem(0)
        }

        listTopId = filteredMemos.firstOrNull()?.id
    }
}
