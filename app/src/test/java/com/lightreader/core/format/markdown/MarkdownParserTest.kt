package com.lightreader.core.format.markdown

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.model.ReflowableDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownParserTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `renders headings emphasis code and tables`() = runTest {
        val markdown = """
            # 第一章 简介

            这是一段 **加粗** 与 *斜体* 文本，还有 `行内代码`。

            ## 小节

            - 项目一
            - 项目二

            | 列 A | 列 B |
            | --- | --- |
            | 1 | 2 |

            ```kotlin
            val x = 1
            ```
        """.trimIndent()
        val file = File.createTempFile("lightreader-md", ".md")
        file.writeText(markdown, Charsets.UTF_8)

        val parser = MarkdownParser()
        val document = parser.parse(context, DocumentSource.ofFile(file, "text/markdown"))
            as ReflowableDocument
        // Headings split the document into chapters; join them for the format assertions.
        val htmlBuilder = StringBuilder()
        for (index in document.chapters.indices) {
            htmlBuilder.append(document.loadChapter(index).html).append('\n')
        }
        val html = htmlBuilder.toString()

        assertTrue("标题: $html", html.contains("<h1"))
        assertTrue("加粗: $html", html.contains("<strong>加粗</strong>"))
        assertTrue("斜体: $html", html.contains("<em>斜体</em>"))
        assertTrue("代码: $html", html.contains("<code>"))
        assertTrue("列表: $html", html.contains("<li>"))
        assertTrue("表格: $html", html.contains("<table>"))
        assertTrue("代码块: $html", html.contains("<pre>"))
        document.close()
    }

    @Test
    fun `first heading becomes the book title`() = runTest {
        val file = File.createTempFile("lightreader-md-title", ".md")
        file.writeText("# 我的书\n\n正文", Charsets.UTF_8)

        val document = MarkdownParser().parse(
            context,
            DocumentSource.ofFile(file, "text/markdown"),
        ) as ReflowableDocument
        assertEquals("我的书", document.metadata.title)
        assertTrue(document.loadChapter(0).text.contains("正文"))
        document.close()
    }
}
