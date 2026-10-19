package com.lightreader.core.format

import android.content.Context
import com.lightreader.core.model.BookFormat
import com.lightreader.core.util.Logs
import java.io.InputStream

/**
 * Detects a book format from, in order: file extension, MIME type, magic bytes. The magic byte
 * pass is what makes "open with" work for files whose provider reports `application/octet-stream`.
 */
object FormatDetector {

    fun detect(context: Context, source: DocumentSource): BookFormat {
        val byName = source.format
        // A `.zip` can be an EPUB, a DOCX or a comic, so it always gets sniffed as well.
        if (byName != BookFormat.UNKNOWN && byName != BookFormat.TXT && byName != BookFormat.COMIC) {
            return byName
        }

        val sniffed = runCatching { sniff(context, source) }
            .onFailure { Logs.w("文件头识别失败：${it.message}", it) }
            .getOrNull()
        if (sniffed != null) return sniffed

        return byName
    }

    private fun sniff(context: Context, source: DocumentSource): BookFormat? {
        val header = source.openInput(context).use { readHeader(it, 1024) }
        if (header.isEmpty()) return null

        // PDF
        if (header.size >= 4 &&
            header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()
        ) {
            return BookFormat.PDF
        }
        // RTF
        if (header.size >= 5 &&
            header[0] == '{'.code.toByte() && header[1] == '\\'.code.toByte() &&
            header[2] == 'r'.code.toByte() && header[3] == 't'.code.toByte() &&
            header[4] == 'f'.code.toByte()
        ) {
            return BookFormat.RTF
        }
        // JPEG / PNG / WebP / GIF / BMP
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) {
            return BookFormat.IMAGE
        }
        if (header.size >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() &&
            header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte()
        ) {
            return BookFormat.IMAGE
        }
        if (header.size >= 12 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) {
            return BookFormat.IMAGE
        }
        if (header.size >= 6 && String(header, 0, 4, Charsets.US_ASCII) == "GIF8") {
            return BookFormat.IMAGE
        }
        if (header.size >= 2 &&
            header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()
        ) {
            return BookFormat.IMAGE
        }
        // HTML
        val head = String(header, Charsets.ISO_8859_1).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (head.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            head.startsWith("<html", ignoreCase = true) ||
            head.startsWith("<?xml", ignoreCase = true) && head.contains("<html", ignoreCase = true)
        ) {
            return BookFormat.HTML
        }
        // ZIP container: EPUB vs DOCX.
        if (header.size >= 4 &&
            header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2] == 3.toByte() && header[3] == 4.toByte()
        ) {
            return runCatching { sniffZip(context, source) }.getOrNull()
        }
        return null
    }

    private fun sniffZip(context: Context, source: DocumentSource): BookFormat? {
        java.util.zip.ZipInputStream(source.openInput(context)).use { zip ->
            var entries = 0
            while (entries < 64) {
                val entry = zip.nextEntry ?: break
                entries++
                when (entry.name.lowercase()) {
                    "mimetype" -> {
                        val buffer = ByteArray(64)
                        val count = zip.read(buffer)
                        if (count > 0 &&
                            String(buffer, 0, count).trim().equals("application/epub+zip", ignoreCase = true)
                        ) {
                            return BookFormat.EPUB
                        }
                    }

                    "[content_types].xml" -> return BookFormat.DOCX
                    "word/document.xml" -> return BookFormat.DOCX
                }
                // Anything else that carries pictures is a comic archive.
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (extension in comicImageExtensions) return BookFormat.COMIC
            }
        }
        return null
    }

    private val comicImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

    private fun readHeader(stream: InputStream, max: Int): ByteArray {
        val buffer = ByteArray(max)
        var read = 0
        while (read < max) {
            val count = stream.read(buffer, read, max - read)
            if (count <= 0) break
            read += count
        }
        return if (read == max) buffer else buffer.copyOf(read)
    }
}
