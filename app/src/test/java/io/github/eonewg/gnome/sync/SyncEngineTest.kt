package io.github.eonewg.gnome.sync

import com.skydoves.sandwich.ApiResponse
import io.github.eonewg.gnome.data.local.dao.MemoDao
import io.github.eonewg.gnome.data.local.dao.SyncOperationDao
import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.MemoWithResources
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.local.entity.SyncEntityType
import io.github.eonewg.gnome.data.local.entity.SyncOperationEntity
import io.github.eonewg.gnome.data.local.entity.SyncOperationType
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.data.model.Memo
import io.github.eonewg.gnome.data.model.MemoVisibility
import io.github.eonewg.gnome.data.model.Resource
import io.github.eonewg.gnome.data.model.User
import io.github.eonewg.gnome.data.repository.RemoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/**
 * JVM tests for the reconcile algorithm using in-memory fakes. Covers the core
 * offline-write lifecycles the refactor must not regress: create/edit/delete
 * offline, conflict duplication, remote deletions, and outbox draining.
 */
class SyncEngineTest {

    private val t0 = Instant.ofEpochMilli(1_000)
    private val t1 = Instant.ofEpochMilli(2_000)
    private val t2 = Instant.ofEpochMilli(3_000)

    private val memoDao = FakeMemoDao()
    private val operationDao = FakeSyncOperationDao()
    private val remote = FakeRemoteRepository()
    private var syncedUser: User? = null

    private val engine = SyncEngine(
        memoDao = memoDao,
        syncOperationDao = operationDao,
        fileStore = SyncFileStore { },
        remoteRepository = remote,
        account = Account.Local(),
        transactionRunner = object : TransactionRunner {
            override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
        },
        onUserSynced = { syncedUser = it },
    )

    private fun outbox(
        entityId: String,
        type: SyncEntityType = SyncEntityType.MEMO,
        operation: SyncOperationType = SyncOperationType.UPSERT,
        payload: String? = null,
    ) = SyncOperationEntity(
        id = UUID.randomUUID().toString(),
        accountKey = "local",
        entityType = type,
        entityId = entityId,
        operation = operation,
        payload = payload,
        createdAt = Instant.now(),
    )

    @Test
    fun `offline create is pushed and marked synced`() = runBlocking {
        memoDao.insertMemo(localMemo(identifier = "L1", content = "hello", needsSync = true))
        operationDao.enqueue(outbox("L1"))

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        assertEquals(listOf("hello"), remote.createdContents)
        val synced = memoDao.memos.getValue("L1")
        assertTrue(!synced.needsSync)
        assertTrue(synced.remoteId != null)
        assertEquals(synced.remoteId!!.let { id -> remote.memos.getValue(id).updatedAt }, synced.lastSyncedAt)
        assertTrue(operationDao.operations.isEmpty())
        assertEquals("u1", syncedUser?.identifier)
    }

    @Test
    fun `offline edit pushes update when remote is unchanged`() = runBlocking {
        remote.memos["r1"] = remoteMemo("r1", "original", updatedAt = t1)
        memoDao.insertMemo(
            localMemo(
                identifier = "L1",
                remoteId = "r1",
                content = "edited offline",
                needsSync = true,
                lastSyncedAt = t1,
            )
        )
        operationDao.enqueue(outbox("L1"))

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        assertEquals(listOf("r1" to "edited offline"), remote.updatedContents)
        val synced = memoDao.memos.getValue("L1")
        assertEquals("edited offline", synced.content)
        assertTrue(!synced.needsSync)
        assertTrue(operationDao.operations.isEmpty())
    }

    @Test
    fun `both changed keeps server version and duplicates local edit`() = runBlocking {
        remote.memos["r1"] = remoteMemo("r1", "server edit", updatedAt = t2)
        memoDao.insertMemo(
            localMemo(
                identifier = "L1",
                remoteId = "r1",
                content = "local edit",
                needsSync = true,
                lastSyncedAt = t1,
            )
        )

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        val original = memoDao.memos.getValue("L1")
        assertEquals("server edit", original.content)
        assertTrue(!original.needsSync)

        val duplicate = memoDao.memos.values.singleOrNull { it.identifier != "L1" }
        assertTrue("duplicate row must exist", duplicate != null)
        assertEquals("local edit", duplicate!!.content)
        assertTrue(duplicate.remoteId != null)
        assertEquals(listOf("local edit"), remote.createdContents)
    }

    @Test
    fun `offline delete pushes remote delete and purges local row`() = runBlocking {
        remote.memos["r1"] = remoteMemo("r1", "to be deleted", updatedAt = t1)
        memoDao.insertMemo(
            localMemo(
                identifier = "L1",
                remoteId = "r1",
                content = "to be deleted",
                needsSync = true,
                isDeleted = true,
                lastSyncedAt = t1,
            )
        )
        operationDao.enqueue(outbox("L1", operation = SyncOperationType.DELETE))

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        assertEquals(listOf("r1"), remote.deletedMemoIds)
        assertNull(memoDao.memos["L1"])
        assertTrue(operationDao.operations.isEmpty())
    }

    @Test
    fun `remote deleted purges clean local row`() = runBlocking {
        memoDao.insertMemo(
            localMemo(
                identifier = "L1",
                remoteId = "r1",
                content = "gone on server",
                needsSync = false,
                lastSyncedAt = t1,
            )
        )

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        assertNull(memoDao.memos["L1"])
    }

    @Test
    fun `attachment delete op survives failure and drains on retry`() = runBlocking {
        remote.memos // empty server
        operationDao.enqueue(outbox("res-1", type = SyncEntityType.ATTACHMENT, operation = SyncOperationType.DELETE, payload = "att-9"))
        remote.failingOperations.add("deleteResource")

        var result = engine.reconcile()
        assertTrue(result is ApiResponse.Failure.Exception)
        val kept = operationDao.operations.values.single()
        assertEquals(1, kept.attemptCount)
        assertTrue(kept.lastError != null)
        assertEquals("att-9", kept.payload)

        remote.failingOperations.clear()
        result = engine.reconcile()
        assertTrue(result is ApiResponse.Success)
        assertEquals(listOf("att-9"), remote.deletedResourceIds)
        assertTrue(operationDao.operations.isEmpty())
    }

    @Test
    fun `memo upsert op is a no-op when the row is already clean`() = runBlocking {
        remote.memos["r1"] = remoteMemo("r1", "settled", updatedAt = t1)
        memoDao.insertMemo(
            localMemo(
                identifier = "L1",
                remoteId = "r1",
                content = "settled",
                needsSync = false,
                lastSyncedAt = t1,
            )
        )
        operationDao.enqueue(outbox("L1"))

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        assertTrue(remote.createdContents.isEmpty())
        assertTrue(operationDao.operations.isEmpty())
    }

    @Test
    fun `delete op for a resurrected memo is dropped`() = runBlocking {
        remote.memos["r1"] = remoteMemo("r1", "alive", updatedAt = t1)
        memoDao.insertMemo(
            localMemo(
                identifier = "L1",
                remoteId = "r1",
                content = "alive",
                needsSync = false,
                lastSyncedAt = t1,
            )
        )
        operationDao.enqueue(outbox("L1", operation = SyncOperationType.DELETE))

        val result = engine.reconcile()

        assertTrue(result is ApiResponse.Success)
        assertTrue(remote.deletedMemoIds.isEmpty())
        assertTrue(operationDao.operations.isEmpty())
    }

    private fun localMemo(
        identifier: String,
        remoteId: String? = null,
        content: String,
        needsSync: Boolean,
        isDeleted: Boolean = false,
        lastSyncedAt: Instant? = null,
    ) = MemoEntity(
        identifier = identifier,
        remoteId = remoteId,
        accountKey = "local",
        content = content,
        date = t0,
        visibility = MemoVisibility.PRIVATE,
        pinned = false,
        archived = false,
        needsSync = needsSync,
        isDeleted = isDeleted,
        lastModified = t0,
        lastSyncedAt = lastSyncedAt,
    )

    private fun remoteMemo(remoteId: String, content: String, updatedAt: Instant) = Memo(
        remoteId = remoteId,
        content = content,
        date = updatedAt,
        pinned = false,
        visibility = MemoVisibility.PRIVATE,
        resources = emptyList(),
        tags = emptyList(),
        updatedAt = updatedAt,
    )
}

class FakeMemoDao : MemoDao {
    val memos = linkedMapOf<String, MemoEntity>()
    val resources = linkedMapOf<String, ResourceEntity>()

    override suspend fun getArchivedMemos(accountKey: String): List<MemoEntity> =
        memos.values.filter { it.accountKey == accountKey && it.archived }

    override suspend fun getAllMemos(accountKey: String): List<MemoEntity> =
        memos.values.filter { it.accountKey == accountKey && !it.archived && !it.isDeleted }
            .sortedWith(compareByDescending<MemoEntity> { it.pinned }.thenByDescending { it.date })

    override fun observeAllMemos(accountKey: String): Flow<List<MemoWithResources>> =
        flowOf(emptyList())

    override suspend fun getAllMemosForSync(accountKey: String): List<MemoEntity> =
        memos.values.filter { it.accountKey == accountKey }

    override suspend fun countUnsyncedMemos(accountKey: String): Int =
        memos.values.count { it.accountKey == accountKey && it.needsSync }

    override suspend fun getMemoById(identifier: String, accountKey: String): MemoEntity? =
        memos[identifier]?.takeIf { it.accountKey == accountKey }

    override suspend fun getMemoByRemoteId(remoteId: String, accountKey: String): MemoEntity? =
        memos.values.firstOrNull { it.remoteId == remoteId && it.accountKey == accountKey }

    override suspend fun insertMemo(memo: MemoEntity) {
        memos[memo.identifier] = memo
    }

    override suspend fun deleteMemo(memo: MemoEntity) {
        memos.remove(memo.identifier)
    }

    override suspend fun getMemoResources(memoId: String, accountKey: String): List<ResourceEntity> =
        resources.values.filter { it.memoId == memoId && it.accountKey == accountKey }

    override suspend fun insertResource(resource: ResourceEntity) {
        resources[resource.identifier] = resource
    }

    override suspend fun deleteResource(resource: ResourceEntity) {
        resources.remove(resource.identifier)
    }

    override suspend fun getAllResources(accountKey: String): List<ResourceEntity> =
        resources.values.filter { it.accountKey == accountKey }

    override suspend fun getResourceById(identifier: String, accountKey: String): ResourceEntity? =
        resources[identifier]?.takeIf { it.accountKey == accountKey }

    override suspend fun getResourceByRemoteId(remoteId: String, accountKey: String): ResourceEntity? =
        resources.values.firstOrNull { it.remoteId == remoteId && it.accountKey == accountKey }

    override suspend fun deleteResourcesByAccount(accountKey: String) {
        resources.entries.removeIf { it.value.accountKey == accountKey }
    }

    override suspend fun deleteMemosByAccount(accountKey: String) {
        memos.entries.removeIf { it.value.accountKey == accountKey }
    }
}

class FakeSyncOperationDao : SyncOperationDao {
    val operations = linkedMapOf<String, SyncOperationEntity>()

    override suspend fun enqueue(operation: SyncOperationEntity) {
        operations.entries.removeIf {
            it.value.accountKey == operation.accountKey &&
                it.value.entityType == operation.entityType &&
                it.value.entityId == operation.entityId &&
                it.value.operation == operation.operation
        }
        operations[operation.id] = operation
    }

    override suspend fun getOperations(accountKey: String): List<SyncOperationEntity> =
        operations.values.filter { it.accountKey == accountKey }.sortedBy { it.createdAt }

    override fun observeOperations(accountKey: String): Flow<List<SyncOperationEntity>> =
        flowOf(emptyList())

    override suspend fun countForAccount(accountKey: String): Int =
        operations.values.count { it.accountKey == accountKey }

    override suspend fun update(operation: SyncOperationEntity) {
        operations[operation.id] = operation
    }

    override suspend fun delete(id: String) {
        operations.remove(id)
    }

    override suspend fun deleteAllForAccount(accountKey: String) {
        operations.entries.removeIf { it.value.accountKey == accountKey }
    }
}

class FakeRemoteRepository : RemoteRepository() {
    val memos = linkedMapOf<String, Memo>()
    val createdContents = mutableListOf<String>()
    val updatedContents = mutableListOf<Pair<String, String?>>()
    val deletedMemoIds = mutableListOf<String>()
    val deletedResourceIds = mutableListOf<String>()
    val failingOperations = mutableSetOf<String>()

    private var nextId = 0

    private fun shouldFail(name: String): Boolean = name in failingOperations

    override suspend fun listMemos(): ApiResponse<List<Memo>> =
        ApiResponse.Success(memos.values.filter { !it.archived }.map { it.copy() })

    override suspend fun listArchivedMemos(): ApiResponse<List<Memo>> =
        ApiResponse.Success(memos.values.filter { it.archived }.map { it.copy() })

    override suspend fun listWorkspaceMemos(pageSize: Int, pageToken: String?): ApiResponse<Pair<List<Memo>, String?>> =
        ApiResponse.Success(emptyList<Memo>() to null)

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        tags: List<String>?,
        createdAt: Instant?,
    ): ApiResponse<Memo> {
        if (shouldFail("createMemo")) {
            return ApiResponse.Failure.Exception(IOException("network down"))
        }
        val remoteId = "r-${nextId++}"
        val stamp = createdAt ?: Instant.now()
        val memo = Memo(
            remoteId = remoteId,
            content = content,
            date = stamp,
            pinned = false,
            visibility = visibility,
            resources = resourceRemoteIds.map { remoteResource(it) },
            tags = emptyList(),
            updatedAt = stamp,
        )
        memos[remoteId] = memo
        createdContents.add(content)
        return ApiResponse.Success(memo)
    }

    override suspend fun updateMemo(
        remoteId: String,
        content: String?,
        resourceRemoteIds: List<String>?,
        visibility: MemoVisibility?,
        tags: List<String>?,
        pinned: Boolean?,
        archived: Boolean?,
    ): ApiResponse<Memo> {
        if (shouldFail("updateMemo")) {
            return ApiResponse.Failure.Exception(IOException("network down"))
        }
        val existing = memos.getValue(remoteId)
        val updated = existing.copy(
            content = content ?: existing.content,
            visibility = visibility ?: existing.visibility,
            pinned = pinned ?: existing.pinned,
            archived = archived ?: existing.archived,
            resources = resourceRemoteIds?.map { remoteResource(it) } ?: existing.resources,
            updatedAt = Instant.now(),
        )
        memos[remoteId] = updated
        updatedContents.add(remoteId to content)
        return ApiResponse.Success(updated)
    }

    override suspend fun deleteMemo(remoteId: String): ApiResponse<Unit> {
        if (shouldFail("deleteMemo")) {
            return ApiResponse.Failure.Exception(IOException("network down"))
        }
        memos.remove(remoteId)
        deletedMemoIds.add(remoteId)
        return ApiResponse.Success(Unit)
    }

    override suspend fun listTags(): ApiResponse<List<String>> = ApiResponse.Success(emptyList())

    override suspend fun listResources(): ApiResponse<List<Resource>> = ApiResponse.Success(emptyList())

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String?,
    ): ApiResponse<Resource> {
        if (shouldFail("createResource")) {
            return ApiResponse.Failure.Exception(IOException("network down"))
        }
        return ApiResponse.Success(remoteResource("att-${nextId++}"))
    }

    override suspend fun deleteResource(remoteId: String): ApiResponse<Unit> {
        if (shouldFail("deleteResource")) {
            return ApiResponse.Failure.Exception(IOException("network down"))
        }
        deletedResourceIds.add(remoteId)
        return ApiResponse.Success(Unit)
    }

    override suspend fun getCurrentUser(): ApiResponse<User> =
        ApiResponse.Success(User(identifier = "u1", name = "Tester"))

    private fun remoteResource(remoteId: String) = Resource(
        remoteId = remoteId,
        date = Instant.now(),
        filename = "file",
        uri = "https://example.com/$remoteId",
    )
}
