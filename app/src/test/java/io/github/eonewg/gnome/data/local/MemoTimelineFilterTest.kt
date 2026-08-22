package io.github.eonewg.gnome.data.local

import androidx.room.Room
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.model.MemoVisibility
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The widget timeline contract: [LocalMemoDataSource.observeTimeline] must
 * never surface archived or deleted rows, so widget rendering stays clean
 * without duplicating the filter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoTimelineFilterTest {

    private fun memo(
        id: String,
        accountKey: String = "remote",
        archived: Boolean = false,
    ) = MemoEntity(
        identifier = id,
        accountKey = accountKey,
        content = "内容 $id",
        date = Instant.ofEpochMilli(1_000_000 + id.hashCode().toLong()),
        visibility = MemoVisibility.PRIVATE,
        pinned = false,
        archived = archived,
    )

    private fun buildDataSource(context: android.content.Context): Pair<GnomeDatabase, LocalMemoDataSource> {
        val database = Room.inMemoryDatabaseBuilder(context, GnomeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val localData = LocalMemoDataSource(
            database.memoDao(),
            database.syncOperationDao(),
            database.tagDao(),
            RoomTransactionRunner(database),
        )
        return database to localData
    }

    @Test
    fun `observeTimeline excludes archived and deleted rows`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val (database, localData) = buildDataSource(context)
        try {
            localData.createLocalMemo(memo("live"), emptyList(), sync = true)
            localData.createLocalMemo(memo("archived", archived = true), emptyList(), sync = true)
            val deleted = memo("deleted")
            localData.createLocalMemo(deleted, emptyList(), sync = true)
            localData.markMemoDeleted(deleted)

            val timeline = localData.observeTimeline("remote").first()

            assertEquals(listOf("live"), timeline.map { it.memo.identifier })
        } finally {
            database.close()
        }
    }

    @Test
    fun `observeTimeline is scoped per account`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val (database, localData) = buildDataSource(context)
        try {
            localData.createLocalMemo(memo("a1", accountKey = "alice"), emptyList(), sync = false)
            localData.createLocalMemo(memo("b1", accountKey = "bob"), emptyList(), sync = false)

            val alice = localData.observeTimeline("alice").first()
            val bob = localData.observeTimeline("bob").first()

            assertEquals(listOf("a1"), alice.map { it.memo.identifier })
            assertEquals(listOf("b1"), bob.map { it.memo.identifier })
        } finally {
            database.close()
        }
    }
}