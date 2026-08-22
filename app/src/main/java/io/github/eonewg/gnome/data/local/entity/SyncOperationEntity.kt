package io.github.eonewg.gnome.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class SyncEntityType {
    MEMO,
    ATTACHMENT,
}

enum class SyncOperationType {
    UPSERT,
    DELETE,
}

/**
 * Durable outbox row describing the next synchronization step for one entity.
 *
 * Written in the same Room transaction as the entity mutation it accompanies, so a
 * process death between "memo saved" and "server contacted" can never lose the
 * pending operation. Consumed by SyncEngine.processOutbox.
 *
 * `entityId` is the local identifier; [payload] carries extra data the operation
 * needs after the local row is gone (e.g. the remote id for DELETE_ATTACHMENT).
 */
@Entity(
    tableName = "sync_operations",
    indices = [
        Index(
            value = ["accountKey", "entityType", "entityId", "operation"],
            unique = true
        ),
        Index(value = ["accountKey"])
    ]
)
data class SyncOperationEntity(
    @PrimaryKey
    val id: String,
    val accountKey: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: SyncOperationType,
    val payload: String? = null,
    val createdAt: Instant,
    val attemptCount: Int = 0,
    val lastAttemptAt: Instant? = null,
    val lastError: String? = null,
)
