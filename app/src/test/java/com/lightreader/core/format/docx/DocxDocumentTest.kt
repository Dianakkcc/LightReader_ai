package com.lightreader.core.format.docx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.model.ReflowableDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocxDocumentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `converts paragraphs headings lists and tables`() = runTest {
        val file = createDocx()
        val document = DocxParser().parse(
            context,
            DocumentSource.ofFile(
                file,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ),
        ) as ReflowableDocument

        val html = document.loadChapter(0).html
        assertTrue("标题应渲染为 h1: $html", html.contains("<h1>"))
        assertTrue("加粗文字应保留: $html", html.contains("<strong>加粗文字</strong>"))
        assertTrue("普通文字应保留: $html", html.contains("普通文字"))
        assertTrue("列表应渲染: $html", html.contains("<li>列表项</li>"))
        assertTrue("表格应渲染: $html", html.contains("<table>"))
        assertTrue("表格内容应渲染: $html", html.contains("<td><p>A</p></td>"))
        document.close()
    }

    @Test
    fun `inline images are linked to the reader host`() = runTest {
        val file = createDocx()
        val document = DocxParser().parse(
            context,
            DocumentSource.ofFile(file),
        ) as ReflowableDocument

        val html = document.loadChapter(0).html
        val url = Regex("""https://reader\.local/media/[^"']*image1\.png""").find(html)?.value
        assertNotNull("图片应指向阅读器资源地址: $html", url)

        val resource = document.resolveResource(url!!.removePrefix("https://reader.local/"))
        assertNotNull(resource)
        assertEquals("image/png", resource!!.mimeType)
        document.close()
    }

    @Test
    fun `table of contents follows headings`() = runTest {
        val file = createDocx()
        val document = DocxParser().parse(context, DocumentSource.ofFile(file)) as ReflowableDocument
        assertTrue(document.chapters.isNotEmpty())
        assertEquals("第一章 标题", document.chapters.first().title)
        document.close()
    }

    private fun createDocx(): File {
        val file = File.createTempFile("lightreader-test", ".docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            add(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                   <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                     <Default Extension="xml" ContentType="application/xml"/>
                     <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                   </Types>""",
            )
            add(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                   <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                               xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                               xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                     <w:body>
                       <w:p>
                         <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                         <w:r><w:t>第一章 标题</w:t></w:r>
                       </w:p>
                       <w:p>
                         <w:pPr><w:jc w:val="center"/></w:pPr>
                         <w:r><w:rPr><w:b/></w:rPr><w:t>加粗文字</w:t></w:r>
                         <w:r><w:t>普通文字</w:t></w:r>
                       </w:p>
                       <w:p>
                         <w:pPr>
                           <w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr>
                         </w:pPr>
                         <w:r><w:t>列表项</w:t></w:r>
                       </w:p>
                       <w:tbl>
                         <w:tr>
                           <w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>
                           <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>
                         </w:tr>
                       </w:tbl>
                       <w:p>
                         <w:r>
                           <w:drawing>
                             <wp:inline>
                               <wp:extent cx="914400" cy="914400"/>
                               <a:graphic><a:blip r:embed="rId5"/></a:graphic>
                             </wp:inline>
                           </w:drawing>
                         </w:r>
                       </w:p>
                     </w:body>
                   </w:document>""",
            )
            add(
                "word/_rels/document.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                   <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                     <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
                   </Relationships>""",
            )
            add(
                "word/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                   <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                     <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style>
                   </w:styles>""",
            )
            add(
                "word/numbering.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                   <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                     <w:abstractNum w:abstractNumId="0">
                       <w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl>
                     </w:abstractNum>
                     <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
                   </w:numbering>""",
            )
            zip.putNextEntry(ZipEntry("word/media/image1.png"))
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 9, 9))
            zip.closeEntry()
        }
        return file
    }
}
