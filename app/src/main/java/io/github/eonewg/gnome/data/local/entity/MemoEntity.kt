package io.github.eonewg.gnome.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.eonewg.gnome.data.model.MemoVisibility
import java.time.Instant

@Entity(
    tableName = "memos",
    indices = [
        Index(value = ["accountKey"]),
        Index(value = ["accountKey", "remoteId"])
    ]
)
data class MemoEntity(
    @PrimaryKey
    val identifier: String,
    val remoteId: String? = null,
    val accountKey: String,
    val content: String,
    val date: Instant,
    val visibility: MemoVisibility,
    val pinned: Boolean,
    val archived: Boolean = false,
    val needsSync: Boolean = true,
    val isDeleted: Boolean = false,
    val lastModified: Instant = Instant.now(),
    val lastSyncedAt: Instant? = null
) {
    @Ignore
    var resources: List<ResourceEntity> = emptyList()
}
