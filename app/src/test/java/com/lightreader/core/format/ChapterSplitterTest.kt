package com.lightreader.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSplitterTest {

    @Test
    fun `splits at top level headings`() {
        val blocks = listOf(
            Block.Heading(1, listOf(Span("第一章"))),
            Block.Paragraph(listOf(Span("内容一"))),
            Block.Heading(1, listOf(Span("第二章"))),
            Block.Paragraph(listOf(Span("内容二"))),
        )
        val sections = ChapterSplitter.split(blocks, "无标题")
        assertEquals(2, sections.size)
        assertEquals("第一章", sections[0].title)
        assertEquals("第二章", sections[1].title)
    }

    @Test
    fun `splits long text without headings`() {
        val paragraph = Block.Paragraph(listOf(Span("长" .repeat(1000))))
        val blocks = List(40) { paragraph }
        val sections = ChapterSplitter.split(blocks, "长篇", maxChars = 5_000)
        assertTrue("应当切成多段，实际 ${sections.size}", sections.size > 1)
        assertEquals("长篇", sections.first().title)
        assertTrue(sections[1].title.contains("续"))
    }

    @Test
    fun `empty document still yields one section`() {
        val sections = ChapterSplitter.split(emptyList(), "空书")
        assertEquals(1, sections.size)
        val refs = ChapterSplitter.toChapterRefs(sections)
        assertEquals(1, refs.size)
        assertTrue(refs.first().weight >= 1)
    }

    @Test
    fun `deep headings do not start new chapters`() {
        val blocks = listOf(
            Block.Heading(1, listOf(Span("第一章"))),
            Block.Heading(3, listOf(Span("小节"))),
            Block.Paragraph(listOf(Span("内容"))),
        )
        val sections = ChapterSplitter.split(blocks, "无标题")
        assertEquals(1, sections.size)
        assertEquals(3, sections.first().blocks.size)
    }
}
