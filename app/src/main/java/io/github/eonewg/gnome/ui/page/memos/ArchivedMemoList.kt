package io.github.eonewg.gnome.ui.page.memos

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eonewg.gnome.ui.util.edgeToEdgeContentPadding
import io.github.eonewg.gnome.ui.component.ArchivedMemoCard
import io.github.eonewg.gnome.viewmodel.ArchivedMemoListViewModel

@Composable
fun ArchivedMemoList(
    viewModel: ArchivedMemoListViewModel = hiltViewModel(),
    contentPadding: PaddingValues
) {
    val memos by viewModel.memos.collectAsStateWithLifecycle()
    val listContentPadding = edgeToEdgeContentPadding(contentPadding)

    LazyColumn(
        modifier = Modifier.consumeWindowInsets(contentPadding),
        contentPadding = listContentPadding
    ) {
        items(memos, key = { it.id }) { memo ->
            ArchivedMemoCard(
                memo = memo,
                onRestore = viewModel::restoreMemo,
                onDelete = viewModel::deleteMemo,
            )
        }
    }
}