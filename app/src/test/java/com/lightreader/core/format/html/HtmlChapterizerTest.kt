package com.lightreader.core.format.html

import com.lightreader.core.format.HtmlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlChapterizerTest {

    @Test
    fun `splits at headings`() {
        val html = "<h1>第一章</h1><p>A</p><h1>第二章</h1><p>B</p>"
        val sections = HtmlChapterizer.split(html, "无标题")
        assertEquals(2, sections.size)
        assertEquals("第一章", sections[0].title)
        assertTrue(sections[1].html().contains("B"))
    }

    @Test
    fun `single section when no headings`() {
        val sections = HtmlChapterizer.split("<p>only text</p>", "书名")
        assertEquals(1, sections.size)
        assertEquals("书名", sections[0].title)
    }

    @Test
    fun `sanitizer strips scripts and handlers`() {
        val cleaned = HtmlSanitizer.cleanToBodyHtml(
            "<body><p onclick=\"evil()\">hi</p><script>alert(1)</script><img src=\"x.png\"></body>",
        )
        assertTrue(!cleaned.contains("script"))
        assertTrue(!cleaned.contains("onclick"))
        assertTrue(cleaned.contains("x.png"))
    }
}
