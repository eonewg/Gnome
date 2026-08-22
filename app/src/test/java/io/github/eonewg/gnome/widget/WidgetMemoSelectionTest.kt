package io.github.eonewg.gnome.widget

import io.github.eonewg.gnome.core.model.Memo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetMemoSelectionTest {

    private fun memo(
        id: String,
        content: String,
        pinned: Boolean = false,
        date: Instant = Instant.ofEpochMilli(id.hashCode().toLong()),
    ) = Memo(id = id, content = content, pinned = pinned, date = date)

    @Test
    fun `sorts pinned first then newest first`() {
        val old = memo("old", "a")
        val pinned = memo("pin", "b", pinned = true)
        val fresh = memo("fresh", "c", date = Instant.EPOCH.plusSeconds(999_999))

        val selected = selectWidgetMemos(listOf(old, pinned, fresh), maxItems = 10)

        assertEquals(listOf("pin", "fresh", "old"), selected.map { it.id })
    }

    @Test
    fun `filters by tag substring`() {
        val match = memo("m1", "笔记 #408/计网 速查")
        val other = memo("m2", "普通记录")

        val selected = selectWidgetMemos(listOf(match, other), filterTag = "408/计网")

        assertEquals(listOf("m1"), selected.map { it.id })
    }

    @Test
    fun `pinned only filter excludes unpinned`() {
        val pinned = memo("p1", "置顶", pinned = true)
        val unpinned = memo("u1", "普通")

        val selected = selectWidgetMemos(listOf(pinned, unpinned), pinnedOnly = true)

        assertEquals(listOf("p1"), selected.map { it.id })
    }

    @Test
    fun `caps the selection at maxItems`() {
        val memos = (0 until 20).map { memo("m$it", "内容 $it") }

        val selected = selectWidgetMemos(memos, maxItems = 5)

        assertEquals(5, selected.size)
    }

    @Test
    fun `empty timeline yields empty selection`() {
        assertTrue(selectWidgetMemos(emptyList()).isEmpty())
    }

    @Test
    fun `memory pick returns null on empty timeline`() {
        assertNull(pickMemoryMemo(emptyList()))
    }

    @Test
    fun `memory pick returns one of the memos`() {
        val memos = listOf(memo("a", "1"), memo("b", "2"), memo("c", "3"))

        val picked = pickMemoryMemo(memos)

        assertTrue(memos.any { it.id == picked?.id })
    }
}