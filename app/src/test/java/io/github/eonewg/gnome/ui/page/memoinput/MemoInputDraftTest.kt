package io.github.eonewg.gnome.ui.page.memoinput

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoInputDraftTest {
    @Test
    fun restorableMemoInputDraft_removesSingleTagWithoutBody() {
        assertEquals("", restorableMemoInputDraft("#名字 "))
    }

    @Test
    fun restorableMemoInputDraft_removesMultipleTagsWithoutBody() {
        assertEquals("", restorableMemoInputDraft("#想法 #书单"))
    }

    @Test
    fun restorableMemoInputDraft_keepsDraftWithBodyText() {
        assertEquals("#名字 正文", restorableMemoInputDraft("#名字 正文"))
    }
}
