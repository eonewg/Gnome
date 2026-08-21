package me.mudkip.moememos.ui.page.memos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.theme.MoeMemosDesign
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPage(navController: NavHostController) {
    var searchText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val colors = MoeMemosDesign.colors

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
                                onValueChange = { searchText = it },
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
                        onClick = {
                            navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
                        }
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
            MemosList(
                contentPadding = innerPadding,
                searchString = searchText.text,
                onTagClick = { tag ->
                    navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    )

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
}
