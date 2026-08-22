package io.github.eonewg.gnome.feature.search

import androidx.lifecycle.SavedStateHandle
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.model.Account
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchViewModelTest {

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

    private fun newViewModel() = SearchViewModel(
        memoService = memoService,
        accountService = accountService,
    )

    private fun memo(id: String, content: String) = Memo(
        id = id,
        content = content,
        date = Instant.EPOCH,
    )

    @Test
    fun `blank query stays idle without touching the repository`() = runTest(testDispatcher) {
        repository.searchState.value = listOf(memo("m1", "#work note"))

        val viewModel = newViewModel()
        val state = viewModel.uiState.first { !it.hasSearched }

        assertFalse(state.hasSearched)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `typed query streams database results into the state`() = runTest(testDispatcher) {
        repository.searchState.value = listOf(memo("m1", "note"), memo("m2", "notes"))

        val viewModel = newViewModel()
        viewModel.setQuery("note")
        val state = viewModel.uiState.first { it.hasSearched }

        assertEquals(listOf("m1", "m2"), state.results.map { it.id })
        assertEquals("note", state.query)
    }

    @Test
    fun `clearing the query returns to the idle state`() = runTest(testDispatcher) {
        repository.searchState.value = listOf(memo("m1", "note"))

        val viewModel = newViewModel()
        viewModel.setQuery("note")
        viewModel.uiState.first { it.hasSearched }
        viewModel.setQuery("")
        val state = viewModel.uiState.first { !it.hasSearched }

        assertFalse(state.hasSearched)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `archived toggle flips the flag in the state`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.setQuery("note")
        viewModel.setIncludeArchived(true)

        val state = viewModel.uiState.first { it.includeArchived }
        assertTrue(state.includeArchived)
    }

    @Test
    fun `no account keeps the search idle`() = runTest(testDispatcher) {
        accountService.accountState.value = null
        repository.searchState.value = listOf(memo("m1", "note"))

        val viewModel = newViewModel()
        viewModel.setQuery("note")

        val state = viewModel.uiState.first { !it.hasSearched }
        assertFalse(state.hasSearched)
    }
}