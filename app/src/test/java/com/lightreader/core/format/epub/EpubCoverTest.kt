package com.lightreader.core.format.epub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.format.DocumentSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Cover detection across the conventions real EPUB files use.
 *
 * These cases come straight from reader bug reports: EPUB 2 `meta` covers that hold either a
 * manifest id or a file name, books with no cover declaration at all, cover pages instead of cover
 * images, SVG wrappers, and archives whose letter case does not match their own manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubCoverTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
        ByteArray(32) { 7 }
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(32) { 9 }

    @Test
    fun `epub3 cover-image property`() = runTest {
        val file = build(
            manifest = """
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
            """,
            spine = """<itemref idref="c1"/>""",
            extra = mapOf("OEBPS/images/cover.jpg" to jpeg),
        )
        assertCoverIs(file, jpeg)
    }

    @Test
    fun `epub2 meta cover pointing at a manifest id`() = runTest {
        val file = build(
            manifest = """
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="coverimg" href="images/pic.jpg" media-type="image/jpeg"/>
            """,
            spine = """<itemref idref="c1"/>""",
            metaExtra = """<meta name="cover" content="coverimg"/>""",
            extra = mapOf("OEBPS/images/pic.jpg" to jpeg),
        )
        assertCoverIs(file, jpeg)
    }

    @Test
    fun `epub2 meta cover pointing at a file name`() = runTest {
        val file = build(
            manifest = """
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="x1" href="images/my-cover.jpg" media-type="image/jpeg"/>
            """,
            spine = """<itemref idref="c1"/>""",
            metaExtra = """<meta name="cover" content="images/my-cover.jpg"/>""",
            extra = mapOf("OEBPS/images/my-cover.jpg" to jpeg),
        )
        assertCoverIs(file, jpeg)
    }

    @Test
    fun `no declaration at all - the cover is found by file name`() = runTest {
        val file = build(
            manifest = """
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="p1" href="images/photo1.jpg" media-type="image/jpeg"/>
                <item id="p2" href="images/cover-front.jpg" media-type="image/jpeg"/>
            """,
            spine = """<itemref idref="c1"/>""",
            extra = mapOf(
                "OEBPS/images/photo1.jpg" to ByteArray(48) { 3 },
                "OEBPS/images/cover-front.jpg" to jpeg,
            ),
        )
        assertCoverIs(file, jpeg)
    }

    @Test
    fun `cover page as the first spine item`() = runTest {
        val file = build(
            manifest = """
                <item id="cp" href="cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="img" href="pics/art.jpg" media-type="image/jpeg"/>
            """,
            spine = """<itemref idref="cp"/><itemref idref="c1"/>""",
            extra = mapOf(
                "OEBPS/pics/art.jpg" to jpeg,
            ),
            extraText = mapOf(
                "OEBPS/cover.xhtml" to """
                    <?xml version="1.0" encoding="utf-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml"><body>
                      <div><img src="pics/art.jpg" alt=""/></div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        assertCoverIs(file, jpeg)
    }

    @Test
    fun `first chapter with text is not mistaken for a cover`() = runTest {
        val file = build(
            manifest = """
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
            """,
            spine = """<itemref idref="c1"/>""",
            extraText = mapOf(
                "OEBPS/text/ch1.xhtml" to """
                    <?xml version="1.0" encoding="utf-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml"><body>
                      <h1>第一章</h1><p>${"正文内容".repeat(40)}</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val document = EpubDocument.open(context, DocumentSource.ofFile(file, "application/epub+zip"))
        assertEquals(null, document.loadCover())
        document.close()
    }

    @Test
    fun `archive letter case may differ from the manifest`() = runTest {
        val file = build(
            manifest = """
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
            """,
            spine = """<itemref idref="c1"/>""",
            // The archive entry is capitalised differently on purpose.
            extra = mapOf("OEBPS/Images/Cover.JPG" to jpeg),
        )
        assertCoverIs(file, jpeg)
    }

    @Test
    fun `svg cover resolves the raster it wraps`() = runTest {
        val file = build(
            manifest = """
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="cover" href="images/cover.svg" media-type="image/svg+xml" properties="cover-image"/>
            """,
            spine = """<itemref idref="c1"/>""",
            extra = mapOf(
                "OEBPS/images/cover-art.png" to png,
            ),
            extraText = mapOf(
                "OEBPS/images/cover.svg" to """
                    <?xml version="1.0" encoding="utf-8"?>
                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                      <image xlink:href="cover-art.png" width="600" height="800"/>
                    </svg>
                """.trimIndent(),
            ),
        )
        assertCoverIs(file, png)
    }

    private suspend fun assertCoverIs(file: File, expected: ByteArray) {
        val document = EpubDocument.open(context, DocumentSource.ofFile(file, "application/epub+zip"))
        val cover = document.loadCover()
        assertNotNull("应识别出封面", cover)
        assertEquals(expected.size, cover!!.size)
        assertEquals(expected[0], cover[0])
        document.close()
    }

    private fun build(
        manifest: String,
        spine: String,
        metaExtra: String = "",
        extra: Map<String, ByteArray> = emptyMap(),
        extraText: Map<String, String> = emptyMap(),
    ): File {
        val file = File.createTempFile("lightreader-cover", ".epub")
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
                       <dc:title>封面测试</dc:title>
                       <dc:creator>作者</dc:creator>
                       $metaExtra
                     </metadata>
                     <manifest>$manifest</manifest>
                     <spine>$spine</spine>
                   </package>""",
            )
            extraText.forEach { (name, content) -> add(name, content) }
            extra.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            // A minimal chapter so the document always has readable content of its own.
            if (!extraText.containsKey("OEBPS/text/ch1.xhtml")) {
                add(
                    "OEBPS/text/ch1.xhtml",
                    """<?xml version="1.0" encoding="utf-8"?>
                       <html xmlns="http://www.w3.org/1999/xhtml"><body><p>正文</p></body></html>""",
                )
            }
        }
        return file
    }
}
