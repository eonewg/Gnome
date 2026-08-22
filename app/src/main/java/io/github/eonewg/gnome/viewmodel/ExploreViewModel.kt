package io.github.eonewg.gnome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import io.github.eonewg.gnome.data.datasource.EXPLORE_PAGE_SIZE
import io.github.eonewg.gnome.data.datasource.ExplorePagingSource
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.feature.explore.ExploreMemo
import io.github.eonewg.gnome.feature.explore.toExploreMemo
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel @Inject constructor(
    accountService: AccountService
) : ViewModel() {
    val exploreMemos = accountService.currentAccount
        .flatMapLatest { account ->
            if (account == null || account is Account.Local) {
                return@flatMapLatest flowOf(PagingData.empty<ExploreMemo>())
            }

            val remoteRepository = accountService.getRemoteDataSource()
                ?: return@flatMapLatest flowOf(PagingData.empty<ExploreMemo>())

            Pager(PagingConfig(pageSize = EXPLORE_PAGE_SIZE)) {
                ExplorePagingSource(remoteRepository)
            }.flow
                .map { pagingData -> pagingData.map { it.toExploreMemo() } }
        }
        .cachedIn(viewModelScope)
}