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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.ext.icon
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ext.titleResource
import io.github.eonewg.gnome.ui.component.MemoContent
import io.github.eonewg.gnome.ui.component.MemosCardActionButton
import io.github.eonewg.gnome.ui.component.toMemoTimestamp
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.viewmodel.LocalMemos
import io.github.eonewg.gnome.viewmodel.LocalUserState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailPage(
    navController: NavHostController,
    memoIdentifier: String
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutDirection = LocalLayoutDirection.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = GnomeDesign.colors
    val memo = remember(memosViewModel.memos.toList(), memoIdentifier) {
        memosViewModel.memos.firstOrNull { it.identifier == memoIdentifier }
    }
    var hadMemo by rememberSaveable(memoIdentifier) { mutableStateOf(false) }

    LaunchedEffect(memo?.identifier) {
        when {
            memo != null -> hadMemo = true
            hadMemo -> navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
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
                    IconButton(onClick = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = R.string.back.string,
                            tint = colors.textSecondary,
                        )
                    }
                },
                actions = {
                    memo?.let { MemosCardActionButton(it) }
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
                        if (currentAccount !is Account.Local && memo.needsSync) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = R.string.memo_sync_pending.string,
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .size(18.dp),
                            )
                        }
                        if (userStateViewModel.currentUser?.defaultVisibility != memo.visibility) {
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
                        memo = memo,
                        selectable = true,
                        checkboxChange = { checked, startOffset, endOffset ->
                            scope.launch {
                                var text = memo.content.substring(startOffset, endOffset)
                                text = if (checked) {
                                    text.replace("[ ]", "[x]")
                                } else {
                                    text.replace("[x]", "[ ]")
                                }
                                memosViewModel.editMemo(
                                    memo.identifier,
                                    memo.content.replaceRange(startOffset, endOffset, text),
                                    memo.resources,
                                    memo.visibility
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
