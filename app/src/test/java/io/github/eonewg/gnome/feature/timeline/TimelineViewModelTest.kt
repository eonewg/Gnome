package io.github.eonewg.gnome.feature.timeline

import androidx.lifecycle.SavedStateHandle
import com.skydoves.sandwich.ApiResponse
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.account.SyncCompatibility
import io.github.eonewg.gnome.data.model.SyncStatus
import io.github.eonewg.gnome.feature.FakeAccountService
import io.github.eonewg.gnome.feature.FakeMemoRepository
import io.github.eonewg.gnome.feature.FakeMemoService
import io.github.eonewg.gnome.data.service.MemoActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Behavior tests for the Phase 11 timeline ViewModel: the sync reapply guard,
 * tag derivation, selection ordering, dialog guards during batch operations,
 * and the batch add-tag/delete lifecycles through the domain repository fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var accountService: FakeAccountService
    private lateinit var repository: FakeMemoRepository
    private lateinit var memoService: FakeMemoService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountService = FakeAccountService.build(RuntimeEnvironment.getApplication())
        repository = FakeMemoRepository()
        memoService = FakeMemoService(accountService, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        TimelineViewModel(
            memoService = memoService,
            accountService = accountService,
            appContext = RuntimeEnvironment.getApplication(),
            memoActions = MemoActions(
                memoService = memoService,
                accountService = accountService,
                appContext = RuntimeEnvironment.getApplication(),
            ),
            savedStateHandle = savedStateHandle,
        )

    private fun memo(
        id: String,
        content: String = "content",
        pinned: Boolean = false,
        created: Long = 0,
    ) = Memo(
        id = id,
        content = content,
        date = Instant.ofEpochSecond(created),
        pinned = pinned,
    )

    private fun collectMessages(viewModel: TimelineViewModel): StateFlow<List<TimelineMessage>> {
        val received = MutableStateFlow(emptyList<TimelineMessage>())
        viewModel.messages
            .onEach { received.value = received.value + it }
            .launchIn(CoroutineScope(testDispatcher))
        return received
    }

    @Test
    fun `mid-sync snapshots are withheld until syncing ends`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        memoService.domainMemoState.value = listOf(memo("a"))
        viewModel.uiState.first { it.memos.map { memo -> memo.id } == listOf("a") }

        memoService.syncStatusState.value = SyncStatus(syncing = true)
        viewModel.uiState.first { it.syncStatus.syncing }
        memoService.domainMemoState.value = listOf(memo("a"), memo("b"))
        // The (a+b, syncing) pair was processed synchronously and skipped.
        assertEquals(listOf("a"), viewModel.uiState.value.memos.map { it.id })

        memoService.syncStatusState.value = SyncStatus(syncing = false)
        viewModel.uiState.first { it.memos.map { memo -> memo.id } == listOf("a", "b") }
    }

    @Test
    fun `empty Room snapshot is loaded content`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val loadedState = viewModel.uiState.first { it.isLoaded }
        assertTrue(loadedState.memos.isEmpty())
    }

    @Test
    fun `tags are derived from timeline contents, deduplicated and sorted`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        memoService.domainMemoState.value = listOf(
            memo("a", "#foo hello"),
            memo("b", "#foo #bar"),
            memo("c", "no tags here"),
        )
        viewModel.uiState.first { it.memos.size == 3 }
        assertEquals(listOf("bar", "foo"), viewModel.uiState.value.tags)
    }

    @Test
    fun `selected memos follow display order rather than selection order`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        memoService.domainMemoState.value = listOf(
            memo("a", created = 200),
            memo("b", pinned = true, created = 100),
            memo("c", created = 150),
        )
        viewModel.uiState.first { it.memos.size == 3 }
        viewModel.setSortOrder(MemoSortOrder.CreatedNewest)
        viewModel.enterSelectionMode()
        viewModel.toggleSelection(memo("c"))
        viewModel.toggleSelection(memo("b"))

        val selected = viewModel.selectedMemos
        assertEquals(setOf("b", "c"), viewModel.uiState.value.selectedMemoIds)
        // Pinned-first display order: b, a, c — filtered to the selection.
        assertEquals(listOf("b", "c"), selected.map { it.id })
    }

    @Test
    fun `sort order survives view model recreation via saved state`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = newViewModel(savedStateHandle)
        viewModel.setSortOrder(MemoSortOrder.UpdatedOldest)
        assertEquals(MemoSortOrder.UpdatedOldest, savedStateHandle.get<MemoSortOrder>("timeline_sort_order"))

        val restored = newViewModel(savedStateHandle)
        assertEquals(MemoSortOrder.UpdatedOldest, restored.uiState.value.sortOrder)
    }

    @Test
    fun `dialogs cannot be dismissed while a batch operation is running`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        repository.memosById["m1"] = memo("m1", "hello")
        memoService.domainMemoState.value = listOf(repository.memosById.getValue("m1"))
        viewModel.uiState.first { it.memos.size == 1 }

        viewModel.enterSelectionMode()
        viewModel.toggleSelection(repository.memosById.getValue("m1"))
        viewModel.requestAddTagDialog()
        assertTrue(viewModel.uiState.value.showAddTagDialog)

        repository.writeGate = kotlinx.coroutines.CompletableDeferred()
        viewModel.addTagToSelection("work")
        assertTrue(viewModel.uiState.value.batchRunning)
        viewModel.dismissAddTagDialog()
        assertTrue(viewModel.uiState.value.showAddTagDialog)

        repository.writeGate!!.complete(Unit)
        viewModel.uiState.first { !it.batchRunning }
        assertFalse(viewModel.uiState.value.showAddTagDialog)
        assertFalse(viewModel.uiState.value.selectionMode)
    }

    @Test
    fun `add tag prepends to every selected memo and skips already tagged ones`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val messages = collectMessages(viewModel)
        repository.memosById["m1"] = memo("m1", "hello")
        repository.memosById["m2"] = memo("m2", "#work existing")
        memoService.domainMemoState.value = repository.memosById.values.toList()
        viewModel.uiState.first { it.memos.size == 2 }

        viewModel.enterSelectionMode()
        viewModel.toggleSelection(repository.memosById.getValue("m1"))
        viewModel.toggleSelection(repository.memosById.getValue("m2"))
        viewModel.addTagToSelection("work")
        viewModel.uiState.first { !it.batchRunning && !it.selectionMode }
        val delivered = messages.first { list -> list.any { it is TimelineMessage.TagAdded } }

        assertEquals(1, repository.updateCalls.size)
        assertEquals("m1", repository.updateCalls.single().identifier)
        assertEquals("#work hello", repository.updateCalls.single().content)
        // The legacy snackbar counts the whole selection, not just modified memos.
        assertEquals(2, delivered.filterIsInstance<TimelineMessage.TagAdded>().single().count)
        assertFalse(viewModel.uiState.value.selectionMode)
        assertTrue(viewModel.uiState.value.selectedMemoIds.isEmpty())
    }

    @Test
    fun `delete selection removes memos, reports the count and clears selection`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val messages = collectMessages(viewModel)
        repository.memosById["m1"] = memo("m1")
        repository.memosById["m2"] = memo("m2")
        memoService.domainMemoState.value = repository.memosById.values.toList()
        viewModel.uiState.first { it.memos.size == 2 }

        viewModel.enterSelectionMode()
        viewModel.toggleSelection(repository.memosById.getValue("m1"))
        viewModel.toggleSelection(repository.memosById.getValue("m2"))
        viewModel.requestBatchDeleteDialog()
        viewModel.deleteSelection()
        viewModel.uiState.first { !it.batchRunning && !it.selectionMode }
        val delivered = messages.first { list -> list.any { it is TimelineMessage.MemosDeleted } }

        assertEquals(setOf("m1", "m2"), repository.deletedIds.toSet())
        assertEquals(2, delivered.filterIsInstance<TimelineMessage.MemosDeleted>().single().count)
        assertTrue(viewModel.uiState.value.memos.isEmpty())
        assertFalse(viewModel.uiState.value.selectionMode)
        assertFalse(viewModel.uiState.value.showBatchDeleteDialog)
    }

    @Test
    fun `failed batch delete keeps selection mode and reports failures`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val messages = collectMessages(viewModel)
        repository.failWrites = true
        repository.memosById["m1"] = memo("m1")
        repository.memosById["m2"] = memo("m2")
        memoService.domainMemoState.value = repository.memosById.values.toList()
        viewModel.uiState.first { it.memos.size == 2 }

        viewModel.enterSelectionMode()
        viewModel.toggleSelection(repository.memosById.getValue("m1"))
        viewModel.toggleSelection(repository.memosById.getValue("m2"))
        viewModel.deleteSelection()
        viewModel.uiState.first { !it.batchRunning }
        val delivered = messages.first { list -> list.any { it is TimelineMessage.BatchFailed } }

        assertEquals(2, delivered.filterIsInstance<TimelineMessage.BatchFailed>().single().count)
        assertTrue(viewModel.uiState.value.selectionMode)
        assertEquals(setOf("m1", "m2"), viewModel.uiState.value.selectedMemoIds)
        // Failed deletes leave the memos in place.
        assertEquals(2, viewModel.uiState.value.memos.size)
    }

    @Test
    fun `exit selection mode clears selection and dialogs`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.enterSelectionMode()
        viewModel.toggleSelection(memo("m1"))
        viewModel.requestAddTagDialog()
        viewModel.requestBatchDeleteDialog()

        viewModel.exitSelectionMode()

        assertFalse(viewModel.uiState.value.selectionMode)
        assertTrue(viewModel.uiState.value.selectedMemoIds.isEmpty())
        assertFalse(viewModel.uiState.value.showAddTagDialog)
        assertFalse(viewModel.uiState.value.showBatchDeleteDialog)
    }

    @Test
    fun `manual sync maps version verdicts into the sync alert state`() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        accountService.syncCompatibility =
            SyncCompatibility.RequiresConfirmation(version = "0.31.0", message = "newer server")
        viewModel.syncNow()
        val confirmation = viewModel.uiState.value.syncAlert
        assertTrue(confirmation is TimelineSyncAlert.RequiresConfirmation)
        assertEquals("0.31.0", (confirmation as TimelineSyncAlert.RequiresConfirmation).version)

        viewModel.dismissSyncAlert()
        assertNull(viewModel.uiState.value.syncAlert)

        accountService.syncCompatibility = SyncCompatibility.Blocked(message = "server too old")
        viewModel.syncNow()
        val blocked = viewModel.uiState.value.syncAlert
        assertTrue(blocked is TimelineSyncAlert.Blocked)
        assertEquals("server too old", (blocked as TimelineSyncAlert.Blocked).message)
    }

    @Test
    fun `successful manual sync accepts the higher v1 version once`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        accountService.syncCompatibility = SyncCompatibility.Allowed

        viewModel.syncNow(allowHigherV1Version = "0.31.0")

        assertEquals(1, memoService.syncCalls)
        assertEquals(listOf("0.31.0"), accountService.rememberedAcceptedVersions)
        assertNull(viewModel.uiState.value.syncAlert)
    }

    @Test
    fun `failed sync surfaces an error message and alert`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        accountService.syncCompatibility = SyncCompatibility.Allowed
        memoService.syncResult = ApiResponse.Failure.Exception(IllegalStateException("offline"))

        viewModel.syncNow()

        val alert = viewModel.uiState.value.syncAlert
        assertTrue(alert is TimelineSyncAlert.Failed)
        assertEquals("offline", viewModel.uiState.value.errorMessage)
    }
}
