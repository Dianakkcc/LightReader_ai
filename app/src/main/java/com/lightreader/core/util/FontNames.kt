package com.lightreader.core.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Reads the family name out of a font binary (TTF/OTF) and rejects files that are obviously not
 * fonts. Used by the font manager so imported fonts show a real name instead of a file name.
 */
object FontNames {

    private val sfntVersions = setOf(0x00010000, 0x4F54544F, 0x74727565, 0x74746366, 0x774F4632)

    fun looksLikeFont(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.int
        return version in sfntVersions
    }

    /**
     * Extracts the typographic family name (name table id 1). Returns null for TrueType
     * collections or files whose name table cannot be read.
     */
    fun familyName(bytes: ByteArray): String? = runCatching {
        if (!looksLikeFont(bytes)) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.int
        if (version == 0x74746366) return null // TTC: name lookup differs, fall back to file name

        val tableCount = buffer.short.toInt() and 0xFFFF
        var nameOffset = -1
        var nameLength = 0
        repeat(tableCount) {
            if (buffer.remaining() < 16) return@repeat
            val tag = buffer.int
            buffer.int // checksum
            val offset = buffer.int
            val length = buffer.int
            if (tag == 0x6E616D65) { // 'name'
                nameOffset = offset
                nameLength = length
            }
        }
        if (nameOffset < 0 || nameOffset + nameLength > bytes.size) return null

        val table = ByteBuffer.wrap(bytes, nameOffset, nameLength).order(ByteOrder.BIG_ENDIAN)
        val format = table.short.toInt()
        if (format != 0 && format != 1) return null
        val count = table.short.toInt() and 0xFFFF
        val stringOffset = table.short.toInt() and 0xFFFF
        var fallback: String? = null
        repeat(count) {
            if (table.remaining() < 12) return@repeat
            val platformId = table.short.toInt() and 0xFFFF
            table.short // encoding id
            table.short // language id
            val nameId = table.short.toInt() and 0xFFFF
            val length = table.short.toInt() and 0xFFFF
            val offset = table.short.toInt() and 0xFFFF
            if (nameId != 1) return@repeat
            val start = nameOffset + stringOffset + offset
            if (start + length > bytes.size) return@repeat
            val raw = bytes.copyOfRange(start, start + length)
            val decoded = when (platformId) {
                0, 3 -> String(raw, Charsets.UTF_16BE)
                else -> String(raw, Charsets.ISO_8859_1)
            }.replace("\u0000", "").trim()
            if (decoded.isEmpty()) return@repeat
            if (platformId == 3) return decoded
            fallback = fallback ?: decoded
        }
        fallback
    }.getOrNull()
}
