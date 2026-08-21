package me.mudkip.moememos.data.repository

import me.mudkip.moememos.data.api.MemosV1Resource
import me.mudkip.moememos.data.api.MemosV1State
import me.mudkip.moememos.data.api.MemosVisibility
import me.mudkip.moememos.data.api.UpdateMemoRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemosV1RepositoryTest {
    @Test
    fun buildsRequiredV030UpdateMaskFromChangedFields() {
        val request = UpdateMemoRequest(
            content = "updated",
            visibility = MemosVisibility.PROTECTED,
            state = MemosV1State.NORMAL,
            pinned = false,
            updateTime = Instant.parse("2026-08-21T00:00:00Z"),
            attachments = listOf(MemosV1Resource(name = "attachments/file")),
        )

        assertEquals(
            "content,visibility,state,pinned,update_time,attachments",
            updateMaskFor(request),
        )
    }

    @Test
    fun omitsFieldsThatAreNotBeingUpdated() {
        val request = UpdateMemoRequest(pinned = true)

        assertEquals("pinned", updateMaskFor(request))
    }
}
