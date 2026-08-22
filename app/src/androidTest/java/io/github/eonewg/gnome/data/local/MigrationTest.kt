package io.github.eonewg.gnome.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GnomeDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesMemosAndCreatesEmptyOutbox() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO memos (
                    identifier, remoteId, accountKey, content, date, visibility,
                    pinned, archived, needsSync, isDeleted, lastModified, lastSyncedAt
                ) VALUES (
                    'm1', 'r1', 'local', 'pending offline edit', 1000, 'PRIVATE',
                    0, 0, 1, 0, 2000, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO resources (
                    identifier, remoteId, accountKey, date, filename, uri, localUri, mimeType, memoId
                ) VALUES (
                    'res1', NULL, 'local', 1000, 'a.png', 'file:///tmp/a.png', 'file:///tmp/a.png', 'image/png', 'm1'
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, GnomeDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT content, needsSync, isDeleted FROM memos WHERE identifier = 'm1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("pending offline edit", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
            }
            db.query("SELECT filename FROM resources WHERE identifier = 'res1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("a.png", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM sync_operations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To3_backfillsTagIndexFromMemoContent() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO memos (
                    identifier, remoteId, accountKey, content, date, visibility,
                    pinned, archived, needsSync, isDeleted, lastModified, lastSyncedAt
                ) VALUES
                    ('m1', 'r1', 'local', '#work #book/fiction note', 1000, 'PRIVATE', 0, 0, 0, 0, 2000, NULL),
                    ('m2', 'r2', 'local', 'plain text with `#code` only', 1100, 'PRIVATE', 0, 0, 0, 0, 2100, NULL),
                    ('m3', 'r3', 'local', '#deleted should not index', 1200, 'PRIVATE', 0, 0, 0, 1, 2200, NULL),
                    ('m4', NULL, 'other', '#数学 #408/计网', 1300, 'PRIVATE', 0, 0, 1, 0, 2300, NULL)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, GnomeDatabase.MIGRATION_2_3).use { db ->
            db.query(
                "SELECT tag FROM memo_tags WHERE accountKey = 'local' AND memoId = 'm1' ORDER BY tag"
            ).use { cursor ->
                val tags = mutableListOf<String>()
                while (cursor.moveToNext()) tags.add(cursor.getString(0))
                assertEquals(listOf("book", "book/fiction", "work"), tags)
            }
            // Code spans never produce tags; deleted memos stay out of the index.
            db.query("SELECT COUNT(*) FROM memo_tags WHERE memoId IN ('m2', 'm3')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            // Slash ancestors and multilingual tags are indexed per account.
            db.query(
                "SELECT tag FROM memo_tags WHERE accountKey = 'other' AND memoId = 'm4' ORDER BY tag"
            ).use { cursor ->
                val tags = mutableListOf<String>()
                while (cursor.moveToNext()) tags.add(cursor.getString(0))
                assertEquals(listOf("408", "408/计网", "数学"), tags)
            }
        }
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
