package io.github.eonewg.gnome.feature.memo

import androidx.lifecycle.SavedStateHandle
import io.github.eonewg.gnome.data.service.MemoActions
import io.github.eonewg.gnome.feature.FakeAccountService
import io.github.eonewg.gnome.feature.FakeMemoRepository
import io.github.eonewg.gnome.feature.FakeMemoService
import io.github.eonewg.gnome.core.model.Memo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The memo id arrives from the typed navigation key via [MemoDetailViewModel.setMemoId]
 * (never from a SavedStateHandle). These tests lock the contract the route relies on:
 * setting the id is idempotent — recomposition must not reset restored state — and a
 * changed id switches the viewed memo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoDetailViewModelTest {

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

    private fun newViewModel() = MemoDetailViewModel(
        memoService = memoService,
        accountService = accountService,
        memoActions = MemoActions(
            memoService = memoService,
            accountService = accountService,
            appContext = RuntimeEnvironment.getApplication(),
        ),
    )

    private fun memo(id: String, content: String = "content $id") = Memo(
        id = id,
        content = content,
        date = Instant.EPOCH,
    )

    @Test
    fun `setMemoId picks the memo from the live domain flow`() = runTest(testDispatcher) {
        repository.memosById["m1"] = memo("m1", "hello")
        memoService.domainMemoState.value = listOf(memo("m1", "hello"))
        val viewModel = newViewModel()

        viewModel.setMemoId("m1")
        viewModel.uiState.first { it.memo != null }

        assertEquals("m1", viewModel.uiState.value.memo?.id)
        assertEquals("hello", viewModel.uiState.value.memo?.content)
    }

    @Test
    fun `setMemoId is idempotent for repeated same-id calls`() = runTest(testDispatcher) {
        repository.memosById["m1"] = memo("m1")
        memoService.domainMemoState.value = listOf(memo("m1"))
        val viewModel = newViewModel()

        // Recompositions re-run LaunchedEffect-keyed calls with the same value.
        viewModel.setMemoId("m1")
        viewModel.setMemoId("m1")
        viewModel.setMemoId("m1")
        viewModel.uiState.first { it.memo != null }

        assertEquals("m1", viewModel.uiState.value.memo?.id)
    }

    @Test
    fun `setMemoId switches to the new id without stale state`() = runTest(testDispatcher) {
        repository.memosById["m1"] = memo("m1")
        repository.memosById["m2"] = memo("m2")
        memoService.domainMemoState.value = listOf(memo("m1"), memo("m2"))
        val viewModel = newViewModel()

        viewModel.setMemoId("m1")
        viewModel.uiState.first { it.memo?.id == "m1" }
        viewModel.setMemoId("m2")
        viewModel.uiState.first { it.memo?.id == "m2" }

        assertEquals("m2", viewModel.uiState.value.memo?.id)
    }

    @Test
    fun `unknown id leaves the memo empty like the not-found page`() = runTest(testDispatcher) {
        memoService.domainMemoState.value = listOf(memo("m1"))
        val viewModel = newViewModel()

        viewModel.setMemoId("missing")
        // The flow emits with a null memo; no crash, page shows the empty state.
        viewModel.uiState.first { it.memo == null }

        assertNull(viewModel.uiState.value.memo)
    }
}