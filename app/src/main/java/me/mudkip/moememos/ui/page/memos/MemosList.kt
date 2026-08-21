package me.mudkip.moememos.ui.page.memos

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoEditGesture
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.ext.settingsDataStore
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.MemosCard
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.util.edgeToEdgeContentPadding
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.ManualSyncResult
import timber.log.Timber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class MemoSortOrder {
    CreatedNewest,
    CreatedOldest,
    UpdatedNewest,
    UpdatedOldest,
}

internal fun orderMemosForTimeline(
    memos: List<MemoEntity>,
    sortOrder: MemoSortOrder,
): List<MemoEntity> {
    val comparator = when (sortOrder) {
        MemoSortOrder.CreatedNewest -> compareByDescending<MemoEntity> { it.date }
        MemoSortOrder.CreatedOldest -> compareBy<MemoEntity> { it.date }
        MemoSortOrder.UpdatedNewest -> compareByDescending<MemoEntity> { it.lastModified }
        MemoSortOrder.UpdatedOldest -> compareBy<MemoEntity> { it.lastModified }
    }
    val pinned = memos.filter { it.pinned }.sortedWith(comparator)
    val nonPinned = memos.filter { !it.pinned }.sortedWith(comparator)
    return pinned + nonPinned
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosList(
    contentPadding: PaddingValues,
    lazyListState: LazyListState = rememberLazyListState(),
    tag: String? = null,
    searchString: String? = null,
    date: LocalDate? = null,
    additionalBottomPadding: Dp = 16.dp,
    onRefresh: (suspend () -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    sortOrder: MemoSortOrder = MemoSortOrder.CreatedNewest,
    selectionMode: Boolean = false,
    selectedMemoIds: Set<String> = emptySet(),
    onSelectionToggle: ((MemoEntity) -> Unit)? = null,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val navController = LocalRootNavController.current
    val viewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val settings by context.settingsDataStore.data.collectAsState(initial = Settings())
    val editGesture = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
        ?.editGesture
    val refreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var syncAlert by remember { mutableStateOf<PullRefreshSyncAlert?>(null) }
    val localOffset = remember { OffsetDateTime.now().offset }
    val filteredMemos by remember(tag, searchString, date, sortOrder, localOffset) {
        derivedStateOf {
            var fullList = orderMemosForTimeline(viewModel.memos, sortOrder)

            tag?.let { tag ->
                fullList = fullList.filter { memo ->
                    memo.content.contains("#$tag") ||
                        memo.content.contains("#$tag/")
                }
            }

            searchString?.let { searchString ->
                if (searchString.isNotEmpty()) {
                    fullList = fullList.filter { memo ->
                        memo.content.contains(searchString, true)
                    }
                }
            }

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
                if (onRefresh != null) {
                    onRefresh()
                } else {
                    when (val result = viewModel.refreshMemos()) {
                        ManualSyncResult.Completed -> Unit
                        is ManualSyncResult.Blocked -> {
                            syncAlert = PullRefreshSyncAlert.Blocked(result.message)
                        }
                        is ManualSyncResult.RequiresConfirmation -> {
                            syncAlert = PullRefreshSyncAlert.RequiresConfirmation(result.version, result.message)
                        }
                        is ManualSyncResult.Failed -> {
                            syncAlert = PullRefreshSyncAlert.Failed(result.message)
                        }
                    }
                }
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
                key = { it.identifier },
                contentType = { "memo" }
            ) { memo ->
                MemosCard(
                    memo = memo,
                    onClick = { selectedMemo ->
                        navController.navigate(
                            "${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(selectedMemo.identifier)}"
                        )
                    },
                    editGesture = if (selectionMode) MemoEditGesture.NONE else editGesture ?: MemoEditGesture.NONE,
                    previewMode = true,
                    showSyncStatus = currentAccount !is Account.Local,
                    onTagClick = if (selectionMode) null else onTagClick,
                    selectionMode = selectionMode,
                    selected = memo.identifier in selectedMemoIds,
                    onSelectionToggle = onSelectionToggle,
                )
            }
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            Timber.d(it)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMemos()
    }

    LaunchedEffect(filteredMemos.firstOrNull()?.identifier) {
        if (listTopId != null && filteredMemos.isNotEmpty() && listTopId != filteredMemos.first().identifier) {
            lazyListState.scrollToItem(0)
        }

        listTopId = filteredMemos.firstOrNull()?.identifier
    }

    when (val alert = syncAlert) {
        null -> Unit
        is PullRefreshSyncAlert.Blocked -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.close.string)
                    }
                }
            )
        }
        is PullRefreshSyncAlert.RequiresConfirmation -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            syncAlert = null
                            scope.launch {
                                when (val result = viewModel.refreshMemos(alert.version)) {
                                    ManualSyncResult.Completed -> Unit
                                    is ManualSyncResult.Blocked -> {
                                        syncAlert = PullRefreshSyncAlert.Blocked(result.message)
                                    }
                                    is ManualSyncResult.RequiresConfirmation -> {
                                        syncAlert = PullRefreshSyncAlert.RequiresConfirmation(result.version, result.message)
                                    }
                                    is ManualSyncResult.Failed -> {
                                        syncAlert = PullRefreshSyncAlert.Failed(result.message)
                                    }
                                }
                            }
                        }
                    ) {
                        Text(R.string.still_sync.string)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.cancel.string)
                    }
                }
            )
        }
        is PullRefreshSyncAlert.Failed -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.sync_failed.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.close.string)
                    }
                }
            )
        }
    }
}

internal fun memoMatchesDate(
    memo: MemoEntity,
    date: LocalDate,
    offset: ZoneOffset,
): Boolean = memo.date.atOffset(offset).toLocalDate() == date

private sealed class PullRefreshSyncAlert {
    data class Blocked(val message: String) : PullRefreshSyncAlert()
    data class RequiresConfirmation(val version: String, val message: String) : PullRefreshSyncAlert()
    data class Failed(val message: String) : PullRefreshSyncAlert()
}
