package io.github.eonewg.gnome.data.service

import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.model.SyncStatus
import io.github.eonewg.gnome.data.repository.AbstractMemoRepository
import io.github.eonewg.gnome.data.repository.MemoRepository
import javax.inject.Inject
import javax.inject.Singleton

// Open for the ViewModel unit tests, which subclass with in-memory fakes.
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
open class MemoService @Inject constructor(
    private val accountService: AccountService,
) {
    private var lastSyncTime = 0L
    private val syncThreshold = 5000L
    private val syncMutex = Mutex()

    suspend fun getRepository(): AbstractMemoRepository {
        return accountService.getRepository()
    }

    /** The current account's repository under the domain-typed contract. */
    open suspend fun getMemoRepository(): MemoRepository {
        return accountService.getMemoRepository()
    }

    open val syncStatus: Flow<SyncStatus> = accountService.currentAccount.flatMapLatest {
        accountService.getRepository().syncStatus
    }

    val memos: Flow<List<MemoEntity>> = accountService.currentAccount.flatMapLatest {
        accountService.getRepository().observeMemos()
    }

    /** Timeline as domain models — the read path new UI code uses. */
    open val domainMemos: Flow<List<Memo>> = accountService.currentAccount.flatMapLatest {
        accountService.getMemoRepository().observeTimeline()
    }

    open suspend fun sync(force: Boolean): ApiResponse<Unit> {
        return syncMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && (now - lastSyncTime) <= syncThreshold) {
                return@withLock ApiResponse.Success(Unit)
            }

            val repo = getRepository()
            val result = repo.sync()
            if (result is ApiResponse.Success) {
                lastSyncTime = now
            }
            result
        }
    }
}
