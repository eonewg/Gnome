package io.github.eonewg.gnome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * All attachments of the current account, streamed from Room; no snapshot
 * management needed since the flow re-emits after every write.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ResourceListViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
) : ViewModel() {

    val resources: StateFlow<List<Attachment>> = accountService.currentAccount
        .flatMapLatest {
            memoService.getMemoRepository().observeAttachments()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}