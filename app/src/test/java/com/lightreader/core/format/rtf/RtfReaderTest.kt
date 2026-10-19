package com.lightreader.core.format.rtf

import com.lightreader.core.format.Block
import com.lightreader.core.format.BlockHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtfReaderTest {

    @Test
    fun `reads plain paragraphs`() {
        val rtf = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times;}}\\f0\\fs24 Hello world\\par Second{ \\b bold\\b0 } end\\par}"
        val blocks = RtfReader(rtf).parse()
        assertEquals(2, blocks.size)
        val html = BlockHtml.render(blocks)
        assertTrue(html.contains("Hello world"))
        assertTrue(html.contains("<strong>bold</strong>"))
    }

    @Test
    fun `skips font table and info groups`() {
        val rtf = "{\\rtf1\\ansi{\\fonttbl{\\f0 Arial;}}{\\*\\generator Riched20 10.0;}仅正文\\par}"
        val blocks = RtfReader(rtf).parse()
        assertEquals(1, blocks.size)
        val text = BlockHtml.plainText(blocks)
        assertTrue(text.contains("仅正文"))
        assertTrue(!text.contains("Arial"))
        assertTrue(!text.contains("Riched20"))
    }

    @Test
    fun `decodes codepage 936 hex escapes`() {
        // 中文 encoded as GBK bytes: D6 D0 CE C4
        val rtf = "{\\rtf1\\ansi\\ansicpg936 \\'d6\\'d0\\'ce\\'c4\\par}"
        val text = BlockHtml.plainText(RtfReader(rtf).parse())
        assertTrue("expected 中文, got $text", text.contains("中文"))
    }

    @Test
    fun `decodes unicode escapes`() {
        val rtf = "{\\rtf1\\ansi\\uc1 \\u20013?\\u25991?\\par}"
        val text = BlockHtml.plainText(RtfReader(rtf).parse())
        assertTrue("expected 中文, got $text", text.contains("中文"))
    }

    @Test
    fun `applies alignment`() {
        val rtf = "{\\rtf1\\ansi\\qc 居中文本\\par}"
        val blocks = RtfReader(rtf).parse()
        val paragraph = blocks.first() as Block.Paragraph
        assertEquals(com.lightreader.core.model.TextAlignment.CENTER, paragraph.alignment)
    }
}
