package io.github.eonewg.gnome.feature.timeline

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.memos.MemosList
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateMemoScreen(
    date: LocalDate,
    drawerState: DrawerState? = null,
    uiState: DateMemoUiState,
    onTagClick: (String) -> Unit,
    actions: MemoCardActions,
) {
    val scope = rememberCoroutineScope()
    val colors = GnomeDesign.colors
    val title = remember(date, Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.getDefault())
            .format(date)
    }

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, color = colors.textPrimary) },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = R.string.menu.string,
                                tint = colors.textSecondary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appBackground,
                    scrolledContainerColor = colors.appBackground,
                ),
            )
        },
    ) { innerPadding ->
        MemosList(
            memos = uiState.memos,
            contentPadding = innerPadding,
            date = date,
            onTagClick = onTagClick,
            isRemoteAccount = uiState.isRemoteAccount,
            host = uiState.host,
            defaultVisibility = uiState.defaultVisibility,
            actions = actions,
        )
    }
}