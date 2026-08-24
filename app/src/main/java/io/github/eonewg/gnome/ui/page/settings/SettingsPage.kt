package io.github.eonewg.gnome.ui.page.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.MemoEditGesture
import io.github.eonewg.gnome.data.model.Settings
import io.github.eonewg.gnome.data.model.displayTitle
import io.github.eonewg.gnome.ext.settingsDataStore
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.nav.AccountKey
import io.github.eonewg.gnome.nav.AddAccountKey
import io.github.eonewg.gnome.nav.GnomeNavigator
import io.github.eonewg.gnome.ui.component.MemosIcon
import io.github.eonewg.gnome.ui.page.common.drawerForegroundAlpha
import io.github.eonewg.gnome.ui.page.common.GnomeDrawerState
import io.github.eonewg.gnome.ui.security.AppLockAuthenticator
import io.github.eonewg.gnome.ui.security.AppLockSession
import io.github.eonewg.gnome.feature.account.AccountSessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    navigator: GnomeNavigator,
    drawerState: GnomeDrawerState? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val accountSessionViewModel: AccountSessionViewModel = hiltViewModel()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val accounts by accountSessionViewModel.accounts.collectAsState()
    val currentAccount by accountSessionViewModel.currentAccount.collectAsState()
    val settings by context.settingsDataStore.data.collectAsState(initial = Settings())
    val appLockSupported = remember(context, AppLockSession.foregroundGeneration) {
        AppLockAuthenticator.canAuthenticate(context)
    }
    var showEditGestureDialog by remember { mutableStateOf(false) }

    fun setAppLockEnabled(enabled: Boolean) {
        if (enabled && !appLockSupported) {
            return
        }
        if (enabled) {
            AppLockSession.lock()
        }
        scope.launch(Dispatchers.IO) {
            context.settingsDataStore.updateData { existingSettings ->
                existingSettings.copy(appLockEnabled = enabled)
            }
        }
    }
    val currentEditGesture = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
        ?.editGesture
        ?: MemoEditGesture.NONE

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = R.string.settings.string,
                        modifier = Modifier.drawerForegroundAlpha(),
                    )
                },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(
                            modifier = Modifier.drawerForegroundAlpha(),
                            onClick = { scope.launch { drawerState.open() } },
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                        }
                    } else {
                        IconButton(
                            modifier = Modifier.drawerForegroundAlpha(),
                            onClick = { navigator.goBack() },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = R.string.back.string)
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.drawerForegroundAlpha(),
            contentPadding = innerPadding,
        ) {
            item {
                Text(
                    R.string.accounts.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            accounts.forEach { account ->
                when (account) {
                    is Account.MemosV0 -> item {
                        SettingItem(
                            icon = MemosIcon,
                            text = account.info.displayTitle(),
                            subtitle = account.info.host,
                            trailingIcon = {
                                if (currentAccount?.accountKey() == account.accountKey()) {
                                    Icon(Icons.Outlined.Check,
                                        contentDescription = R.string.selected.string,
                                        modifier = Modifier.padding(start = 16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }) {
                            navigator.navigate(AccountKey(account.accountKey()))
                        }
                    }
                    is Account.MemosV1 -> item {
                        SettingItem(
                            icon = MemosIcon,
                            text = account.info.displayTitle(),
                            subtitle = account.info.host,
                            trailingIcon = {
                                if (currentAccount?.accountKey() == account.accountKey()) {
                                    Icon(Icons.Outlined.Check,
                                        contentDescription = R.string.selected.string,
                                        modifier = Modifier.padding(start = 16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }) {
                            navigator.navigate(AccountKey(account.accountKey()))
                        }
                    }
                    is Account.Local -> item {
                        SettingItem(icon = Icons.Outlined.Home, text = R.string.local_account.string, trailingIcon = {
                            if (currentAccount?.accountKey() == account.accountKey()) {
                                Icon(Icons.Outlined.Check,
                                    contentDescription = R.string.selected.string,
                                    modifier = Modifier.padding(start = 16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }) {
                            navigator.navigate(AccountKey(account.accountKey()))
                        }
                    }
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.PersonAdd, text = R.string.add_account.string) {
                    navigator.navigate(AddAccountKey)
                }
            }

            item {
                Text(
                    R.string.preferences.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            item {
                SettingItem(
                    icon = Icons.Outlined.Edit,
                    text = R.string.edit_gesture.string,
                    trailingIcon = {
                        Text(
                            text = currentEditGesture.titleResource.string,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                ) {
                    showEditGestureDialog = true
                }
            }

            item {
                Text(
                    R.string.security.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            item {
                val appLockToggleEnabled = appLockSupported || settings.appLockEnabled
                SettingItem(
                    icon = Icons.Outlined.Lock,
                    text = R.string.app_lock.string,
                    subtitle = if (appLockSupported) {
                        R.string.app_lock_summary.string
                    } else {
                        R.string.app_lock_unavailable_short.string
                    },
                    trailingIcon = {
                        Switch(
                            checked = settings.appLockEnabled,
                            onCheckedChange = null,
                            enabled = appLockToggleEnabled,
                        )
                    },
                    enabled = appLockToggleEnabled,
                ) {
                    setAppLockEnabled(!settings.appLockEnabled)
                }
            }

            item {
                Text(
                    R.string.about.string,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp, 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            item {
                SettingItem(icon = Icons.Outlined.Web, text = R.string.website.string) {
                    uriHandler.openUri("https://github.com/eonewg/Gnome")
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.Lock, text = R.string.privacy_policy.string) {
                    uriHandler.openUri("https://github.com/eonewg/Gnome/blob/custom-memos/PRIVACY.md")
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.Source, text = R.string.acknowledgements.string) {
                    uriHandler.openUri("https://github.com/eonewg/Gnome#acknowledgments")
                }
            }

            item {
                SettingItem(icon = Icons.Outlined.BugReport, text = R.string.report_an_issue.string) {
                    uriHandler.openUri("https://github.com/eonewg/Gnome/issues")
                }
            }
        }
    }

    if (showEditGestureDialog) {
        AlertDialog(
            onDismissRequest = { showEditGestureDialog = false },
            title = { Text(R.string.edit_gesture.string) },
            text = {
                LazyColumn {
                    items(MemoEditGesture.entries.size) { index ->
                        val gesture = MemoEditGesture.entries[index]
                        TextButton(
                            onClick = {
                                showEditGestureDialog = false
                                scope.launch(Dispatchers.IO) {
                                    context.settingsDataStore.updateData { existingSettings ->
                                        val userIndex = existingSettings.usersList.indexOfFirst { user ->
                                            user.accountKey == existingSettings.currentUser
                                        }
                                        if (userIndex == -1) {
                                            return@updateData existingSettings
                                        }
                                        val users = existingSettings.usersList.toMutableList()
                                        val user = users[userIndex]
                                        users[userIndex] = user.copy(
                                            settings = user.settings.copy(editGesture = gesture)
                                        )
                                        existingSettings.copy(usersList = users)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = gesture.titleResource.string,
                                color = if (gesture == currentEditGesture) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditGestureDialog = false }) {
                    Text(R.string.close.string)
                }
            }
        )
    }
}

private val MemoEditGesture.titleResource: Int
    get() = when (this) {
        MemoEditGesture.NONE -> R.string.edit_gesture_none
        MemoEditGesture.SINGLE -> R.string.edit_gesture_single
        MemoEditGesture.DOUBLE -> R.string.edit_gesture_double
        MemoEditGesture.LONG -> R.string.edit_gesture_long
    }
