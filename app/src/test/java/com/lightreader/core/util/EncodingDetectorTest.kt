package com.lightreader.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class EncodingDetectorTest {

    @Test
    fun `detects utf8 without bom`() {
        val text = "这是一段中文文本，用来测试编码识别。\n" + "The quick brown fox jumps over the lazy dog.\n"
        val detection = EncodingDetector.detect(text.toByteArray(StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, detection.charset)
        assertEquals(0, detection.bomLength)
    }

    @Test
    fun `respects utf8 bom`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "中文".toByteArray(StandardCharsets.UTF_8)
        val detection = EncodingDetector.detect(bytes)
        assertEquals(StandardCharsets.UTF_8, detection.charset)
        assertEquals(3, detection.bomLength)
    }

    @Test
    fun `detects utf16 with bom`() {
        val text = "第一章 测试章节\n正文内容在这里。"
        val detection = EncodingDetector.detect(text.toByteArray(StandardCharsets.UTF_16LE)
            .let { byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it })
        assertEquals(StandardCharsets.UTF_16LE, detection.charset)
        assertEquals(2, detection.bomLength)
    }

    @Test
    fun `detects utf16 without bom`() {
        val text = "这是一段没有 BOM 的 UTF-16 文本，用来验证推断逻辑。"
        val detection = EncodingDetector.detect(text.toByteArray(StandardCharsets.UTF_16LE))
        assertEquals(StandardCharsets.UTF_16LE, detection.charset)
    }

    @Test
    fun `detects gb18030 chinese text`() {
        val text = "这是一段 GBK 编码的中文文本，用于测试编码自动识别功能。"
        val bytes = text.toByteArray(Charset.forName("GB18030"))
        val detection = EncodingDetector.detect(bytes)
        val decoded = String(bytes, detection.charset)
        assertTrue(
            "GB18030 content should round trip, got ${detection.charset}",
            decoded.contains("编码"),
        )
    }

    @Test
    fun `plain ascii stays utf8`() {
        val detection = EncodingDetector.detect("hello world".toByteArray())
        assertEquals(StandardCharsets.UTF_8, detection.charset)
    }
}
