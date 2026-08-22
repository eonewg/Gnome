package io.github.eonewg.gnome.sync

import com.skydoves.sandwich.ApiResponse
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.local.entity.SyncEntityType
import io.github.eonewg.gnome.data.local.entity.SyncOperationEntity
import io.github.eonewg.gnome.data.local.entity.SyncOperationType
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.MemoVisibility
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Attachment upload ordering, run under Robolectric because the engine parses
 * android.net.Uri when reading local files. The invariant: an attachment is
 * uploaded (and its remoteId persisted) before the memo push references it —
 * the server must never see a memo pointing at a nonexistent attachment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineAttachmentTest {

    private val memoDao = FakeMemoDao()
    private val operationDao = FakeSyncOperationDao()
    private val remote = FakeRemoteRepository()
    private val deletedFiles = mutableListOf<String>()

    private val engine = SyncEngine(
        memoDao = memoDao,
        syncOperationDao = operationDao,
        fileStore = SyncFileStore { deletedFiles.add(it.toString()) },
        remoteRepository = remote,
        account = Account.Local(),
        transactionRunner = object : TransactionRunner {
            override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
        },
    )

    @Test
    fun `attachment uploads before the memo and the memo references its remote id`() = runBlocking {
        val attachment = File.createTempFile("gnome-att", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        memoDao.insertMemo(
            MemoEntity(
                identifier = "L1",
                remoteId = null,
                accountKey = "local",
                content = "with attachment",
                date = Instant.ofEpochMilli(1_000),
                visibility = MemoVisibility.PRIVATE,
                pinned = false,
                archived = false,
                needsSync = true,
                isDeleted = false,
                lastModified = Instant.ofEpochMilli(1_000),
                lastSyncedAt = null,
            )
        )
        memoDao.insertResource(
            ResourceEntity(
                identifier = "res-1",
                remoteId = null,
                accountKey = "local",
                date = Instant.ofEpochMilli(1_000),
                filename = "photo.bin",
                uri = android.net.Uri.fromFile(attachment).toString(),
                localUri = android.net.Uri.fromFile(attachment).toString(),
                mimeType = "application/octet-stream",
                memoId = "L1",
            )
        )
        operationDao.enqueue(
            SyncOperationEntity(
                id = UUID.randomUUID().toString(),
                accountKey = "local",
                entityType = SyncEntityType.MEMO,
                entityId = "L1",
                operation = SyncOperationType.UPSERT,
                payload = null,
                createdAt = Instant.now(),
            )
        )

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)

        // Upload happened, exactly once, and the row now carries the remote id.
        val uploaded = memoDao.resources.getValue("res-1")
        assertTrue(uploaded.remoteId != null)

        // Ordering: resource upload precedes the memo create that references it.
        val mutations = remote.callLog
        val uploadIndex = mutations.indexOf("createResource")
        val memoIndex = mutations.indexOf("createMemo")
        assertTrue("createResource must be called", uploadIndex >= 0)
        assertTrue("createMemo must be called", memoIndex >= 0)
        assertTrue("expected createResource before createMemo", uploadIndex < memoIndex)

        // And the created memo references exactly the uploaded attachment.
        val createdRemoteId = memoDao.memos.getValue("L1").remoteId!!
        val created = remote.memos.getValue(createdRemoteId)
        assertEquals(listOf(uploaded.remoteId), created.resources.map { it.remoteId })

        assertTrue(operationDao.operations.isEmpty())
    }

    @Test
    fun `purging a memo with local files deletes them outside the engine`() = runBlocking {
        val attachment = File.createTempFile("gnome-att", ".bin").apply { writeBytes(byteArrayOf(1)) }
        memoDao.insertResource(
            ResourceEntity(
                identifier = "res-1",
                remoteId = "att-existing",
                accountKey = "local",
                date = Instant.ofEpochMilli(1_000),
                filename = "photo.bin",
                uri = "https://example.com/att-existing",
                localUri = android.net.Uri.fromFile(attachment).toString(),
                mimeType = null,
                memoId = "L1",
            )
        )
        // Server no longer has r1 and the local row is clean → purge path.
        memoDao.insertMemo(
            MemoEntity(
                identifier = "L1",
                remoteId = "r1",
                accountKey = "local",
                content = "gone",
                date = Instant.ofEpochMilli(1_000),
                visibility = MemoVisibility.PRIVATE,
                pinned = false,
                archived = false,
                needsSync = false,
                isDeleted = false,
                lastModified = Instant.ofEpochMilli(1_000),
                lastSyncedAt = Instant.ofEpochMilli(1_000),
            )
        )

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        assertEquals(listOf(android.net.Uri.fromFile(attachment).toString()), deletedFiles)
    }
}
