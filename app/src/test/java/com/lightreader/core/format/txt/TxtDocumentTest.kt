package com.lightreader.core.format.txt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.format.DocumentSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.Charset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TxtDocumentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `decodes gbk text and reads paragraphs`() = runTest {
        val text = buildString {
            append("第一章 开端\n")
            append("这是一个用于测试的段落，包含中文内容。\n")
            append("\n")
            append("第二章 发展\n")
            append("第二章的正文内容。\n")
        }
        val file = File.createTempFile("lightreader-test", ".txt")
        file.writeBytes(text.toByteArray(Charset.forName("GBK")))

        val document = TxtDocument.open(context, DocumentSource.ofFile(file, "text/plain"))
        assertEquals(Charset.forName("GB18030"), document.charset)

        val chapter = document.loadChapter(0)
        assertTrue("应能读出中文正文：${chapter.text}", chapter.text.contains("测试的段落"))
        document.close()
    }

    @Test
    fun `builds chapter index from headings`() = runTest {
        val text = buildString {
            append("第一章 开端\n")
            append("正文一\n".repeat(40))
            append("第二章 发展\n")
            append("正文二\n".repeat(40))
            append("第三章 结尾\n")
            append("正文三\n".repeat(40))
        }
        val file = File.createTempFile("lightreader-index", ".txt")
        file.writeBytes(text.toByteArray(Charsets.UTF_8))

        val document = TxtDocument.open(context, DocumentSource.ofFile(file, "text/plain"))
        val chapters = document.buildIndex()
        assertEquals(3, chapters.size)
        assertEquals("第一章 开端", chapters[0].title)
        assertEquals("第三章 结尾", chapters[2].title)

        val third = document.loadChapter(2)
        assertTrue(third.text.contains("正文三"))
        assertTrue(!third.text.contains("正文一"))
        document.close()
    }

    @Test
    fun `searches inside a large file`() = runTest {
        val text = buildString {
            repeat(2000) { index ->
                append("第 $index 行 普通内容\n")
            }
            append("这里出现了关键字 特殊词汇\n")
            repeat(2000) { index ->
                append("尾部第 $index 行\n")
            }
        }
        val file = File.createTempFile("lightreader-search", ".txt")
        file.writeBytes(text.toByteArray(Charsets.UTF_8))

        val document = TxtDocument.open(context, DocumentSource.ofFile(file, "text/plain"))
        val hits = document.search("特殊词汇", 10)
        assertEquals(1, hits.size)
        assertTrue(hits.first().snippet.contains("特殊词汇"))
        document.close()
    }
}
