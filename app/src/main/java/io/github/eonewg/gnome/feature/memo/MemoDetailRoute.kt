package io.github.eonewg.gnome.feature.memo

import androidx.compose.runtime.Composable
import io.github.eonewg.gnome.nav.EditorKey
import io.github.eonewg.gnome.nav.GnomeNavigator
import io.github.eonewg.gnome.ui.page.memos.MemoDetailPage

/**
 * Host wiring for the memo detail feature: the page stays navigation-free and
 * this route turns its callbacks into graph navigation.
 */
@Composable
fun MemoDetailRoute(
    memoIdentifier: String,
    navigator: GnomeNavigator,
) {
    MemoDetailPage(
        memoIdentifier = memoIdentifier,
        onBack = { navigator.goBack() },
        onEditMemo = { memoId ->
            navigator.navigate(EditorKey(memoId))
        },
    )
}