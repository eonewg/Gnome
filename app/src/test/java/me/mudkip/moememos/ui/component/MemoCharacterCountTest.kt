package me.mudkip.moememos.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoCharacterCountTest {
    @Test
    fun countsUnicodeCodePointsInsteadOfUtf16Units() {
        assertEquals(3, "灵感🙂".memoCharacterCount())
    }

    @Test
    fun includesSpacesAndLineBreaksShownInMemoContent() {
        assertEquals(5, "A B\n中".memoCharacterCount())
    }
}