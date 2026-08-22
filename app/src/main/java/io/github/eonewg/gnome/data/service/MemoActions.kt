package io.github.eonewg.gnome.data.service

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.skydoves.sandwich.ApiResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.eonewg.gnome.core.model.Memo
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.repository.MemoRepository
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single-memo mutations shared by the list screens. Each write goes through
 * the account-scoped [MemoRepository] so the local-first pipeline (Room +
 * outbox + background sync) stays identical to the legacy callers; the
 * screens' Room-backed flows re-emit on their own after every write.
 */
@Singleton
class MemoActions @Inject constructor(
    private val memoService: MemoService,
    private val accountService: AccountService,
    @param:ApplicationContext private val appContext: Context,
) {
    private suspend fun repository(): MemoRepository = memoService.getMemoRepository()

    /** Writes checkbox toggles, keeping the memo's attachments and visibility. */
    suspend fun updateContent(memoId: String, content: String): ApiResponse<Memo> {
        val current = repository().getMemo(memoId)
            ?: return ApiResponse.exception(IllegalStateException("Memo $memoId not found"))
        return repository().updateMemo(
            identifier = memoId,
            content = content,
            attachments = current.attachments,
            visibility = current.visibility,
        )
    }

    suspend fun updatePinned(memoId: String, pinned: Boolean): ApiResponse<Memo> =
        repository().updateMemo(memoId, pinned = pinned)

    suspend fun archive(memoId: String): ApiResponse<Unit> = repository().archiveMemo(memoId)

    suspend fun delete(memoId: String): ApiResponse<Unit> = repository().deleteMemo(memoId)

    suspend fun cacheResource(resourceId: String, uri: Uri): ApiResponse<Unit> =
        repository().cacheAttachmentFile(resourceId, uri)

    /**
     * Downloads an HTTP attachment into the app cache and persists it as the
     * resource's canonical local copy; returns its [android.net.Uri], or null
     * when the download or the persist fails. Implements the attachment chip's
     * download-and-cache callback for every memo list.
     */
    suspend fun downloadAndCache(resource: ResourceEntity): Uri? {
        val downloaded = downloadAttachmentToCache(
            context = appContext,
            okHttpClient = accountService.httpClient,
            url = resource.uri,
            filename = resource.filename,
        ) ?: return null
        val response = repository().cacheAttachmentFile(resource.identifier, Uri.fromFile(downloaded))
        downloaded.delete()
        if (response !is ApiResponse.Success) {
            return null
        }
        val localUri = repository().getAttachment(resource.identifier)?.localUri ?: return null
        return localUri.toUri()
    }
}

/**
 * Downloads an attachment into the cache directory. Shared by [MemoActions]
 * (via the attachment chip's download-and-cache callback).
 */
internal suspend fun downloadAttachmentToCache(
    context: Context,
    okHttpClient: OkHttpClient,
    url: String,
    filename: String,
): File? = withContext(Dispatchers.IO) {
    val request = okhttp3.Request.Builder().url(url).get().build()
    okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            return@withContext null
        }
        val body = response.body
        val dir = File(context.cacheDir, "image_cache").also { it.mkdirs() }
        val suffix = "_${sanitizeFilename(filename.ifBlank { "attachment" })}"
        val target = File.createTempFile("attachment_", suffix, dir)
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        target
    }
}

private fun sanitizeFilename(filename: String): String {
    return filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
}