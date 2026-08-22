package io.github.eonewg.gnome.ui.page.memos

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.eonewg.gnome.ui.util.edgeToEdgeContentPadding
import io.github.eonewg.gnome.ui.component.ArchivedMemoCard
import io.github.eonewg.gnome.viewmodel.ArchivedMemoListViewModel

@Composable
fun ArchivedMemoList(
    viewModel: ArchivedMemoListViewModel = hiltViewModel(),
    contentPadding: PaddingValues
) {
    val listContentPadding = edgeToEdgeContentPadding(contentPadding)

    LazyColumn(
        modifier = Modifier.consumeWindowInsets(contentPadding),
        contentPadding = listContentPadding
    ) {
        items(viewModel.memos, key = { it.identifier }) { memo ->
            ArchivedMemoCard(
                memo = memo,
                onRestore = viewModel::restoreMemo,
                onDelete = viewModel::deleteMemo,
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMemos()
    }
}