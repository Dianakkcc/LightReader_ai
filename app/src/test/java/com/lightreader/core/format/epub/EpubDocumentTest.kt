package com.lightreader.core.format.epub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.model.ChapterContent
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubDocumentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `reads metadata spine and navigation`() = runTest {
        val file = createEpub()
        val document = EpubDocument.open(context, DocumentSource.ofFile(file, "application/epub+zip"))

        assertEquals("测试书籍", document.metadata.title)
        assertEquals("测试作者", document.metadata.author)
        assertEquals(2, document.chapters.size)
        assertEquals(listOf("第一章", "第二章"), document.toc.map { it.title })
        document.close()
    }

    @Test
    fun `chapter html keeps book css and rewrites resources`() = runTest {
        val file = createEpub()
        val document = EpubDocument.open(context, DocumentSource.ofFile(file, "application/epub+zip"))
        val chapter: ChapterContent = document.loadChapter(0)

        assertTrue(chapter.html.contains("正文一"))
        assertTrue(chapter.html.contains("images/pic.png"))
        assertTrue(chapter.html.contains("https://reader.local/"))
        assertTrue(chapter.text.contains("正文一"))
        assertNotNull(chapter.css)
        assertTrue(chapter.css!!.contains("color"))

        // The chapter HTML points at the reader's virtual host; resolve that exact path.
        val url = Regex("""https://reader\.local/[^"']*images/pic\.png""")
            .find(chapter.html)?.value
        assertNotNull("图片地址应被重写到阅读器内部资源地址", url)
        val resource = document.resolveResource(url!!.removePrefix("https://reader.local/"))
        assertNotNull(resource)
        assertTrue(resource!!.mimeType.startsWith("image/"))
        document.close()
    }

    @Test
    fun `extracts cover image`() = runTest {
        val file = createEpub()
        val document = EpubDocument.open(context, DocumentSource.ofFile(file, "application/epub+zip"))
        val cover = document.loadCover()
        assertNotNull(cover)
        assertTrue(cover!!.isNotEmpty())
        document.close()
    }

    @Test
    fun `progress weights are refined from the chapter text`() = runTest {
        val file = createEpub()
        val document = EpubDocument.open(context, DocumentSource.ofFile(file, "application/epub+zip"))
        val before = document.chapters.map { it.weight }

        assertTrue("后台权重扫描应报告有变化", document.refineWeights())
        val after = document.chapters.map { it.weight }
        assertTrue("权重应改成按正文字数计算：$before -> $after", after != before)
        assertTrue("每一章都应有正的权重", after.all { it > 0L })
        assertEquals(
            "再扫一次不应重复计算",
            false,
            document.refineWeights(),
        )
        document.close()
    }

    @Test
    fun `removes scripts from chapter content`() = runTest {
        val file = createEpub(chapterScript = true)
        val document = EpubDocument.open(context, DocumentSource.ofFile(file, "application/epub+zip"))
        val chapter = document.loadChapter(0)
        assertTrue(!chapter.html.contains("<script"))
        assertTrue(!chapter.html.contains("onclick"))
        document.close()
    }

    private fun createEpub(chapterScript: Boolean = false): File {
        val file = File.createTempFile("lightreader-test", ".epub")
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
                     <rootfiles>
                       <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                     </rootfiles>
                   </container>""",
            )
            add(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="utf-8"?>
                   <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                     <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                       <dc:title>测试书籍</dc:title>
                       <dc:creator>测试作者</dc:creator>
                       <dc:language>zh</dc:language>
                       <dc:identifier id="bookid">urn:uuid:test</dc:identifier>
                     </metadata>
                     <manifest>
                       <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                       <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                       <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                       <item id="css" href="style.css" media-type="text/css"/>
                       <item id="pic" href="images/pic.png" media-type="image/png"/>
                       <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                     </manifest>
                     <spine>
                       <itemref idref="c1"/>
                       <itemref idref="c2"/>
                     </spine>
                   </package>""",
            )
            add(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="utf-8"?>
                   <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                     <body>
                       <nav epub:type="toc">
                         <ol>
                           <li><a href="chapter1.xhtml">第一章</a></li>
                           <li><a href="chapter2.xhtml">第二章</a></li>
                         </ol>
                       </nav>
                     </body>
                   </html>""",
            )
            val script = if (chapterScript) {
                "<script>alert('x')</script><p onclick=\"evil()\">危险段落</p>"
            } else {
                ""
            }
            add(
                "OEBPS/chapter1.xhtml",
                """<?xml version="1.0" encoding="utf-8"?>
                   <html xmlns="http://www.w3.org/1999/xhtml">
                     <head><link rel="stylesheet" type="text/css" href="style.css"/></head>
                     <body>
                       $script
                       <h1 id="c1">第一章</h1>
                       <p>正文一</p>
                       <img src="images/pic.png" alt="插图"/>
                     </body>
                   </html>""",
            )
            add(
                "OEBPS/chapter2.xhtml",
                """<?xml version="1.0" encoding="utf-8"?>
                   <html xmlns="http://www.w3.org/1999/xhtml">
                     <body><h1>第二章</h1><p>正文二</p></body>
                   </html>""",
            )
            add("OEBPS/style.css", "p { color: #333333; line-height: 1.5; } .x { background: url(images/pic.png); }")
            zip.putNextEntry(ZipEntry("OEBPS/images/pic.png"))
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
            zip.write(ByteArray(64) { it.toByte() })
            zip.closeEntry()
        }
        return file
    }
}
