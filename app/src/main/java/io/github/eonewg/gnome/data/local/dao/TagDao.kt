package io.github.eonewg.gnome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.MemoTagEntity
import io.github.eonewg.gnome.data.model.TagUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<MemoTagEntity>)

    @Query("SELECT * FROM memo_tags WHERE accountKey = :accountKey AND memoId = :memoId")
    suspend fun getTagsForMemo(accountKey: String, memoId: String): List<MemoTagEntity>

    /**
     * Tag vocabulary with usage counts, restricted to live timeline memos,
     * ordered by frequency descending then tag ascending.
     */
    @Query("""
        SELECT t.tag AS tag, COUNT(*) AS usageCount
        FROM memo_tags t
        INNER JOIN memos m ON m.identifier = t.memoId AND m.accountKey = t.accountKey
        WHERE t.accountKey = :accountKey AND m.isDeleted = 0 AND m.archived = 0
        GROUP BY t.tag
        ORDER BY usageCount DESC, tag ASC
    """)
    fun observeTags(accountKey: String): Flow<List<TagUsage>>

    /**
     * Prefix-matched suggestions (ESCAPE-protected) with the same frequency
     * ordering, bounded for keyboard-size dropdowns.
     */
    @Query("""
        SELECT t.tag AS tag, COUNT(*) AS usageCount
        FROM memo_tags t
        INNER JOIN memos m ON m.identifier = t.memoId AND m.accountKey = t.accountKey
        WHERE t.accountKey = :accountKey AND m.isDeleted = 0 AND m.archived = 0
            AND t.tag LIKE :prefix ESCAPE '\'
        GROUP BY t.tag
        ORDER BY usageCount DESC, tag ASC
        LIMIT 50
    """)
    suspend fun getTagSuggestions(accountKey: String, prefix: String): List<TagUsage>

    /** Timeline memos (live, non-archived) carrying the exact tag. */
    @Query("""
        SELECT m.* FROM memos m
        INNER JOIN memo_tags t ON t.memoId = m.identifier AND t.accountKey = m.accountKey
        WHERE m.accountKey = :accountKey AND t.tag = :tag AND m.isDeleted = 0 AND m.archived = 0
        ORDER BY m.pinned DESC, m.date DESC
    """)
    fun observeMemosByTag(accountKey: String, tag: String): Flow<List<MemoEntity>>

    @Query("DELETE FROM memo_tags WHERE accountKey = :accountKey AND memoId = :memoId")
    suspend fun deleteByMemo(accountKey: String, memoId: String)

    @Query("DELETE FROM memo_tags WHERE accountKey = :accountKey")
    suspend fun deleteByAccount(accountKey: String)
}
