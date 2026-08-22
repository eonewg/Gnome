package io.github.eonewg.gnome.feature.memo

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.page.memos.MemoDetailPage

/**
 * Host wiring for the memo detail feature: the page stays navigation-free and
 * this route turns its callbacks into graph navigation.
 */
@Composable
fun MemoDetailRoute(
    memoIdentifier: String,
    navController: NavHostController,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    MemoDetailPage(
        memoIdentifier = memoIdentifier,
        onBack = { navController.popBackStackIfLifecycleIsResumed(lifecycleOwner) },
        onEditMemo = { memoId ->
            navController.navigate("${RouteName.EDIT}?memoId=$memoId")
        },
    )
}