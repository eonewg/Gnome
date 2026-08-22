package io.github.eonewg.gnome.ui.page.memos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.icon
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ext.titleResource
import io.github.eonewg.gnome.feature.memo.MemoDetailViewModel
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.component.MemoContent
import io.github.eonewg.gnome.ui.component.MemosCardActionButton
import io.github.eonewg.gnome.ui.component.toMemoTimestamp
import io.github.eonewg.gnome.ui.component.toRepresentable
import io.github.eonewg.gnome.ui.theme.GnomeDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailPage(
    memoIdentifier: String,
    onBack: () -> Unit,
    onEditMemo: (memoId: String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val memoDetailViewModel: MemoDetailViewModel = hiltViewModel()
    val uiState by memoDetailViewModel.uiState.collectAsStateWithLifecycle()
    val memo = uiState.memo
    val scope = rememberCoroutineScope()
    val colors = GnomeDesign.colors
    var hadMemo by rememberSaveable(memoIdentifier) { mutableStateOf(false) }

    val memoCardActions = MemoCardActions(
        onEdit = { id ->
            onEditMemo(id)
        },
        onTogglePin = { _, pinned ->
            scope.launch { memoDetailViewModel.updateMemoPinned(pinned) }
        },
        onArchive = {
            scope.launch { memoDetailViewModel.archiveMemo() }
        },
        onDelete = {
            scope.launch { memoDetailViewModel.deleteMemo() }
        },
        onCacheResource = { resourceId, uri ->
            scope.launch { memoDetailViewModel.cacheResourceFile(resourceId, uri) }
        },
        onDownloadAndCache = { resource ->
            memoDetailViewModel.downloadAndCacheResource(resource)
        },
    )

    LaunchedEffect(memo?.id) {
        when {
            memo != null -> hadMemo = true
            hadMemo -> onBack()
        }
    }

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appBackground,
                    scrolledContainerColor = colors.appBackground,
                ),
                title = {
                    Text(
                        text = R.string.memo.string,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = R.string.back.string,
                            tint = colors.textSecondary,
                        )
                    }
                },
                actions = {
                    memo?.let {
                        MemosCardActionButton(
                            memo = it,
                            isRemoteAccount = uiState.isRemoteAccount,
                            host = uiState.host,
                            actions = memoCardActions,
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (memo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = R.string.memo_not_found.string)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.cardBackground,
                contentColor = colors.textPrimary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(start = 18.dp, top = 16.dp, end = 18.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            memo.date.toMemoTimestamp(),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        if (uiState.isRemoteAccount && memo.syncState != io.github.eonewg.gnome.core.model.SyncState.SYNCED) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = R.string.memo_sync_pending.string,
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .size(18.dp),
                            )
                        }
                        if (uiState.defaultVisibility != memo.visibility) {
                            Icon(
                                imageVector = memo.visibility.icon,
                                contentDescription = stringResource(memo.visibility.titleResource),
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .size(18.dp),
                                tint = colors.textSecondary,
                            )
                        }
                    }

                    MemoContent(
                        memo = memo.toRepresentable(),
                        selectable = true,
                        imageBaseUrl = uiState.host,
                        actions = memoCardActions,
                        checkboxChange = { checked, startOffset, endOffset ->
                            scope.launch {
                                var text = memo.content.substring(startOffset, endOffset)
                                text = if (checked) {
                                    text.replace("[ ]", "[x]")
                                } else {
                                    text.replace("[x]", "[ ]")
                                }
                                memoDetailViewModel.updateMemoContent(
                                    memo.content.replaceRange(startOffset, endOffset, text)
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
        }
    }
}