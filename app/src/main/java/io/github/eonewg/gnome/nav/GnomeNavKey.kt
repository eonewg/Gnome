package io.github.eonewg.gnome.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Typed navigation keys for the single Gnome back stack.
 *
 * Only primitive/id payloads are allowed: a destination always reads its data
 * from the repository, never from the key. Dates travel as ISO strings so the
 * key stays serializable without extra modules; the destination parses them.
 */
@Serializable
sealed interface GnomeNavKey : NavKey

@Serializable
data object TimelineKey : GnomeNavKey

@Serializable
data object ArchivedKey : GnomeNavKey

@Serializable
data object ExploreKey : GnomeNavKey

@Serializable
data object SearchKey : GnomeNavKey

@Serializable
data object StatsKey : GnomeNavKey

@Serializable
data object StatsDetailKey : GnomeNavKey

@Serializable
data object ResourcesKey : GnomeNavKey

@Serializable
data object SettingsKey : GnomeNavKey

@Serializable
data object AddAccountKey : GnomeNavKey

@Serializable
data object LoginKey : GnomeNavKey

@Serializable
data class AccountKey(val accountKey: String) : GnomeNavKey

@Serializable
data class EditorKey(val memoId: String? = null) : GnomeNavKey

@Serializable
data class MemoDetailKey(val memoId: String) : GnomeNavKey

@Serializable
data class TagKey(val tag: String) : GnomeNavKey

@Serializable
data class DateKey(val date: String) : GnomeNavKey

@Serializable
data object ShareKey : GnomeNavKey

/** Pages that live inside the navigation drawer; others are covered by it. */
fun isDrawerScoped(key: NavKey?): Boolean = when (key) {
    is TimelineKey,
    is ArchivedKey,
    is ExploreKey,
    is ResourcesKey,
    is SettingsKey,
    is SearchKey,
    is TagKey,
    is DateKey,
    is MemoDetailKey,
    is EditorKey,
    -> true
    else -> false
}

/** Drawer destinations that replace the current root instead of pushing a child page. */
fun isDrawerSwitchDestination(key: NavKey?): Boolean = when (key) {
    is TimelineKey,
    is ExploreKey,
    is ResourcesKey,
    is ArchivedKey,
    is SettingsKey,
    is TagKey,
    -> true
    else -> false
}
