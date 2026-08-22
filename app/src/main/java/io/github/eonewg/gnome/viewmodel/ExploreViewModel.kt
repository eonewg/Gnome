package io.github.eonewg.gnome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import io.github.eonewg.gnome.data.datasource.EXPLORE_PAGE_SIZE
import io.github.eonewg.gnome.data.datasource.ExplorePagingSource
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.Memo
import io.github.eonewg.gnome.data.service.AccountService
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel @Inject constructor(
    accountService: AccountService
) : ViewModel() {
    val exploreMemos = accountService.currentAccount
        .flatMapLatest { account ->
            if (account == null || account is Account.Local) {
                return@flatMapLatest flowOf(PagingData.empty<Memo>())
            }

            val remoteRepository = accountService.getRemoteRepository()
                ?: return@flatMapLatest flowOf(PagingData.empty<Memo>())

            Pager(PagingConfig(pageSize = EXPLORE_PAGE_SIZE)) {
                ExplorePagingSource(remoteRepository)
            }.flow
        }
        .cachedIn(viewModelScope)
}
