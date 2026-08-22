package io.github.eonewg.gnome.feature.timeline

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.core.model.toCore
import io.github.eonewg.gnome.data.account.SyncCompatibility
import io.github.eonewg.gnome.data.constant.GnomeException
import io.github.eonewg.gnome.data.constant.MemosVersionSupport
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.ResourceRepresentable
import io.github.eonewg.gnome.data.model.SyncStatus
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoActions
import io.github.eonewg.gnome.data.service.MemoService
import io.github.eonewg.gnome.ext.getErrorMessage
import io.github.eonewg.gnome.core.tag.MemosTagParser
import io.github.eonewg.gnome.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    @param:ApplicationContext private val appContext: Context,
    private val memoActions: MemoActions,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TimelineUiState(
            sortOrder = savedStateHandle.get<MemoSortOrder>(KEY_SORT_ORDER) ?: MemoSortOrder.CreatedNewest,
        )
    )
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<TimelineMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<TimelineMessage> = _messages.asSharedFlow()

    /** Selection in display order (pinned first), as the legacy home page computed it. */
    val selectedMemos: List<Memo>
        get() = orderMemosForTimeline(_uiState.value.memos, _uiState.value.sortOrder)
            .filter { it.id in _uiState.value.selectedMemoIds }

    init {
        viewModelScope.launch {
            var hasPresentedLocalSnapshot = false
            combine(memoService.domainMemos, memoService.syncStatus) { latestMemos, status ->
                latestMemos to status
            }
                .distinctUntilChanged()
                .collectLatest { (latestMemos, status) ->
                    _uiState.update { it.copy(syncStatus = status) }
                    // Don't reapply a Room snapshot mid-sync: reconcile would flicker
                    // pending rows away while the SyncEngine is still pushing them.
                    if (!hasPresentedLocalSnapshot || !status.syncing) {
                        applyMemos(latestMemos)
                        hasPresentedLocalSnapshot = true
                    }
                }
        }
        viewModelScope.launch {
            accountService.currentAccount.collect { account ->
                _uiState.update {
                    it.copy(
                        isLocalAccount = account is Account.Local,
                        host = when (account) {
                            is Account.MemosV0 -> account.info.host
                            is Account.MemosV1 -> account.info.host
                            else -> null
                        },
                        defaultVisibility = account?.toUser()?.defaultVisibility?.toCore(),
                    )
                }
            }
        }
    }

    private suspend fun applyMemos(latestMemos: List<Memo>) {
        val snapshot = latestMemos.toList()
        if (_uiState.value.memos != snapshot) {
            val preparedTags = withContext(Dispatchers.Default) {
                snapshot.asSequence()
                    .flatMap { MemosTagParser.extractTags(it.content).asSequence() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .toList()
            }
            _uiState.update { it.copy(memos = snapshot, tags = preparedTags) }
        }
        _uiState.update { it.copy(errorMessage = null) }
    }

    suspend fun loadMemos(syncAfterLoad: Boolean = true) = withContext(viewModelScope.coroutineContext) {
        if (syncAfterLoad) {
            val compatibility = accountService.checkCurrentAccountSyncCompatibility(isAutomatic = true)
            if (compatibility !is SyncCompatibility.Allowed) {
                return@withContext
            }

            val syncResult = memoService.sync(false)
            if (syncResult is ApiResponse.Success) {
                WidgetUpdater.updateWidgets(appContext)
            } else {
                if (!syncResult.isAccessTokenInvalidFailure()) {
                    _uiState.update { it.copy(errorMessage = syncResult.getErrorMessage()) }
                }
            }
        }
    }

    /** Manual sync; on version problems the dialog state lands in [TimelineUiState.syncAlert]. */
    suspend fun syncNow(allowHigherV1Version: String? = null) = withContext(viewModelScope.coroutineContext) {
        when (val result = refreshMemos(allowHigherV1Version)) {
            ManualSyncResult.Completed -> Unit
            is ManualSyncResult.Blocked -> _uiState.update {
                it.copy(syncAlert = TimelineSyncAlert.Blocked(result.message))
            }
            is ManualSyncResult.RequiresConfirmation -> _uiState.update {
                it.copy(syncAlert = TimelineSyncAlert.RequiresConfirmation(result.version, result.message))
            }
            is ManualSyncResult.Failed -> _uiState.update {
                it.copy(syncAlert = TimelineSyncAlert.Failed(result.message))
            }
        }
    }

    private suspend fun refreshMemos(allowHigherV1Version: String? = null): ManualSyncResult =
        withContext(viewModelScope.coroutineContext) {
            when (val compatibility = accountService.checkCurrentAccountSyncCompatibility(
                isAutomatic = false,
                allowHigherV1Version = allowHigherV1Version
            )) {
                is SyncCompatibility.Blocked -> {
                    return@withContext ManualSyncResult.Blocked(
                        compatibility.message ?: MemosVersionSupport.supportedVersionsMessage(appContext)
                    )
                }
                is SyncCompatibility.RequiresConfirmation -> {
                    return@withContext ManualSyncResult.RequiresConfirmation(
                        version = compatibility.version,
                        message = compatibility.message
                    )
                }
                SyncCompatibility.Allowed -> Unit
            }

            val syncResult = memoService.sync(true)
            if (syncResult is ApiResponse.Success) {
                if (allowHigherV1Version != null) {
                    accountService.rememberAcceptedUnsupportedSyncVersion(allowHigherV1Version)
                }
                WidgetUpdater.updateWidgets(appContext)
            } else {
                val message = syncResult.getErrorMessage()
                _uiState.update { it.copy(errorMessage = message) }
                return@withContext ManualSyncResult.Failed(message)
            }
            ManualSyncResult.Completed
        }

    fun dismissSyncAlert() {
        _uiState.update { it.copy(syncAlert = null) }
    }

    private fun ApiResponse<Unit>.isAccessTokenInvalidFailure(): Boolean {
        return this is ApiResponse.Failure.Exception && this.throwable == GnomeException.accessTokenInvalid
    }

    // -----------------------------------------------------------------------
    // Sorting & selection
    // -----------------------------------------------------------------------

    fun setSortOrder(sortOrder: MemoSortOrder) {
        savedStateHandle[KEY_SORT_ORDER] = sortOrder
        _uiState.update { it.copy(sortOrder = sortOrder) }
    }

    fun enterSelectionMode() {
        _uiState.update { it.copy(selectionMode = true) }
    }

    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                selectionMode = false,
                selectedMemoIds = emptySet(),
                showAddTagDialog = false,
                showBatchDeleteDialog = false,
            )
        }
    }

    fun toggleSelection(memo: Memo) {
        _uiState.update { state ->
            val ids = if (memo.id in state.selectedMemoIds) {
                state.selectedMemoIds - memo.id
            } else {
                state.selectedMemoIds + memo.id
            }
            state.copy(selectedMemoIds = ids)
        }
    }

    fun requestAddTagDialog() {
        _uiState.update { it.copy(showAddTagDialog = true) }
    }

    fun dismissAddTagDialog() {
        if (!_uiState.value.batchRunning) {
            _uiState.update { it.copy(showAddTagDialog = false) }
        }
    }

    fun requestBatchDeleteDialog() {
        _uiState.update { it.copy(showBatchDeleteDialog = true) }
    }

    fun dismissBatchDeleteDialog() {
        if (!_uiState.value.batchRunning) {
            _uiState.update { it.copy(showBatchDeleteDialog = false) }
        }
    }

    // -----------------------------------------------------------------------
    // Batch operations
    // -----------------------------------------------------------------------

    fun addTagToSelection(tag: String) {
        val targets = selectedMemos
        if (targets.isEmpty()) return
        viewModelScope.launch {
            setBatchRunning(true)
            var failedCount = 0
            targets.forEach { memo ->
                if (tag !in MemosTagParser.extractTags(memo.content)) {
                    val updatedContent = if (memo.content.isBlank()) {
                        "#$tag"
                    } else {
                        "#$tag ${memo.content}"
                    }
                    val response = editMemo(
                        memoIdentifier = memo.id,
                        content = updatedContent,
                        attachments = memo.attachments,
                        visibility = memo.visibility,
                    )
                    if (response !is ApiResponse.Success) {
                        failedCount++
                    }
                }
            }
            _uiState.update { it.copy(batchRunning = false, showAddTagDialog = false) }
            if (failedCount == 0) {
                exitSelectionMode()
                _messages.tryEmit(TimelineMessage.TagAdded(targets.size))
            } else {
                _messages.tryEmit(TimelineMessage.BatchFailed(failedCount))
            }
        }
    }

    fun deleteSelection() {
        val targets = selectedMemos
        if (targets.isEmpty()) return
        viewModelScope.launch {
            setBatchRunning(true)
            var failedCount = 0
            targets.forEach { memo ->
                if (deleteMemo(memo.id) !is ApiResponse.Success) {
                    failedCount++
                }
            }
            val existingIds = _uiState.value.memos.map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    batchRunning = false,
                    showBatchDeleteDialog = false,
                    selectedMemoIds = it.selectedMemoIds.filterTo(mutableSetOf()) { id -> id in existingIds },
                )
            }
            if (failedCount == 0) {
                exitSelectionMode()
                _messages.tryEmit(TimelineMessage.MemosDeleted(targets.size))
            } else {
                _messages.tryEmit(TimelineMessage.BatchFailed(failedCount))
            }
        }
    }

    private fun setBatchRunning(running: Boolean) {
        _uiState.update { it.copy(batchRunning = running) }
    }

    // -----------------------------------------------------------------------
    // Single-memo operations
    // -----------------------------------------------------------------------

    suspend fun editMemo(
        memoIdentifier: String,
        content: String,
        attachments: List<Attachment>?,
        visibility: MemoVisibility,
    ): ApiResponse<Memo> = withContext(viewModelScope.coroutineContext) {
        memoService.getMemoRepository().updateMemo(
            identifier = memoIdentifier,
            content = content,
            attachments = attachments,
            visibility = visibility,
        ).also { response ->
            if (response is ApiResponse.Success) {
                applyMemoUpdate(response.data)
                WidgetUpdater.updateWidgets(appContext)
            }
        }
    }

    suspend fun updateMemoPinned(memoIdentifier: String, pinned: Boolean) =
        withContext(viewModelScope.coroutineContext) {
            memoService.getMemoRepository().updateMemo(memoIdentifier, pinned = pinned).let { response ->
                if (response is ApiResponse.Success) {
                    applyMemoUpdate(response.data)
                    WidgetUpdater.updateWidgets(appContext)
                }
                response
            }
        }

    suspend fun archiveMemo(memoIdentifier: String) = withContext(viewModelScope.coroutineContext) {
        memoService.getMemoRepository().archiveMemo(memoIdentifier).let { response ->
            if (response is ApiResponse.Success) {
                removeMemoFromState(memoIdentifier)
                WidgetUpdater.updateWidgets(appContext)
            }
            response
        }
    }

    suspend fun deleteMemo(memoIdentifier: String) = withContext(viewModelScope.coroutineContext) {
        memoService.getMemoRepository().deleteMemo(memoIdentifier).let { response ->
            if (response is ApiResponse.Success) {
                removeMemoFromState(memoIdentifier)
                WidgetUpdater.updateWidgets(appContext)
            }
            response
        }
    }

    /** Checkbox toggles inside the rendered memo keep attachments and visibility. */
    suspend fun updateMemoContent(memoIdentifier: String, content: String): ApiResponse<Memo> =
        memoActions.updateContent(memoIdentifier, content).also { response ->
            if (response is ApiResponse.Success) {
                applyMemoUpdate(response.data)
                WidgetUpdater.updateWidgets(appContext)
            }
        }

    suspend fun cacheResourceFile(resourceIdentifier: String, downloadedUri: Uri): ApiResponse<Unit> =
        memoActions.cacheResource(resourceIdentifier, downloadedUri)

    suspend fun downloadAndCacheResource(resource: ResourceRepresentable): Uri? =
        memoActions.downloadAndCache(resource)

    private fun applyMemoUpdate(memo: Memo) {
        _uiState.update { state ->
            val index = state.memos.indexOfFirst { it.id == memo.id }
            if (index == -1) {
                state
            } else {
                state.copy(memos = state.memos.toMutableList().also { it[index] = memo })
            }
        }
    }

    private fun removeMemoFromState(memoIdentifier: String) {
        _uiState.update { state ->
            state.copy(memos = state.memos.filterNot { it.id == memoIdentifier })
        }
    }

    private companion object {
        const val KEY_SORT_ORDER = "timeline_sort_order"
    }
}
