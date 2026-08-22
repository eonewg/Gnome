package io.github.eonewg.gnome.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoContentTest {
    @Test
    fun shortMemoIsNotTruncated() {
        val content = "#想法 一段适合直接阅读的短 Memo"

        val (preview, truncated) = extractPreviewContent(content)

        assertEquals(content, preview)
        assertFalse(truncated)
    }

    @Test
    fun plainChineseMemoWithTagsUsesFastRenderer() {
        assertFalse(requiresRichMarkdown("#想法 普通中文正文\n第二行 #项目/安卓"))
    }

    @Test
    fun markdownContentKeepsFullRenderer() {
        assertTrue(requiresRichMarkdown("- 列表项"))
        assertTrue(requiresRichMarkdown("查看 [链接](https://example.com)"))
        assertTrue(requiresRichMarkdown("**加粗正文**"))
        assertTrue(requiresRichMarkdown("*斜体正文*"))
        assertTrue(requiresRichMarkdown("_italic text_"))
    }

    @Test
    fun longMemoUsesDisplayOnlyEllipsis() {
        val content = "这是一段很长的中文 Memo。".repeat(40)

        val (preview, truncated) = extractPreviewContent(content)

        assertTrue(truncated)
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length < content.length)
        assertFalse(content.endsWith("…"))
    }

    @Test
    fun longPlainMemoPreviewDoesNotSplitSurrogatePair() {
        val content = "中文".repeat(109) + "😀" + "结尾"

        val (preview, truncated) = extractPreviewContent(content, maxLength = 219)

        assertTrue(truncated)
        assertEquals("中文".repeat(109) + "…", preview)
    }

    @Test
    fun unbreakableMarkdownNodeIsNeverCutInHalf() {
        val image = "![示例图片](https://example.com/image.png)"
        val content = "开头\n\n$image\n\n" + "后续内容".repeat(40)

        val (preview, truncated) = extractPreviewContent(content, maxLength = 120)

        assertTrue(truncated)
        assertTrue(preview.contains(image))
    }
}
