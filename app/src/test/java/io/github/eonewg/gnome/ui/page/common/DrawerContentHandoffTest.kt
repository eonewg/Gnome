package io.github.eonewg.gnome.ui.page.common

import io.github.eonewg.gnome.nav.ExploreKey
import io.github.eonewg.gnome.nav.MemoDetailKey
import io.github.eonewg.gnome.nav.TagKey
import io.github.eonewg.gnome.nav.TimelineKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerContentHandoffTest {

    @Test
    fun `drawer root switch creates outgoing and incoming visual roles`() {
        val handoff = requireNotNull(createDrawerContentHandoff(ExploreKey, TimelineKey))

        assertEquals(DrawerContentHandoffRole.Outgoing, handoff.roleOf(ExploreKey))
        assertEquals(DrawerContentHandoffRole.Incoming, handoff.roleOf(TimelineKey))
        assertEquals(1f, handoff.alphaFor(ExploreKey, isTargetScene = false))
        assertEquals(0f, handoff.alphaFor(ExploreKey, isTargetScene = true))
        assertEquals(0f, handoff.alphaFor(TimelineKey, isTargetScene = false))
        assertEquals(1f, handoff.alphaFor(TimelineKey, isTargetScene = true))
    }

    @Test
    fun `same destination reselection does not create a handoff`() {
        assertNull(createDrawerContentHandoff(TimelineKey, TimelineKey))
    }

    @Test
    fun `tag to tag remains owned by tag content transition`() {
        assertNull(createDrawerContentHandoff(TagKey("#歌单"), TagKey("#想法")))
    }

    @Test
    fun `drawer navigation between timeline and tag creates a root handoff`() {
        val timelineToTag = requireNotNull(
            createDrawerContentHandoff(TimelineKey, TagKey("#歌单")),
        )
        val tagToTimeline = requireNotNull(
            createDrawerContentHandoff(TagKey("#歌单"), TimelineKey),
        )

        assertEquals(DrawerContentHandoffRole.Outgoing, timelineToTag.roleOf(TimelineKey))
        assertEquals(DrawerContentHandoffRole.Incoming, timelineToTag.roleOf(TagKey("#歌单")))
        assertEquals(DrawerContentHandoffRole.Outgoing, tagToTimeline.roleOf(TagKey("#歌单")))
        assertEquals(DrawerContentHandoffRole.Incoming, tagToTimeline.roleOf(TimelineKey))
    }

    @Test
    fun `hierarchical navigation does not create a drawer handoff`() {
        assertNull(createDrawerContentHandoff(TimelineKey, MemoDetailKey("memo-1")))
    }

    @Test
    fun `drawer only open close does not create a page handoff`() {
        assertNull(createDrawerContentHandoff(TimelineKey, null))
    }

    @Test
    fun `unrelated scene cannot fade a drawer destination`() {
        val handoff = requireNotNull(createDrawerContentHandoff(ExploreKey, TimelineKey))

        assertTrue(handoff.alphaFor(TagKey("#想法"), isTargetScene = true) == 1f)
    }
}
