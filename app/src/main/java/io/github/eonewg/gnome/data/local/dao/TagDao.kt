package io.github.eonewg.gnome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.eonewg.gnome.data.local.entity.MemoTagEntity

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<MemoTagEntity>)

    @Query("SELECT * FROM memo_tags WHERE accountKey = :accountKey AND memoId = :memoId")
    suspend fun getTagsForMemo(accountKey: String, memoId: String): List<MemoTagEntity>

    @Query("DELETE FROM memo_tags WHERE accountKey = :accountKey AND memoId = :memoId")
    suspend fun deleteByMemo(accountKey: String, memoId: String)

    @Query("DELETE FROM memo_tags WHERE accountKey = :accountKey")
    suspend fun deleteByAccount(accountKey: String)
}
