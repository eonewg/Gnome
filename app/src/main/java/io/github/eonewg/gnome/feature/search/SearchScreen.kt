package io.github.eonewg.gnome.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.component.MemoCardActions
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.page.memos.MemosList
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.net.URLEncoder
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onIncludeArchivedChange: (Boolean) -> Unit,
    navController: NavHostController,
    actions: MemoCardActions,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val colors = GnomeDesign.colors
    var searchText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.large,
                        color = colors.subtleSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.textSecondary,
                            )
                            BasicTextField(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                value = searchText,
                                onValueChange = {
                                    searchText = it
                                    onQueryChange(it.text)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                                cursorBrush = SolidColor(colors.accent),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (searchText.text.isEmpty()) {
                                            Text(
                                                text = R.string.search.string,
                                                color = colors.textSecondary,
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) }
                    ) {
                        Text(
                            text = R.string.cancel.string,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                },
            )
        },

        content = { innerPadding ->
            Column(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = { onIncludeArchivedChange(!uiState.includeArchived) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = if (uiState.includeArchived) {
                            R.string.search_include_archived_on.string
                        } else {
                            R.string.search_include_archived_off.string
                        },
                        color = if (uiState.includeArchived) colors.accent else colors.textSecondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                when {
                    !uiState.hasSearched -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = R.string.search_hint.string,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    uiState.results.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = R.string.no_results.string,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    else -> {
                        MemosList(
                            memos = uiState.results,
                            contentPadding = innerPadding,
                            loadOnStart = false,
                            onTagClick = { tag ->
                                navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            isRemoteAccount = uiState.isRemoteAccount,
                            host = uiState.host,
                            defaultVisibility = uiState.defaultVisibility,
                            actions = actions,
                        )
                    }
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
}