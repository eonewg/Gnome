package io.github.eonewg.gnome.ui.page.memoinput

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashtagAutocompleteTest {
    @Test
    fun findsHashtagAtCursorInCommonContexts() {
        val cases = listOf(
            "#" to "",
            "#数" to "数",
            "今天学习 #数" to "数",
            "今天学习了#数" to "数",
            "#408" to "408",
            "第一行\n#数学" to "数学",
            "#408/计网" to "408/计网",
        )

        cases.forEach { (content, expectedQuery) ->
            val token = findActiveHashtag(valueAtEnd(content))
            assertEquals(content, expectedQuery, token?.query)
        }
    }

    @Test
    fun usesCursorInsteadOfLastHashtagInDocument() {
        val content = "开头 #数学 中间 #408/计网 结尾"
        val cursor = content.indexOf("数学") + 1

        val token = findActiveHashtag(TextFieldValue(content, TextRange(cursor)))

        assertEquals("数", token?.query)
        assertEquals(content.indexOf("#数学"), token?.start)
        assertEquals(content.indexOf("#数学") + "#数学".length, token?.end)
    }

    @Test
    fun hidesSuggestionsWhenCursorLeavesOrHashIsDeleted() {
        assertNull(findActiveHashtag(valueAtEnd("没有标签")))
        assertNull(findActiveHashtag(valueAtEnd("#数学 后续")))
        assertNull(
            findActiveHashtag(
                TextFieldValue("#数学", TextRange(0, 2))
            )
        )
    }

    @Test
    fun filtersNestedUnicodeAndNumericTags() {
        val tags = listOf("数学", "数据结构", "408/计网", "project/android")

        assertEquals(
            listOf("数学", "数据结构"),
            hashtagSuggestions(findActiveHashtag(valueAtEnd("#数")), tags),
        )
        assertEquals(
            listOf("408/计网"),
            hashtagSuggestions(findActiveHashtag(valueAtEnd("#408/")), tags),
        )
    }

    @Test
    fun replacesWholeTokenAndPreservesTextAndCursor() {
        val content = "今天 #数据构 继续"
        val cursor = content.indexOf("构")
        val value = TextFieldValue(content, TextRange(cursor))
        val token = requireNotNull(findActiveHashtag(value))

        val replaced = replaceActiveHashtag(value, token, "数据结构")

        assertEquals("今天 #数据结构 继续", replaced.text)
        assertEquals("今天 #数据结构 ".length, replaced.selection.start)
        assertEquals(replaced.selection.start, replaced.selection.end)
    }

    private fun valueAtEnd(text: String) = TextFieldValue(text, TextRange(text.length))
}
