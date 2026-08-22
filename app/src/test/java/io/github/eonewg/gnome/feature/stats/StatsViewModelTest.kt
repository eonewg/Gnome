package io.github.eonewg.gnome.feature.stats

import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.feature.FakeAccountService
import io.github.eonewg.gnome.feature.FakeMemoRepository
import io.github.eonewg.gnome.feature.FakeMemoService
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var memoService: FakeMemoService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        memoService = FakeMemoService(
            FakeAccountService.build(RuntimeEnvironment.getApplication()),
            FakeMemoRepository(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = StatsViewModel(memoService = memoService)

    @Test
    fun `snapshot derives from the domain memo stream`() = runTest(testDispatcher) {
        val zone = ZoneId.systemDefault()
        memoService.domainMemoState.value = listOf(
            Memo(id = "m1", content = "你好🙂", date = Instant.parse("2026-08-20T16:30:00Z")),
            Memo(id = "m2", content = "ab", date = Instant.parse("2026-08-21T02:00:00Z")),
        )

        val viewModel = newViewModel()
        val state = viewModel.uiState.first { it.snapshot.totalMemoCount == 2 }

        val day = state.snapshot.day(
            Instant.parse("2026-08-21T02:00:00Z").atZone(zone).toLocalDate()
        )
        assertEquals(2, day.memoCount)
        assertEquals(5, day.characterCount)
        assertEquals(YearMonth.of(2026, 8), day.date.let { YearMonth.from(it) })
    }

    @Test
    fun `empty stream produces an empty snapshot`() = runTest(testDispatcher) {
        memoService.domainMemoState.value = emptyList()

        val viewModel = newViewModel()
        val state = viewModel.uiState.first { it.snapshot.totalMemoCount == 0 }

        assertEquals(0, state.snapshot.totalMemoCount)
        assertEquals(0, state.snapshot.activeDayCount)
    }
}