package io.github.eonewg.gnome.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.MemoWithResources
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import java.time.Instant

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE accountKey = :accountKey AND archived = 1 ORDER BY date DESC")
    suspend fun getArchivedMemos(accountKey: String): List<MemoEntity>

    @Query("""
        SELECT * FROM memos 
        WHERE accountKey = :accountKey AND archived = 0 AND isDeleted = 0
        ORDER BY pinned DESC, date DESC
    """)
    suspend fun getAllMemos(accountKey: String): List<MemoEntity>

    @Transaction
    @Query("""
        SELECT * FROM memos
        WHERE accountKey = :accountKey AND archived = 0 AND isDeleted = 0
        ORDER BY pinned DESC, date DESC
    """)
    fun observeAllMemos(accountKey: String): Flow<List<MemoWithResources>>

    @Query("SELECT * FROM memos WHERE accountKey = :accountKey")
    suspend fun getAllMemosForSync(accountKey: String): List<MemoEntity>

    @Query("SELECT COUNT(*) FROM memos WHERE accountKey = :accountKey AND needsSync = 1")
    suspend fun countUnsyncedMemos(accountKey: String): Int

    @Query("SELECT COUNT(*) FROM memos WHERE accountKey = :accountKey AND needsSync = 1")
    fun observeUnsyncedCount(accountKey: String): Flow<Int>

    /**
     * Content search over the local database: case-insensitive LIKE substring
     * (works for CJK without an FTS index), filtering by archived state, an
     * exact tag (indexed ancestors match too) and an optional date range.
     * The caller must LIKE-escape `%`/`_`/`\` in [escapedQuery].
     */
    @Transaction
    @Query("""
        SELECT * FROM memos
        WHERE accountKey = :accountKey AND isDeleted = 0
            AND (:includeArchived = 1 OR archived = 0)
            AND content LIKE '%' || :escapedQuery || '%' ESCAPE '\'
            AND (:tag IS NULL OR identifier IN (
                SELECT memoId FROM memo_tags
                WHERE accountKey = :accountKey AND tag = :tag
            ))
            AND (:dateFrom IS NULL OR date >= :dateFrom)
            AND (:dateTo IS NULL OR date < :dateTo)
        ORDER BY pinned DESC, date DESC
    """)
    fun observeSearchMemos(
        accountKey: String,
        escapedQuery: String,
        includeArchived: Boolean,
        tag: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
    ): Flow<List<MemoWithResources>>

    @Query("SELECT * FROM memos WHERE identifier = :identifier AND accountKey = :accountKey")
    suspend fun getMemoById(identifier: String, accountKey: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE remoteId = :remoteId AND accountKey = :accountKey")
    suspend fun getMemoByRemoteId(remoteId: String, accountKey: String): MemoEntity?

    @Upsert
    suspend fun insertMemo(memo: MemoEntity)

    @Delete
    suspend fun deleteMemo(memo: MemoEntity)

    @Query("SELECT * FROM resources WHERE memoId = :memoId AND accountKey = :accountKey")
    suspend fun getMemoResources(memoId: String, accountKey: String): List<ResourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: ResourceEntity)

    @Delete
    suspend fun deleteResource(resource: ResourceEntity)

    @Query("SELECT * FROM resources WHERE accountKey = :accountKey ORDER BY date DESC")
    suspend fun getAllResources(accountKey: String): List<ResourceEntity>

    @Query("SELECT * FROM resources WHERE identifier = :identifier AND accountKey = :accountKey")
    suspend fun getResourceById(identifier: String, accountKey: String): ResourceEntity?

    @Query("SELECT * FROM resources WHERE remoteId = :remoteId AND accountKey = :accountKey")
    suspend fun getResourceByRemoteId(remoteId: String, accountKey: String): ResourceEntity?

    @Query("DELETE FROM resources WHERE accountKey = :accountKey")
    suspend fun deleteResourcesByAccount(accountKey: String)

    @Query("DELETE FROM memos WHERE accountKey = :accountKey")
    suspend fun deleteMemosByAccount(accountKey: String)

}
