package com.lightreader.core.util

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Detects the charset of plain text files (and of the text payload of other formats).
 *
 * Order of evidence: byte order mark, NUL-byte pattern for UTF-16 without BOM, strict UTF-8
 * validation, then a scoring based comparison between GB18030 and Big5 - the two encodings
 * Chinese legacy files realistically use - finishing with windows-1252 as a total fallback.
 */
object EncodingDetector {

    private const val SAMPLE_SIZE = 64 * 1024

    data class Detection(val charset: Charset, val bomLength: Int)

    fun detect(bytes: ByteArray): Detection {
        val bom = detectBom(bytes)
        if (bom != null) return bom

        val sample = if (bytes.size > SAMPLE_SIZE) bytes.copyOf(SAMPLE_SIZE) else bytes
        utf16WithoutBom(sample)?.let { return Detection(it, 0) }
        if (isValidUtf8(sample)) {
            return Detection(StandardCharsets.UTF_8, 0)
        }

        // Legacy encodings: compare how plausible each decoding looks and keep the best one.
        var best: Charset = UTF8
        var bestScore = Double.NEGATIVE_INFINITY
        for ((charset, weight) in candidates) {
            val score = scoreDecoding(sample, charset) * weight
            if (score > bestScore) {
                bestScore = score
                best = charset
            }
        }
        return Detection(best, 0)
    }

    /** Detects the charset and returns a stream positioned right after the BOM (if any). */
    fun detectStream(stream: InputStream): Pair<Charset, InputStream> {
        val buffered = if (stream is BufferedInputStream) stream else BufferedInputStream(stream, SAMPLE_SIZE)
        buffered.mark(SAMPLE_SIZE)
        val sample = ByteArray(SAMPLE_SIZE)
        var read = 0
        while (read < SAMPLE_SIZE) {
            val count = buffered.read(sample, read, SAMPLE_SIZE - read)
            if (count <= 0) break
            read += count
        }
        buffered.reset()
        val detection = detect(if (read == SAMPLE_SIZE) sample else sample.copyOf(read))
        if (detection.bomLength > 0) {
            buffered.skip(detection.bomLength.toLong())
        }
        return detection.charset to buffered
    }

    fun detectBom(bytes: ByteArray): Detection? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Detection(StandardCharsets.UTF_8, 3)
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
            bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()
        ) {
            return Detection(Charset.forName("UTF-32LE"), 4)
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
            bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte()
        ) {
            return Detection(Charset.forName("UTF-32BE"), 4)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Detection(StandardCharsets.UTF_16LE, 2)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Detection(StandardCharsets.UTF_16BE, 2)
        }
        return null
    }

    /**
     * UTF-16 without a BOM: every ASCII character produces a NUL byte on a fixed parity
     * (the second byte for little endian, the first for big endian).
     */
    private fun utf16WithoutBom(sample: ByteArray): Charset? {
        if (sample.size < 16) return null
        val limit = minOf(sample.size, 4096)
        var lowZeros = 0
        var highZeros = 0
        var pairs = 0
        var index = 0
        while (index + 1 < limit) {
            if (sample[index].toInt() == 0) highZeros++
            if (sample[index + 1].toInt() == 0) lowZeros++
            pairs++
            index += 2
        }
        if (pairs == 0) return null
        val lowRatio = lowZeros.toDouble() / pairs
        val highRatio = highZeros.toDouble() / pairs
        return when {
            lowRatio > UTF16_NUL_THRESHOLD && lowRatio > highRatio ->
                StandardCharsets.UTF_16LE

            highRatio > UTF16_NUL_THRESHOLD && highRatio > lowRatio ->
                StandardCharsets.UTF_16BE

            else -> null
        }
    }

    fun isValidUtf8(sample: ByteArray): Boolean {
        if (sample.isEmpty()) return true
        // A sample cut out of a larger file may end in the middle of a multi byte character,
        // so up to three trailing bytes are allowed to be incomplete. Everything before that
        // still has to decode cleanly.
        for (dropped in 0..3) {
            if (dropped >= sample.size) break
            val slice = if (dropped == 0) sample else sample.copyOf(sample.size - dropped)
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                decoder.decode(ByteBuffer.wrap(slice))
                return true
            } catch (_: CharacterCodingException) {
                // try again with one more byte removed
            }
        }
        return false
    }

    private fun scoreDecoding(sample: ByteArray, charset: Charset): Double {
        val decoder: CharsetDecoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(sample)).toString()
        } catch (_: CharacterCodingException) {
            return -1.0
        }
        var score = 0.0
        for (char in text) {
            score += when {
                char == '\uFFFD' -> -8.0
                char in '\u4E00'..'\u9FFF' -> 2.0
                char in '\u3000'..'\u303F' -> 2.0
                char in '\uFF00'..'\uFFEF' -> 1.5
                char == '\n' || char == '\r' || char == '\t' -> 1.0
                char.code in 0x20..0x7E -> 1.0
                char.code < 0x20 -> -4.0
                else -> -1.0
            }
        }
        return score
    }

    private val UTF8: Charset = StandardCharsets.UTF_8

    private val candidates: List<Pair<Charset, Double>> = listOf(
        Charset.forName("GB18030") to 1.0,
        Charset.forName("Big5") to 1.0,
        StandardCharsets.UTF_16LE to 0.92,
        StandardCharsets.UTF_16BE to 0.92,
        Charset.forName("windows-1252") to 0.5,
    )

    private const val UTF16_NUL_THRESHOLD = 0.25
}
