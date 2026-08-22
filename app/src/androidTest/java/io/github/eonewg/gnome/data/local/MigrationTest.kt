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

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
