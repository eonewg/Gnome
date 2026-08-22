package io.github.eonewg.gnome.data.model

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Share intent parsing contract: text prefill, single/multiple streams and
 * graceful empty handling for unsupported payloads (the editor renders an
 * empty compose draft instead of crashing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareContentTest {

    @Test
    fun `text share prefills the editor`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "分享一段文字")
        }

        val content = ShareContent.parseIntent(intent)

        assertEquals("分享一段文字", content.text)
        assertTrue(content.images.isEmpty())
    }

    @Test
    fun `single image share carries the stream uri`() {
        val stream = Uri.parse("content://com.example/shared/photo.jpg")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, stream)
        }

        val content = ShareContent.parseIntent(intent)

        assertEquals("", content.text)
        assertEquals(listOf(stream), content.images)
    }

    @Test
    fun `multiple image share carries every stream uri`() {
        val first = Uri.parse("content://com.example/shared/a.png")
        val second = Uri.parse("content://com.example/shared/b.png")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }

        val content = ShareContent.parseIntent(intent)

        assertEquals(listOf(first, second), content.images)
    }

    @Test
    fun `unsupported payload yields an empty draft`() {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream" }

        val content = ShareContent.parseIntent(intent)

        assertEquals("", content.text)
        assertTrue(content.images.isEmpty())
    }

    @Test
    fun `missing extras yield an empty draft`() {
        val content = ShareContent.parseIntent(Intent(Intent.ACTION_SEND))

        assertEquals("", content.text)
        assertTrue(content.images.isEmpty())
    }

    @Test
    fun `text with blank value keeps the images`() {
        val stream = Uri.parse("content://com.example/shared/doc.pdf")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TEXT, "   ")
            putExtra(Intent.EXTRA_STREAM, stream)
        }

        val content = ShareContent.parseIntent(intent)

        assertEquals("   ", content.text)
        assertEquals(listOf(stream), content.images)
    }
}