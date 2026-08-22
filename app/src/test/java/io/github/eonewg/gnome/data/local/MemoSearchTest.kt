package io.github.eonewg.gnome.data.local

import androidx.room.Room
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.MemoWithResources
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
 * Locally-runnable coverage for the Room content search: CJK substring,
 * archived/tag/date-range filters and LIKE wildcard escaping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoSearchTest {

    private fun memo(id: String, content: String, archived: Boolean = false) = MemoEntity(
        identifier = id,
        accountKey = "local",
        content = content,
        date = Instant.ofEpochMilli(1_000_000 + id.hashCode().toLong()),
        visibility = MemoVisibility.PRIVATE,
        pinned = false,
        archived = archived,
    )

    @Test
    fun `content search matches CJK substrings case-insensitively`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, GnomeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val localData = LocalMemoDataSource(
                database.memoDao(),
                database.syncOperationDao(),
                database.tagDao(),
                RoomTransactionRunner(database),
            )
            localData.createLocalMemo(memo("m1", "今天学习了 #数学 和 #408/计网"), emptyList(), sync = false)
            localData.createLocalMemo(memo("m2", "学习笔记：FooBar 实现"), emptyList(), sync = false)
            localData.createLocalMemo(memo("m3", "不相关的内容"), emptyList(), sync = false)

            val chinese = localData.searchMemos("local", "计网").first()
            assertEquals(listOf("m1"), chinese.map { it.memo.identifier })

            val caseInsensitive = localData.searchMemos("local", "foobar").first()
            assertEquals(listOf("m2"), caseInsensitive.map { it.memo.identifier })

            val noMatch = localData.searchMemos("local", "不存在").first()
            assertEquals(emptyList<MemoWithResources>(), noMatch)
        } finally {
            database.close()
        }
    }

    @Test
    fun `search filters archived tag and date range`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, GnomeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val localData = LocalMemoDataSource(
                database.memoDao(),
                database.syncOperationDao(),
                database.tagDao(),
                RoomTransactionRunner(database),
            )
            localData.createLocalMemo(memo("m1", "#work alpha"), emptyList(), sync = false)
            localData.createLocalMemo(memo("m2", "#work alpha archived", archived = true), emptyList(), sync = false)
            localData.createLocalMemo(memo("m3", "#life alpha"), emptyList(), sync = false)
            localData.createLocalMemo(memo("m4", "#work/gamma alpha"), emptyList(), sync = false)

            // Archived memos are excluded by default and included on request.
            // Result order is pinned DESC, date DESC (dates derive from id.hashCode).
            val live = localData.searchMemos("local", "alpha").first()
            assertEquals(listOf("m4", "m3", "m1"), live.map { it.memo.identifier })

            val withArchived = localData.searchMemos("local", "alpha", includeArchived = true).first()
            assertEquals(listOf("m4", "m3", "m2", "m1"), withArchived.map { it.memo.identifier })

            // Exact tag filter, ancestors matching via the index.
            val byTag = localData.searchMemos("local", "alpha", tag = "work").first()
            assertEquals(listOf("m4", "m1"), byTag.map { it.memo.identifier })

            // Half-open date range.
            val byRange = localData.searchMemos(
                "local",
                "alpha",
                dateFrom = Instant.ofEpochMilli(1_000_000 + "m3".hashCode().toLong()),
                dateTo = Instant.ofEpochMilli(1_000_000 + "m4".hashCode().toLong() + 1),
            ).first()
            assertEquals(listOf("m4", "m3"), byRange.map { it.memo.identifier })
        } finally {
            database.close()
        }
    }

    @Test
    fun `like wildcards in the query stay literal`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, GnomeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val localData = LocalMemoDataSource(
                database.memoDao(),
                database.syncOperationDao(),
                database.tagDao(),
                RoomTransactionRunner(database),
            )
            localData.createLocalMemo(memo("m1", "progress 100% and A_B"), emptyList(), sync = false)
            localData.createLocalMemo(memo("m2", "plain text"), emptyList(), sync = false)

            // `%` would match everything if unescaped; it must match literally.
            val percent = localData.searchMemos("local", "100%").first()
            assertEquals(listOf("m1"), percent.map { it.memo.identifier })

            val underscore = localData.searchMemos("local", "A_B").first()
            assertEquals(listOf("m1"), underscore.map { it.memo.identifier })

            // A bare `%` query is escaped, so it only matches memos
            // containing a literal percent sign.
            val singleWildcard = localData.searchMemos("local", "%").first()
            assertEquals(listOf("m1"), singleWildcard.map { it.memo.identifier })
        } finally {
            database.close()
        }
    }
}