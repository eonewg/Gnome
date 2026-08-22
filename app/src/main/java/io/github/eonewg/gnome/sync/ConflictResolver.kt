package io.github.eonewg.gnome.sync

import io.github.eonewg.gnome.data.local.entity.MemoEntity
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.model.Memo
import io.github.eonewg.gnome.data.model.Resource
import java.time.Instant

/**
 * Pure conflict rules for the full-snapshot reconcile.
 *
 * Product semantics (unchanged from the original SyncingRepository behaviour):
 * when both sides changed, the server version keeps the memo identity and the
 * local edit is duplicated as a new memo — user text is never silently dropped.
 */
object ConflictResolver {

    enum class MemoDecision {
        /** No local row, only the server changed, or a deleted local row that the server resurrected. */
        APPLY_REMOTE,

        /** Local and remote carry the same content; just refresh sync markers. */
        MARK_SYNCED,

        /** Only the local side changed; push it. */
        PUSH_LOCAL,

        /** Both sides changed; keep the server version and duplicate the local edit. */
        DUPLICATE,

        /** Local soft-delete with an unchanged server copy; delete it remotely. */
        DELETE_REMOTE,
    }

    data class LocalMemoState(
        val isDeleted: Boolean,
        val needsSync: Boolean,
    )

    fun resolve(local: LocalMemoState?, equivalent: Boolean, remoteChanged: Boolean): MemoDecision {
        if (local == null) {
            return MemoDecision.APPLY_REMOTE
        }
        if (local.isDeleted) {
            if (!local.needsSync) {
                return MemoDecision.APPLY_REMOTE
            }
            return if (remoteChanged || !equivalent) MemoDecision.APPLY_REMOTE else MemoDecision.DELETE_REMOTE
        }
        if (equivalent) {
            return MemoDecision.MARK_SYNCED
        }
        return when {
            !local.needsSync -> MemoDecision.APPLY_REMOTE
            !remoteChanged -> MemoDecision.PUSH_LOCAL
            else -> MemoDecision.DUPLICATE
        }
    }

    fun hasRemoteChanged(lastSyncedAt: Instant?, remote: Memo): Boolean {
        val remoteUpdatedAt = remote.updatedAt ?: remote.date
        lastSyncedAt ?: return true
        return remoteUpdatedAt != lastSyncedAt
    }

    fun memoEquivalent(local: MemoEntity, remote: Memo, localResources: List<ResourceEntity>): Boolean {
        if (local.content != remote.content) return false
        if (local.pinned != remote.pinned) return false
        if (local.visibility != remote.visibility) return false
        if (local.archived != remote.archived) return false
        return resourceEntitySignature(localResources) == resourceModelSignature(remote.resources)
    }

    private fun resourceEntitySignature(resources: List<ResourceEntity>): List<String> {
        return resources.map { resource ->
            resource.remoteId ?: "local:${resource.localUri ?: resource.uri}"
        }.sorted()
    }

    private fun resourceModelSignature(resources: List<Resource>): List<String> {
        return resources.map { resource -> resource.remoteId }.sorted()
    }
}
