package io.github.eonewg.gnome.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Back-stack semantics of the single [GnomeNavigator]. These are the exact
 * operations the Navigation 2 graph used to express with string routes,
 * popUpTo and launchSingleTop — now plain list mutations on the typed stack.
 */
class GnomeNavigatorTest {

    private fun stackOf(vararg keys: NavKey): NavBackStack<NavKey> =
        NavBackStack(*keys)

    @Test
    fun `navigate pushes a key`() {
        val backStack = stackOf(TimelineKey)
        val navigator = GnomeNavigator(backStack)

        navigator.navigate(StatsKey)

        assertEquals(listOf(TimelineKey, StatsKey), backStack)
    }

    @Test
    fun `navigate singleTop skips a repeated top key`() {
        val backStack = stackOf(TimelineKey, TagKey("work"))
        val navigator = GnomeNavigator(backStack)

        navigator.navigate(TagKey("work"), singleTop = true)

        assertEquals(listOf(TimelineKey, TagKey("work")), backStack)
    }

    @Test
    fun `navigate singleTop still pushes when top differs`() {
        val backStack = stackOf(TimelineKey, TagKey("work"))
        val navigator = GnomeNavigator(backStack)

        navigator.navigate(TagKey("home"), singleTop = true)

        assertEquals(listOf(TimelineKey, TagKey("work"), TagKey("home")), backStack)
    }

    @Test
    fun `drawer destination switch replaces previously visited roots`() {
        val backStack = stackOf(TimelineKey)
        val navigator = GnomeNavigator(backStack)

        navigator.switchDrawerDestination(ExploreKey)
        navigator.switchDrawerDestination(ResourcesKey)
        navigator.switchDrawerDestination(SettingsKey)

        assertEquals(listOf(SettingsKey), backStack)
    }

    @Test
    fun `reselecting current drawer destination does not mutate the stack`() {
        val backStack = stackOf(ExploreKey)
        val navigator = GnomeNavigator(backStack)

        val changed = navigator.switchDrawerDestination(ExploreKey)

        assertTrue(!changed)
        assertEquals(listOf(ExploreKey), backStack)
    }

    @Test
    fun `switching tags leaves only the requested timeline filter`() {
        val backStack = stackOf(TagKey("playlist"))
        val navigator = GnomeNavigator(backStack)

        navigator.switchDrawerDestination(TagKey("idea"))

        assertEquals(listOf(TagKey("idea")), backStack)
    }

    @Test
    fun `reselecting current tag does not rebuild its destination`() {
        val key = TagKey("idea")
        val backStack = stackOf(key)
        val navigator = GnomeNavigator(backStack)

        val changed = navigator.switchDrawerDestination(key)

        assertTrue(!changed)
        assertEquals(listOf(key), backStack)
    }

    @Test
    fun `goBack pops one entry`() {
        val backStack = stackOf(TimelineKey, SettingsKey)
        val navigator = GnomeNavigator(backStack)

        navigator.goBack()

        assertEquals(listOf(TimelineKey), backStack)
    }

    @Test
    fun `rapid goBack never crashes and empties the stack`() {
        val backStack = stackOf(TimelineKey, StatsKey, StatsDetailKey, ArchivedKey)
        val navigator = GnomeNavigator(backStack)

        repeat(10) { navigator.goBack() }

        assertTrue(backStack.isEmpty())
    }

    @Test
    fun `resetTo replaces the whole stack like login success`() {
        val backStack = stackOf(AddAccountKey, LoginKey)
        val navigator = GnomeNavigator(backStack)

        navigator.resetTo(TimelineKey)

        assertEquals(listOf(TimelineKey), backStack)
        // Back from timeline must not return to the login page.
        navigator.goBack()
        assertTrue(backStack.isEmpty())
    }

    @Test
    fun `popUpTo inclusive removes the target and everything above`() {
        val backStack = stackOf(TimelineKey, StatsKey, StatsDetailKey)
        val navigator = GnomeNavigator(backStack)

        navigator.popUpTo(StatsKey, inclusive = true)
        navigator.navigate(DateKey("2026-08-22"))

        assertEquals(listOf(TimelineKey, DateKey("2026-08-22")), backStack)
    }

    @Test
    fun `popUpTo exclusive keeps the target`() {
        val backStack = stackOf(TimelineKey, StatsKey, StatsDetailKey)
        val navigator = GnomeNavigator(backStack)

        navigator.popUpTo(StatsKey)

        assertEquals(listOf(TimelineKey, StatsKey), backStack)
    }

    @Test
    fun `popUpTo is a no-op when the key is absent`() {
        val backStack = stackOf(TimelineKey, SettingsKey)
        val navigator = GnomeNavigator(backStack)

        navigator.popUpTo(StatsKey, inclusive = true)

        assertEquals(listOf(TimelineKey, SettingsKey), backStack)
    }

    @Test
    fun `quick memo returns to the timeline and clears pages above it`() {
        val backStack = stackOf(TimelineKey, TagKey("work"), MemoDetailKey("10"))
        val navigator = GnomeNavigator(backStack)

        navigator.popUpTo(TimelineKey)

        assertEquals(listOf(TimelineKey), backStack)
    }

    @Test
    fun `tag key preserves chinese and slash characters`() {
        val tag = "#408/计网"
        val backStack = stackOf(TimelineKey)
        val navigator = GnomeNavigator(backStack)

        navigator.navigate(TagKey(tag))

        val pushed = backStack.last()
        assertTrue(pushed is TagKey)
        assertEquals(tag, (pushed as TagKey).tag)
    }

    @Test
    fun `memoId key preserves unusual characters without encoding`() {
        val memoId = "a b/c?d&e=f#g%h中文"
        val backStack = stackOf(TimelineKey)
        val navigator = GnomeNavigator(backStack)

        navigator.navigate(MemoDetailKey(memoId))

        val pushed = backStack.last()
        assertTrue(pushed is MemoDetailKey)
        assertEquals(memoId, (pushed as MemoDetailKey).memoId)
    }

    @Test
    fun `date key roundtrips through ISO string`() {
        val date = LocalDate.of(2026, 8, 22)
        val key = DateKey(date.toString())

        assertEquals(date, LocalDate.parse(key.date))
    }

    @Test
    fun `editor key carries an optional memo id`() {
        val backStack = stackOf(TimelineKey)
        val navigator = GnomeNavigator(backStack)

        navigator.navigate(EditorKey())
        assertNull((backStack.last() as EditorKey).memoId)

        navigator.goBack()
        navigator.navigate(EditorKey("42"))
        assertEquals("42", (backStack.last() as EditorKey).memoId)
    }

    @Test
    fun `data object keys are equal and singleTop collapses them`() {
        val backStack = stackOf(TimelineKey)
        val navigator = GnomeNavigator(backStack)

        navigator.navigate(SearchKey, singleTop = true)
        navigator.navigate(SearchKey, singleTop = true)

        assertEquals(listOf(TimelineKey, SearchKey), backStack)
    }

    @Test
    fun `drawer scoping covers drawer roots and memo child pages`() {
        assertTrue(isDrawerScoped(TimelineKey))
        assertTrue(isDrawerScoped(ArchivedKey))
        assertTrue(isDrawerScoped(TagKey("x")))
        assertTrue(isDrawerScoped(DateKey("2026-08-22")))
        assertTrue(isDrawerScoped(ExploreKey))
        assertTrue(isDrawerScoped(SearchKey))
        assertTrue(isDrawerScoped(MemoDetailKey("1")))
        assertTrue(isDrawerScoped(EditorKey()))
        assertTrue(!isDrawerScoped(StatsKey))
        assertTrue(isDrawerScoped(SettingsKey))
        assertTrue(!isDrawerScoped(LoginKey))
        assertTrue(!isDrawerScoped(AccountKey("k")))
        assertTrue(!isDrawerScoped(ShareKey))
        assertTrue(isDrawerScoped(ResourcesKey))
        assertTrue(!isDrawerScoped(AddAccountKey))
        assertTrue(!isDrawerScoped(null))
    }
}