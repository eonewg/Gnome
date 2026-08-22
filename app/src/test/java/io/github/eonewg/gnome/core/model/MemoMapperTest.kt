package io.github.eonewg.gnome.core.model

import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.Memo as RemoteMemo
import io.github.eonewg.gnome.data.model.MemoVisibility as DataVisibility
import io.github.eonewg.gnome.data.model.Resource as RemoteResource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoMapperTest {

    private val t0 = Instant.ofEpochMilli(1_000)
    private val t1 = Instant.ofEpochMilli(2_000)

    private fun entity(
        needsSync: Boolean = false,
        isDeleted: Boolean = false,
        remoteId: String? = "r1",
    ) = MemoEntity(
        identifier = "L1",
        remoteId = remoteId,
        accountKey = "acc",
        content = "hello",
        date = t0,
        visibility = DataVisibility.PROTECTED,
        pinned = true,
        archived = false,
        needsSync = needsSync,
        isDeleted = isDeleted,
        lastModified = t1,
        lastSyncedAt = if (needsSync || isDeleted) null else t1,
    )

    private val resource = ResourceEntity(
        identifier = "res-1",
        remoteId = "att-1",
        accountKey = "acc",
        date = t0,
        filename = "f.png",
        uri = "https://example.com/att-1",
        localUri = "file:///cache/f.png",
        mimeType = "image/png",
        memoId = "L1",
    )

    @Test
    fun `entity to domain round trip preserves all fields`() {
        val domain = entity().toDomain(listOf(resource))

        assertEquals("L1", domain.id)
        assertEquals("r1", domain.remoteId)
        assertEquals("hello", domain.content)
        assertEquals(MemoVisibility.PROTECTED, domain.visibility)
        assertEquals(true, domain.pinned)
        assertEquals(SyncState.SYNCED, domain.syncState)
        assertEquals(t1, domain.lastSyncedAt)

        val back = domain.toEntity("acc").also { it.resources = emptyList() }
        assertEquals(entity(), back)

        assertEquals(listOf(resource), listOf(domain.attachments.single().toEntity("acc")))
    }

    @Test
    fun `sync state derivation covers all room flag combinations`() {
        assertEquals(SyncState.SYNCED, entity().syncState())
        assertEquals(SyncState.PENDING_CREATE, entity(needsSync = true, remoteId = null).syncState())
        assertEquals(SyncState.PENDING_UPDATE, entity(needsSync = true).syncState())
        assertEquals(SyncState.PENDING_DELETE, entity(needsSync = true, isDeleted = true).syncState())
    }

    @Test
    fun `pending delete round trips through the entity tombstone`() {
        val domain = entity(needsSync = true, isDeleted = true).toDomain()
        assertEquals(SyncState.PENDING_DELETE, domain.syncState)

        val back = domain.toEntity("acc")
        assertEquals(true, back.isDeleted)
        assertEquals(true, back.needsSync)
    }

    @Test
    fun `remote snapshot maps to a synced domain memo`() {
        val snapshot = RemoteMemo(
            remoteId = "r9",
            content = "from server",
            date = t0,
            pinned = false,
            visibility = DataVisibility.PUBLIC,
            resources = listOf(
                RemoteResource(
                    remoteId = "att-9",
                    date = t0,
                    filename = "a.jpg",
                    uri = "https://example.com/att-9",
                )
            ),
            tags = emptyList(),
            archived = true,
            updatedAt = t1,
        )

        val domain = snapshot.toDomain(localIdentifier = "L9")

        assertEquals("L9", domain.id)
        assertEquals("r9", domain.remoteId)
        assertEquals(MemoVisibility.PUBLIC, domain.visibility)
        assertEquals(true, domain.archived)
        assertEquals(SyncState.SYNCED, domain.syncState)
        assertEquals(t1, domain.lastSyncedAt)
        assertEquals("att-9", domain.attachments.single().remoteId)
    }

    @Test
    fun `visibility bridge covers every value both ways`() {
        DataVisibility.entries.forEach { data ->
            assertEquals(data, data.toCore().toData())
        }
    }
}
