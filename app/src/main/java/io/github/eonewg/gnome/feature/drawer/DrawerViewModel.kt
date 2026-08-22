package io.github.eonewg.gnome.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.DailyUsageStat
import io.github.eonewg.gnome.data.service.AccountService
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * The drawer's header stats and tag list, streamed from the account's Room
 * flows so the drawer no longer reaches into the timeline ViewModel.
 */
@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
) : ViewModel() {

    val uiState: StateFlow<DrawerUiState> = combine(
        memoService.domainMemos,
        accountService.currentAccount,
    ) { memos, account ->
        memos to account
    }
        .flatMapLatest { (memos, account) ->
            val tagsFlow = if (account == null) {
                flowOf(emptyList())
            } else {
                memoService.getMemoRepository().observeTagsFlow().map { it.map { usage -> usage.tag } }
            }
            combine(flowOf(memos), tagsFlow) { snapshot, tags ->
                DrawerUiState(
                    tags = tags,
                    memoCount = snapshot.size,
                    matrix = calculateMatrix(snapshot),
                    displayName = account?.toUser()?.name?.takeIf { it.isNotBlank() } ?: "",
                    isRemoteAccount = account !is Account.Local,
                    days = account?.toUser()?.startDate?.let { startDate ->
                        ChronoUnit.DAYS.between(
                            startDate.atZone(OffsetDateTime.now().offset).toLocalDate(),
                            LocalDate.now(),
                        ).toInt()
                    } ?: 0,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DrawerUiState(),
        )

    private fun calculateMatrix(source: List<io.github.eonewg.gnome.core.model.Memo>): List<DailyUsageStat> {
        val countMap = HashMap<LocalDate, Int>()
        for (memo in source) {
            val date = memo.date.atZone(OffsetDateTime.now().offset).toLocalDate()
            countMap[date] = (countMap[date] ?: 0) + 1
        }
        return DailyUsageStat.initialMatrix.map {
            it.copy(count = countMap[it.date] ?: 0)
        }
    }
}