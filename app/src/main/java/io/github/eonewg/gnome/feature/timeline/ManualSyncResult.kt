package io.github.eonewg.gnome.feature.timeline

/** Outcome of a manual timeline sync; version problems map to [TimelineSyncAlert]. */
sealed class ManualSyncResult {
    object Completed : ManualSyncResult()
    data class Blocked(val message: String) : ManualSyncResult()
    data class RequiresConfirmation(val version: String, val message: String) : ManualSyncResult()
    data class Failed(val message: String) : ManualSyncResult()
}