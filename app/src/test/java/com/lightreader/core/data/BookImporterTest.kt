package com.lightreader.core.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.data.db.AppDatabase
import com.lightreader.core.format.ParserFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * End to end check of the import chain: format detection → metadata → cover thumbnail → database
 * entry → removal, exactly the path the shelf uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookImporterTest {

    private lateinit var database: AppDatabase
    private lateinit var workspace: BookWorkspace
    private lateinit var importer: BookImporter
    private lateinit var library: LibraryRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workspace = BookWorkspace(context)
        val coverStore = CoverStore(workspace)
        importer = BookImporter(context, ParserFactory.createRegistry(), database.bookDao(), coverStore)
        library = LibraryRepository(
            context = context,
            bookDao = database.bookDao(),
            progressDao = database.progressDao(),
            bookmarkDao = database.bookmarkDao(),
            workspace = workspace,
            coverStore = coverStore,
            importer = importer,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `importing an epub stores metadata and a cover`() = runTest {
        val file = createEpub()
        val uri = Uri.fromFile(file)

        val result = importer.import(listOf(uri))
        assertEquals(1, result.imported)
        assertTrue(result.failures.isEmpty())

        val book = library.observeBooks().first().single()
        assertEquals("测试书籍", book.title)
        assertEquals("测试作者", book.author)
        assertEquals("epub", book.format.id)
        assertNotNull("应生成封面缩略图", book.coverPath)
        assertTrue(File(book.coverPath!!).isFile)
        assertTrue(File(book.coverPath!!).length() > 0)
    }

    @Test
    fun `importing the same file twice reports a duplicate`() = runTest {
        val file = createEpub()
        val uri = Uri.fromFile(file)

        importer.import(listOf(uri))
        val second = importer.import(listOf(uri))
        assertEquals(0, second.imported)
        assertEquals(1, second.duplicates)
        assertEquals(1, library.observeBooks().first().size)
    }

    @Test
    fun `unsupported files fail without throwing`() = runTest {
        val file = File.createTempFile("lightreader-unknown", ".xyz")
        file.writeBytes(ByteArray(64) { 0x1F })

        val result = importer.import(listOf(Uri.fromFile(file)))
        assertEquals(0, result.imported)
        assertEquals(1, result.failures.size)
        assertTrue(result.failures.first().message.isNotBlank())
    }

    @Test
    fun `removing from the shelf keeps the original file`() = runTest {
        val file = createEpub()
        importer.import(listOf(Uri.fromFile(file)))
        val book = library.observeBooks().first().single()

        library.removeFromShelf(book)

        assertTrue(library.observeBooks().first().isEmpty())
        assertTrue("原文件必须保留", file.isFile)
    }

    private fun createEpub(): File {
        val file = File.createTempFile("lightreader-import", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            add(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                     <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                   </container>""",
            )
            add(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="utf-8"?>
                   <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                     <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                       <dc:title>测试书籍</dc:title>
                       <dc:creator>测试作者</dc:creator>
                     </metadata>
                     <manifest>
                       <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                       <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                     </manifest>
                     <spine><itemref idref="c1"/></spine>
                   </package>""",
            )
            add(
                "OEBPS/chapter1.xhtml",
                """<?xml version="1.0" encoding="utf-8"?>
                   <html xmlns="http://www.w3.org/1999/xhtml"><body><p>正文</p></body></html>""",
            )
            zip.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
            zip.write(createJpegBytes())
            zip.closeEntry()
        }
        return file
    }

    /** A tiny but genuinely decodable JPEG, so the cover pipeline can be verified. */
    private fun createJpegBytes(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(40, 60, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(40, 80, 140))
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
