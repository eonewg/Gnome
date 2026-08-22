package io.github.eonewg.gnome.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoRepositoryTest {
    @Test
    fun mergesRemoteInstanceTagsWithOfflineTags() {
        assertEquals(
            listOf("408/计网", "local", "数学", "数据结构"),
            mergeTags(
                localTags = listOf("local", "数学"),
                remoteTags = listOf("数据结构", "数学", "408/计网", ""),
            ),
        )
    }
}
