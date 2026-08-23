package io.github.eonewg.gnome.feature.memo

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.service.MemoActions
import io.github.eonewg.gnome.feature.FakeAccountService
import io.github.eonewg.gnome.feature.FakeMemoRepository
import io.github.eonewg.gnome.feature.FakeMemoService
import java.time.Instant
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

/**
 * The memo id arrives from the typed navigation key through the assisted factory.
 * These tests lock that fixed entry-scoped selection while the live memo flow changes.
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

    private fun newViewModel(memoId: String) = MemoDetailViewModel(
        memoId = memoId,
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
    fun `assisted memo id picks the memo from the live domain flow`() = runTest(testDispatcher) {
        repository.memosById["m1"] = memo("m1", "hello")
        memoService.domainMemoState.value = listOf(memo("m1", "hello"))
        val viewModel = newViewModel("m1")

        viewModel.uiState.first { it.memo != null }

        assertEquals("m1", viewModel.uiState.value.memo?.id)
        assertEquals("hello", viewModel.uiState.value.memo?.content)
    }

    @Test
    fun `assisted memo id stays selected across live updates`() = runTest(testDispatcher) {
        repository.memosById["m1"] = memo("m1")
        memoService.domainMemoState.value = listOf(memo("m1"))
        val viewModel = newViewModel("m1")
        viewModel.uiState.first { it.memo != null }

        memoService.domainMemoState.value = listOf(memo("m1", "updated"))
        viewModel.uiState.first { it.memo?.content == "updated" }

        assertEquals("m1", viewModel.uiState.value.memo?.id)
        assertEquals("updated", viewModel.uiState.value.memo?.content)
    }

    @Test
    fun `separate assisted ids select separate memos`() = runTest(testDispatcher) {
        repository.memosById["m1"] = memo("m1")
        repository.memosById["m2"] = memo("m2")
        memoService.domainMemoState.value = listOf(memo("m1"), memo("m2"))
        val firstViewModel = newViewModel("m1")
        val secondViewModel = newViewModel("m2")

        firstViewModel.uiState.first { it.memo?.id == "m1" }
        secondViewModel.uiState.first { it.memo?.id == "m2" }

        assertEquals("m1", firstViewModel.uiState.value.memo?.id)
        assertEquals("m2", secondViewModel.uiState.value.memo?.id)
    }

    @Test
    fun `unknown id leaves the memo empty like the not-found page`() = runTest(testDispatcher) {
        memoService.domainMemoState.value = listOf(memo("m1"))
        val viewModel = newViewModel("missing")

        viewModel.uiState.first { it.memo == null }

        assertNull(viewModel.uiState.value.memo)
    }
}