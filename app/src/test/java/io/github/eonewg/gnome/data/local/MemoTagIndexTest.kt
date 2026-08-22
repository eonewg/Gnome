package io.github.eonewg.gnome.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.data.model.TagUsage
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
 * Locally-runnable coverage for the memo_tags index: the 2→3 migration
 * backfill (the schema-validated equivalent lives in the androidTest
 * MigrationTest), the transactional tag-row lifecycle in
 * [LocalMemoDataSource], and the tag query semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoTagIndexTest {

    private fun tagsOf(db: SupportSQLiteDatabase, where: String): List<String> {
        val tags = mutableListOf<String>()
        db.query("SELECT tag FROM memo_tags WHERE $where ORDER BY tag").use { cursor ->
            while (cursor.moveToNext()) tags.add(cursor.getString(0))
        }
        return tags
    }

    @Test
    fun migration2To3_backfillsTagIndexFromLiveMemoContent() {
        val context = RuntimeEnvironment.getApplication()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Schema 2 memos table, copied from schemas/2.json.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `memos` (" +
                                "`identifier` TEXT NOT NULL, `remoteId` TEXT, " +
                                "`accountKey` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                                "`date` INTEGER NOT NULL, `visibility` TEXT NOT NULL, " +
                                "`pinned` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                                "`needsSync` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                                "`lastModified` INTEGER NOT NULL, `lastSyncedAt` INTEGER, " +
                                "PRIMARY KEY(`identifier`))"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        try {
            fun insertMemo(id: String, accountKey: String, content: String, deleted: Int) {
                db.execSQL(
                    "INSERT INTO memos (identifier, accountKey, content, date, visibility, " +
                        "pinned, archived, needsSync, isDeleted, lastModified) " +
                        "VALUES (?, ?, ?, 1000, 'PRIVATE', 0, 0, 0, $deleted, 2000)",
                    arrayOf(id, accountKey, content)
                )
            }
            insertMemo("m1", "local", "#work #book/fiction note", 0)
            insertMemo("m2", "local", "plain text with `#code` only", 0)
            insertMemo("m3", "local", "#deleted should not index", 1)
            insertMemo("m4", "other", "#数学 #408/计网", 0)

            GnomeDatabase.MIGRATION_2_3.migrate(db)

            // Direct values, slash ancestors, code-span and tombstone exclusion.
            assertEquals(listOf("book", "book/fiction", "work"), tagsOf(db, "memoId = 'm1'"))
            assertEquals(emptyList<String>(), tagsOf(db, "memoId IN ('m2', 'm3')"))
            assertEquals(listOf("408", "408/计网", "数学"), tagsOf(db, "accountKey = 'other'"))
        } finally {
            db.close()
            helper.close()
        }
    }

    @Test
    fun tagIndex_followsMemoLifecycle() = runTest {
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
            val tagDao = database.tagDao()

            fun memo(content: String) = MemoEntity(
                identifier = "m1",
                accountKey = "local",
                content = content,
                date = Instant.ofEpochMilli(1_000),
                visibility = MemoVisibility.PRIVATE,
                pinned = false,
            )

            suspend fun indexedTags(): List<String> =
                tagDao.getTagsForMemo("local", "m1").map { it.tag }.sorted()

            localData.createLocalMemo(memo("#work #book/fiction"), emptyList(), sync = false)
            assertEquals(listOf("book", "book/fiction", "work"), indexedTags())

            localData.updateLocalMemo(memo("#renamed"), null, sync = false)
            assertEquals(listOf("renamed"), indexedTags())

            localData.setMemoArchived(memo("#renamed"), archived = true, sync = false)
            assertEquals(listOf("renamed"), indexedTags())

            localData.markMemoDeleted(memo("#renamed"))
            assertEquals(emptyList<String>(), indexedTags())

            localData.upsertMemo(memo("#restored/by-sync"))
            assertEquals(listOf("restored", "restored/by-sync"), indexedTags())

            localData.purgeMemo("m1", "local")
            assertEquals(emptyList<String>(), indexedTags())
        } finally {
            database.close()
        }
    }

    @Test
    fun tagQueries_orderByFrequencyThenName_andFilterLiveTimeline() = runTest {
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

            fun memo(id: String, content: String, archived: Boolean = false): MemoEntity =
                MemoEntity(
                    identifier = id,
                    accountKey = "local",
                    content = content,
                    date = Instant.ofEpochMilli(1_000L + id.hashCode()),
                    visibility = MemoVisibility.PRIVATE,
                    pinned = false,
                    archived = archived,
                )

            // work appears twice, life once; b/child implies ancestor b.
            localData.createLocalMemo(memo("m1", "#work #life"), emptyList(), sync = false)
            localData.createLocalMemo(memo("m2", "#work #b/child"), emptyList(), sync = false)
            // Archived and soft-deleted memos must not inflate counts or results.
            localData.createLocalMemo(memo("m3", "#life", archived = true), emptyList(), sync = false)
            localData.markMemoDeleted(memo("m4", "#work"))

            // frequency DESC first, then tag ASC: work(2) outranks the 1-count tags.
            val tags = database.tagDao().observeTags("local").first()
            assertEquals(
                listOf(TagUsage("work", 2), TagUsage("b", 1), TagUsage("b/child", 1), TagUsage("life", 1)),
                tags,
            )

            // Prefix suggestions are literal (`%`/`_` escaped) and frequency-ordered.
            assertEquals(
                listOf(TagUsage("b", 1), TagUsage("b/child", 1)),
                localData.getTagSuggestions("local", "b"),
            )
            assertEquals(emptyList<TagUsage>(), localData.getTagSuggestions("local", "100%"))

            // Whole-word lookup matches indexed ancestors; live timeline only.
            val byChild = database.tagDao().observeMemosByTag("local", "b/child").first()
            assertEquals(listOf("m2"), byChild.map { it.identifier })
            val byLife = database.tagDao().observeMemosByTag("local", "life").first()
            assertEquals(listOf("m1"), byLife.map { it.identifier })
        } finally {
            database.close()
        }
    }
}