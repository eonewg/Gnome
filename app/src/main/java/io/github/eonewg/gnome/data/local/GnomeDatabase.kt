package io.github.eonewg.gnome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.eonewg.gnome.core.tag.MemosTagParser
import io.github.eonewg.gnome.data.local.dao.MemoDao
import io.github.eonewg.gnome.data.local.dao.SyncOperationDao
import io.github.eonewg.gnome.data.local.dao.TagDao
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.MemoTagEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.local.entity.SyncOperationEntity

@Database(
    entities = [MemoEntity::class, ResourceEntity::class, SyncOperationEntity::class, MemoTagEntity::class],
    version = 3
)
@TypeConverters(Converters::class)
abstract class GnomeDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun syncOperationDao(): SyncOperationDao
    abstract fun tagDao(): TagDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_operations` (
                        `id` TEXT NOT NULL,
                        `accountKey` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `payload` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER,
                        `lastError` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_sync_operations_accountKey_entityType_entityId_operation` " +
                        "ON `sync_operations` (`accountKey`, `entityType`, `entityId`, `operation`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_operations_accountKey` " +
                        "ON `sync_operations` (`accountKey`)"
                )
            }
        }

        /**
         * Creates the memo_tags index table and backfills it by parsing the
         * content of every live (non-deleted) memo with MemosTagParser, so the
         * tag index is complete the moment the upgrade finishes.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memo_tags` (
                        `accountKey` TEXT NOT NULL,
                        `memoId` TEXT NOT NULL,
                        `tag` TEXT NOT NULL,
                        PRIMARY KEY(`accountKey`, `memoId`, `tag`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memo_tags_accountKey_tag` " +
                        "ON `memo_tags` (`accountKey`, `tag`)"
                )

                val insert = db.compileStatement(
                    "INSERT OR REPLACE INTO `memo_tags` (`accountKey`, `memoId`, `tag`) VALUES (?, ?, ?)"
                )
                db.query(
                    "SELECT `identifier`, `accountKey`, `content` FROM `memos` WHERE `isDeleted` = 0"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val memoId = cursor.getString(0)
                        val accountKey = cursor.getString(1)
                        val content = cursor.getString(2) ?: continue
                        for (tag in MemosTagParser.extractTags(content)) {
                            insert.bindString(1, accountKey)
                            insert.bindString(2, memoId)
                            insert.bindString(3, tag)
                            insert.executeInsert()
                        }
                    }
                }
            }
        }

        private val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile
        private var INSTANCE: GnomeDatabase? = null

        fun getDatabase(context: Context): GnomeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GnomeDatabase::class.java,
                    "gnome.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
