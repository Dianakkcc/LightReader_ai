package com.lightreader.core.format.comic

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.format.DocumentSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Comic archives come in two shapes and both have to work: a flat archive of page images, and an
 * archive of per-chapter folders. Page order is the other classic trap, because lexicographic
 * order puts page 10 before page 2 and ruins the reading order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComicDocumentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a flat archive of images becomes one chapter`() = runTest {
        val file = createArchive(
            "page1.png" to png(8, 12),
            "page2.png" to png(8, 12),
            "page10.png" to png(8, 12),
            "Thumbs.db" to byteArrayOf(1, 2, 3),
        )
        val document = ComicDocument.open(context, DocumentSource.ofFile(file, "application/zip"))

        assertEquals(1, document.chapters.size)
        assertEquals(3, document.pageCount)
        // A single chapter takes the archive's own name, which is what the shelf shows.
        assertEquals(file.nameWithoutExtension, document.chapters[0].title)
        assertEquals(
            listOf("page1.png", "page2.png", "page10.png"),
            (0 until document.pageCount).map { document.entryNameOf(it) },
        )
        assertNotNull("封面取第一页", document.loadCover())
        document.close()
    }

    @Test
    fun `folders become chapters in natural order`() = runTest {
        val file = createArchive(
            "第2话/002.png" to png(8, 12),
            "第1话/002.png" to png(8, 12),
            "第2话/001.png" to png(8, 12),
            "第1话/001.png" to png(8, 12),
            "第10话/001.png" to png(8, 12),
        )
        val document = ComicDocument.open(context, DocumentSource.ofFile(file, "application/zip"))

        assertEquals(listOf("第1话", "第2话", "第10话"), document.chapters.map { it.title })
        assertEquals(5, document.pageCount)
        assertEquals(
            listOf(
                "第1话/001.png",
                "第1话/002.png",
                "第2话/001.png",
                "第2话/002.png",
                "第10话/001.png",
            ),
            (0 until document.pageCount).map { document.entryNameOf(it) },
        )
        assertTrue(document.pageTitle(2).contains("第2话"))
        document.close()
    }

    @Test
    fun `pages are decoded down to the requested width`() = runTest {
        val file = createArchive("001.png" to png(400, 600), "002.png" to png(400, 600))
        val document = ComicDocument.open(context, DocumentSource.ofFile(file, "application/zip"))

        val bitmap = document.renderPage(0, targetWidth = 200, targetHeight = 800)
        assertNotNull(bitmap)
        assertEquals(200, bitmap!!.width)
        assertEquals(300, bitmap.height)
        document.close()
    }

    private fun createArchive(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("lightreader-comic", ".cbz")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
