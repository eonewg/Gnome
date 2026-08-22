package io.github.eonewg.gnome.data.repository

import android.net.Uri
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import io.github.eonewg.gnome.core.model.Attachment
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.core.model.MemoVisibility
import io.github.eonewg.gnome.data.model.SyncStatus

/**
 * The feature-facing contract for memo data. New ViewModels and screens
 * depend on this interface only — never on Room entities, ResourceEntity,
 * the legacy data.model.Memo or MemoRepresentable.
 *
 * Writes follow the local-first pipeline: Room transaction (memo + outbox)
 * → success returned to the caller → background WorkManager sync for
 * remote accounts. Reads expose Room flows projected into domain models.
 *
 * [MemoRepositoryImpl] keeps the old [AbstractMemoRepository] surface as a
 * migration adapter for not-yet-migrated callers; it shrinks away as
 * features move onto this contract.
 */
interface MemoRepository {
    val accountKeyValue: String

    /** True for remote (syncing) accounts; local-only accounts just persist. */
    val syncEnabled: Boolean

    val syncStatus: StateFlow<SyncStatus>

    fun observeTimeline(): Flow<List<Memo>>

    suspend fun getMemo(identifier: String): Memo?

    suspend fun getAttachment(identifier: String): Attachment?

    suspend fun listArchived(): ApiResponse<List<Memo>>

    suspend fun listTags(): ApiResponse<List<String>>

    suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        attachments: List<Attachment> = emptyList(),
    ): ApiResponse<Memo>

    suspend fun updateMemo(
        identifier: String,
        content: String? = null,
        attachments: List<Attachment>? = null,
        visibility: MemoVisibility? = null,
        pinned: Boolean? = null,
    ): ApiResponse<Memo>

    suspend fun deleteMemo(identifier: String): ApiResponse<Unit>

    suspend fun archiveMemo(identifier: String): ApiResponse<Unit>

    suspend fun restoreMemo(identifier: String): ApiResponse<Unit>

    suspend fun createAttachment(
        filename: String,
        mimeType: String?,
        contentUri: Uri,
        memoIdentifier: String? = null,
    ): ApiResponse<Attachment>

    suspend fun deleteAttachment(identifier: String): ApiResponse<Unit>

    suspend fun cacheAttachmentFile(identifier: String, downloadedUri: Uri): ApiResponse<Unit>

    /** Runs a full reconcile for remote accounts; no-op for local accounts. */
    suspend fun sync(): ApiResponse<Unit>

    fun close()
}
