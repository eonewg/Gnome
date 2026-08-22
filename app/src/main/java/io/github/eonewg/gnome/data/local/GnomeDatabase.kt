package io.github.eonewg.gnome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.eonewg.gnome.data.local.dao.MemoDao
import io.github.eonewg.gnome.data.local.dao.SyncOperationDao
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.local.entity.SyncOperationEntity

@Database(
    entities = [MemoEntity::class, ResourceEntity::class, SyncOperationEntity::class],
    version = 2
)
@TypeConverters(Converters::class)
abstract class GnomeDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun syncOperationDao(): SyncOperationDao

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

        private val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

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
