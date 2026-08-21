package me.mudkip.moememos.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiResourceNameTest {
    @Test
    fun parsesOpaqueResourceIdentifiersWithoutNumericAssumptions() {
        val memo = ApiResourceName.parse("memos/AbC-123_uuid", "memos")

        assertEquals("AbC-123_uuid", memo.identifier)
        assertEquals("memos/AbC-123_uuid", memo.value)
    }

    @Test
    fun handlesLegacyTransportSuffixAtOneBoundary() {
        val attachment = ApiResourceName.parse("attachments/file-id|legacy", "attachments")

        assertEquals("file-id", attachment.identifier)
        assertEquals("attachments/file-id", attachment.value)
    }

    @Test
    fun rejectsWrongOrMalformedResourceNames() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiResourceName.parse("users/alice", "memos")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiResourceName.parse("memos/a/second-segment", "memos")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiResourceName.parse("42", "memos")
        }
    }
}
