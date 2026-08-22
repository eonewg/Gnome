package io.github.eonewg.gnome.sync

import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.Memo
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.data.model.Resource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ConflictResolverTest {

    private val state = ConflictResolver.LocalMemoState(isDeleted = false, needsSync = false)

    @Test
    fun `no local row applies remote`() {
        assertEquals(
            ConflictResolver.MemoDecision.APPLY_REMOTE,
            ConflictResolver.resolve(null, equivalent = false, remoteChanged = false)
        )
    }

    @Test
    fun `local deleted and already synced resurrects from remote`() {
        val deleted = state.copy(isDeleted = true, needsSync = false)
        assertEquals(
            ConflictResolver.MemoDecision.APPLY_REMOTE,
            ConflictResolver.resolve(deleted, equivalent = false, remoteChanged = false)
        )
    }

    @Test
    fun `local deleted pending and remote unchanged pushes delete`() {
        val deleted = state.copy(isDeleted = true, needsSync = true)
        assertEquals(
            ConflictResolver.MemoDecision.DELETE_REMOTE,
            ConflictResolver.resolve(deleted, equivalent = true, remoteChanged = false)
        )
    }

    @Test
    fun `local deleted pending but remote changed keeps remote`() {
        val deleted = state.copy(isDeleted = true, needsSync = true)
        assertEquals(
            ConflictResolver.MemoDecision.APPLY_REMOTE,
            ConflictResolver.resolve(deleted, equivalent = false, remoteChanged = true)
        )
    }

    @Test
    fun `equivalent content marks synced`() {
        assertEquals(
            ConflictResolver.MemoDecision.MARK_SYNCED,
            ConflictResolver.resolve(state, equivalent = true, remoteChanged = false)
        )
        assertEquals(
            ConflictResolver.MemoDecision.MARK_SYNCED,
            ConflictResolver.resolve(state.copy(needsSync = true), equivalent = true, remoteChanged = false)
        )
    }

    @Test
    fun `only remote changed applies remote`() {
        assertEquals(
            ConflictResolver.MemoDecision.APPLY_REMOTE,
            ConflictResolver.resolve(state, equivalent = false, remoteChanged = true)
        )
    }

    @Test
    fun `only local changed pushes local`() {
        assertEquals(
            ConflictResolver.MemoDecision.PUSH_LOCAL,
            ConflictResolver.resolve(state.copy(needsSync = true), equivalent = false, remoteChanged = false)
        )
    }

    @Test
    fun `both changed duplicates local edit`() {
        assertEquals(
            ConflictResolver.MemoDecision.DUPLICATE,
            ConflictResolver.resolve(state.copy(needsSync = true), equivalent = false, remoteChanged = true)
        )
    }

    @Test
    fun `never synced local row counts as remote changed`() {
        val remote = remoteMemo(content = "hello", updatedAt = Instant.ofEpochMilli(1_000))
        assertEquals(true, ConflictResolver.hasRemoteChanged(lastSyncedAt = null, remote = remote))
        assertEquals(
            false,
            ConflictResolver.hasRemoteChanged(lastSyncedAt = Instant.ofEpochMilli(1_000), remote = remote)
        )
        assertEquals(
            true,
            ConflictResolver.hasRemoteChanged(lastSyncedAt = Instant.ofEpochMilli(999), remote = remote)
        )
    }

    @Test
    fun `equivalence compares content flags and resource signatures`() {
        val now = Instant.now()
        val local = MemoEntity(
            identifier = "l1",
            remoteId = "r1",
            accountKey = "local",
            content = "hello",
            date = now,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            archived = false,
        )
        val base = remoteMemo(content = "hello", updatedAt = now)

        assertEquals(true, ConflictResolver.memoEquivalent(local, base, localResources = emptyList()))

        assertEquals(
            false,
            ConflictResolver.memoEquivalent(local.copy(content = "hello!"), base, emptyList())
        )
        assertEquals(
            false,
            ConflictResolver.memoEquivalent(local, base.copy(pinned = true), emptyList())
        )
        assertEquals(
            false,
            ConflictResolver.memoEquivalent(local, base.copy(archived = true), emptyList())
        )

        val remoteWithResource = base.copy(resources = listOf(resource("r-a")))
        assertEquals(
            false,
            ConflictResolver.memoEquivalent(local, remoteWithResource, localResources = emptyList())
        )
        assertEquals(
            true,
            ConflictResolver.memoEquivalent(
                local,
                remoteWithResource,
                localResources = listOf(resourceEntity(remoteId = "r-a"))
            )
        )
    }
}

private fun remoteMemo(content: String, updatedAt: Instant) = Memo(
    remoteId = "r1",
    content = content,
    date = updatedAt,
    pinned = false,
    visibility = MemoVisibility.PRIVATE,
    resources = emptyList(),
    tags = emptyList(),
    updatedAt = updatedAt,
)

private fun resource(remoteId: String) = Resource(
    remoteId = remoteId,
    date = Instant.now(),
    filename = "f",
    uri = "https://example.com/$remoteId",
)

private fun resourceEntity(remoteId: String) = ResourceEntity(
    identifier = "local-$remoteId",
    remoteId = remoteId,
    accountKey = "local",
    date = Instant.now(),
    filename = "f",
    uri = "https://example.com/$remoteId",
    mimeType = null,
)
