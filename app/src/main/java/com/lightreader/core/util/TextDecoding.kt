package com.lightreader.core.util

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes an in-memory text payload, honouring, in order: byte order mark, XML declaration,
 * HTML meta charset, then statistical detection.
 */
object TextDecoding {

    private val xmlEncoding = Regex("""<\?xml[^>]*encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val metaCharset = Regex(
        """<meta[^>]*charset\s*=\s*["']?\s*([A-Za-z0-9_\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun decode(bytes: ByteArray, defaultCharset: Charset = Charsets.UTF_8): String {
        val bom = EncodingDetector.detectBom(bytes)
        if (bom != null) {
            return decodeWith(bytes, bom.bomLength, bom.charset)
        }
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val declared = xmlEncoding.find(head)?.groupValues?.get(1)
            ?: metaCharset.find(head)?.groupValues?.get(1)
        if (declared != null) {
            val charset = runCatching { Charset.forName(declared.trim()) }.getOrNull()
            if (charset != null) return decodeWith(bytes, 0, charset)
        }
        val detection = EncodingDetector.detect(bytes)
        if (detection.charset == Charsets.UTF_8 || EncodingDetector.isValidUtf8(bytes)) {
            return decodeWith(bytes, detection.bomLength, defaultCharset)
        }
        return decodeWith(bytes, detection.bomLength, detection.charset)
    }

    private fun decodeWith(bytes: ByteArray, offset: Int, charset: Charset): String {
        if (bytes.isEmpty() || offset >= bytes.size) return ""
        val payload = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(payload).toString()
    }
}
