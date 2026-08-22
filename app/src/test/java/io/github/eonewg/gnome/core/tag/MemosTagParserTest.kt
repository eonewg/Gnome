package io.github.eonewg.gnome.core.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compatibility vectors for [MemosTagParser] against Memos ADR 0001
 * "Tag Syntax and Recognition" (2026-08) plus the CJK/punctuation cases
 * Gnome users hit daily. Vectors marked with the ADR's source strings are
 * normative examples from the accepted spec.
 */
class MemosTagParserTest {

    private fun tags(content: String): Set<String> = MemosTagParser.extractTags(content)

    // ------------------------------------------------------------------
    // Everyday forms
    // ------------------------------------------------------------------

    @Test
    fun `plain and multilingual tags`() {
        assertEquals(setOf("tag"), tags("#tag"))
        assertEquals(setOf("数学"), tags("#数学"))
        assertEquals(setOf("中文"), tags("#中文"))
        assertEquals(setOf("408", "408/计网"), tags("#408/计网"))
        assertEquals(setOf("2026"), tags("#2026"))
        assertEquals(setOf("foo-bar"), tags("#foo-bar"))
        assertEquals(setOf("foo_bar"), tags("#foo_bar"))
    }

    @Test
    fun `multiple tags in flowing text`() {
        assertEquals(setOf("work", "idea"), tags("今天的 #work 记录和 #idea 备忘"))
        assertEquals(setOf("a", "b", "c"), tags("#a text #b more #c"))
    }

    @Test
    fun `no general left boundary`() {
        assertEquals(setOf("tag"), tags("hello#tag"))
        assertEquals(setOf("标签"), tags("中文#标签"))
    }

    @Test
    fun `consecutive hash marks`() {
        assertEquals(setOf("first", "second"), tags("#first#second"))
        assertEquals(setOf("tag"), tags("##tag"))
        assertEquals(emptySet<String>(), tags("## tag"))
    }

    @Test
    fun `english punctuation terminates the identifier`() {
        assertEquals(setOf("foo"), tags("#foo,bar"))
        assertEquals(setOf("foo"), tags("#foo.bar"))
        assertEquals(setOf("foo"), tags("#foo:bar"))
        assertEquals(setOf("x", "foo"), tags("#x=y #foo!"))
        assertEquals(setOf("price"), tags("#price€"))
        assertEquals(setOf("v"), tags("#v²"))
    }

    @Test
    fun `chinese punctuation terminates the identifier`() {
        assertEquals(setOf("标签"), tags("#标签，后续"))
        assertEquals(setOf("标签"), tags("#标签。句子"))
        assertEquals(setOf("标签"), tags("#标签、其他"))
        assertEquals(setOf("标签"), tags("#标签：说明"))
        assertEquals(setOf("标签"), tags("#标签！"))
        assertEquals(setOf("标签"), tags("#标签？"))
        assertEquals(setOf("标签"), tags("“#标签”引号"))
    }

    @Test
    fun `newlines terminate and separate tags`() {
        assertEquals(setOf("one", "two"), tags("#one\n#two"))
        assertEquals(setOf("one", "two"), tags("#one\r\n#two"))
        assertEquals(setOf("one"), tags("#one\n\n#two".replace("#two", "plain")))
    }

    // ------------------------------------------------------------------
    // ADR 0001 lexical vectors
    // ------------------------------------------------------------------

    @Test
    fun `memos extension units - plus ampersand hyphen`() {
        assertEquals(setOf("C++"), tags("#C++"))
        assertEquals(setOf("R&D"), tags("#R&D"))
        assertEquals(setOf("-foo"), tags("#-foo"))
        assertEquals(setOf("foo-"), tags("#foo-"))
        assertEquals(setOf("---"), tags("#---"))
        assertEquals(setOf("&&"), tags("#&&"))
    }

    @Test
    fun `apostrophe joiners follow the contextual rule`() {
        assertEquals(setOf("tag's"), tags("#tag's"))
        assertEquals(setOf("сім'я"), tags("#сім'я"))
        assertEquals(setOf("O’Brien"), tags("#O’Brien"))
        assertEquals(setOf("café's"), tags("#café's"))
        assertEquals(setOf("users"), tags("#users'"))
        assertEquals(setOf("foo"), tags("#foo'1️⃣"))
        assertEquals(emptySet<String>(), tags("#'tag"))
        assertEquals(setOf("rock’n’roll"), tags("#rock’n’roll"))
        assertEquals(setOf("OʼBrien"), tags("#OʼBrien"))
    }

    @Test
    fun `slash hierarchy expands ancestors and rejects empty segments`() {
        assertEquals(setOf("work", "work/notes"), tags("#work/notes"))
        assertEquals(setOf("book", "book/fiction", "book/fiction/history"), tags("#book/fiction/history"))
        assertEquals(setOf("book"), tags("#book/"))
        assertEquals(emptySet<String>(), tags("#/book"))
        assertEquals(setOf("book"), tags("#book//fiction"))
        assertEquals(setOf("book", "book/fiction"), tags("#book/fiction/"))
        assertEquals(setOf("foo", "foo/bar"), tags("#foo/́bar"))
    }

    @Test
    fun `invisible code points are consumed but omitted`() {
        // A + ZWJ + B and A + ZWNJ + B both emit "AB".
        assertEquals(setOf("AB"), tags("#A‍B"))
        assertEquals(setOf("foo"), tags("#‍foo"))
        assertEquals(setOf("AB"), tags("#A‌B"))
        assertEquals(setOf("foo"), tags("#‌foo"))
        assertEquals(setOf("AB"), tags("#A️B"))
        assertEquals(setOf("foo"), tags("#́foo"))
        assertEquals(emptySet<String>(), tags("#́"))
        assertEquals(setOf("café"), tags("#café"))
    }

    @Test
    fun `emoji sequences are atomic value units`() {
        assertEquals(setOf("♥️"), tags("#♥️"))
        assertEquals(setOf("‼️"), tags("#‼️"))
        assertEquals(setOf("*️⃣"), tags("#*️⃣"))
        assertEquals(setOf("#️⃣"), tags("##️⃣"))
        assertEquals(setOf("first#️⃣"), tags("#first#️⃣"))
        assertEquals(setOf("😀笔记"), tags("#😀笔记"))
        assertEquals(emptySet<String>(), tags("#♥"))
        assertEquals(emptySet<String>(), tags("#️⃣"))
        assertEquals(emptySet<String>(), tags("#🏻"))
    }

    @Test
    fun `quote punctuation stays outside the occurrence`() {
        assertEquals(setOf("tag"), tags("'#tag'"))
        assertEquals(setOf("tag"), tags("“#tag”"))
    }

    // ------------------------------------------------------------------
    // Markdown context (GFM literal-source runs)
    // ------------------------------------------------------------------

    @Test
    fun `escaped introducer produces no tag`() {
        assertEquals(emptySet<String>(), tags("\\#literal"))
    }

    @Test
    fun `inline code and code blocks are excluded`() {
        assertEquals(emptySet<String>(), tags("`#code`"))
        assertEquals(emptySet<String>(), tags("before `#code` after"))
        assertEquals(emptySet<String>(), tags("```\n#code\n```"))
        assertEquals(setOf("real"), tags("```\n#code\n```\n#real"))
    }

    @Test
    fun `links images and autolinks are excluded entirely`() {
        assertEquals(emptySet<String>(), tags("[hello#tag](https://example.com)"))
        assertEquals(emptySet<String>(), tags("[release #notes](https://example.com/releases#notes)"))
        assertEquals(emptySet<String>(), tags("![alt #tag](https://example.com/pic#tag.png)"))
        assertEquals(emptySet<String>(), tags("https://example.com/#tag"))
        assertEquals(emptySet<String>(), tags("<https://example.com/#tag>"))
    }

    @Test
    fun `link definitions are excluded while following text stays eligible`() {
        val markdown = """
            [docs][memos]
            [memos]: https://example.com/path#fragment
            #realTag
        """.trimIndent()
        assertEquals(setOf("realTag"), tags(markdown))
    }

    @Test
    fun `plain text paths are not treated as urls`() {
        assertEquals(setOf("tag"), tags("/path#tag"))
    }

    @Test
    fun `formatted text headings and lists stay eligible`() {
        assertEquals(setOf("urgent"), tags("**#urgent**"))
        assertEquals(setOf("urgent"), tags("*#urgent*"))
        assertEquals(setOf("urgent"), tags("## 标题 #urgent"))
        assertEquals(setOf("item"), tags("- #item\n- plain"))
        assertEquals(setOf("item"), tags("1. #item"))
        assertEquals(setOf("quote"), tags("> #quote"))
    }

    @Test
    fun `character references are syntax boundaries`() {
        assertEquals(emptySet<String>(), tags("&#35;tag"))
        assertEquals(emptySet<String>(), tags("&num;tag"))
        assertEquals(setOf("R"), tags("#R&amp;D"))
        assertEquals(setOf("R&D"), tags("#R&D"))
    }

    // ------------------------------------------------------------------
    // Raw scan API (editor highlighting)
    // ------------------------------------------------------------------

    @Test
    fun `scan reports source spans for highlighting`() {
        val occurrences = MemosTagParser.scanTagOccurrences("hello #world, bye")
        assertEquals(1, occurrences.size)
        assertEquals("world", occurrences.single().value)
        assertEquals("#world", "hello #world, bye".substring(occurrences.single().start, occurrences.single().endExclusive))
    }

    @Test
    fun `scan terminates the span before terminating punctuation`() {
        val text = "#foo,bar #second"
        val occurrences = MemosTagParser.scanTagOccurrences(text)
        assertEquals(listOf("foo", "second"), occurrences.map { it.value })
        assertEquals("#foo", text.substring(occurrences[0].start, occurrences[0].endExclusive))
        assertEquals("#second", text.substring(occurrences[1].start, occurrences[1].endExclusive))
    }

    @Test
    fun `empty and hash-only inputs produce nothing`() {
        assertTrue(tags("").isEmpty())
        assertTrue(tags("#").isEmpty())
        assertTrue(tags("# ").isEmpty())
        assertTrue(tags("plain text").isEmpty())
    }
}
