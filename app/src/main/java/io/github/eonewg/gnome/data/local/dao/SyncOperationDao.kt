package io.github.eonewg.gnome.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.eonewg.gnome.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {
    /**
     * Re-enqueueing the same (account, entity, operation) replaces the pending row
     * instead of queueing a duplicate; consumers always act on the entity's latest
     * local state, so coalescing is safe.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: SyncOperationEntity)

    /**
     * `rowid` breaks same-millisecond ties in insertion order; REPLACE also
     * assigns a fresh rowid, so a coalesced re-enqueue moves to the back of
     * the queue, matching its renewed intent.
     */
    @Query("SELECT * FROM sync_operations WHERE accountKey = :accountKey ORDER BY createdAt ASC, rowid ASC")
    suspend fun getOperations(accountKey: String): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE accountKey = :accountKey ORDER BY createdAt ASC, rowid ASC")
    fun observeOperations(accountKey: String): Flow<List<SyncOperationEntity>>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE accountKey = :accountKey")
    suspend fun countForAccount(accountKey: String): Int

    @Update
    suspend fun update(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sync_operations WHERE accountKey = :accountKey")
    suspend fun deleteAllForAccount(accountKey: String)
}
