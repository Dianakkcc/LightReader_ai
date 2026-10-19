package com.lightreader.core.format.rtf

import android.content.Context
import com.lightreader.core.format.Block
import com.lightreader.core.format.BlockHtml
import com.lightreader.core.format.ChapterSplitter
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.Span
import com.lightreader.core.format.html.HtmlBasedDocument
import com.lightreader.core.format.toParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.OpenedDocument
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * RTF reader.
 *
 * Covers what text documents actually use: character formatting (bold, italic, underline,
 * strike, super/subscript, font size), paragraph alignment, tab and line breaks, lists rendered
 * as plain text, code page dependent hex escapes (`\'hh`), Unicode escapes (`\uNNNN`) and the
 * destination groups that must be skipped (font table, colour table, stylesheet, pictures,
 * headers, footers).
 */
class RtfParser : DocumentParser {

    override val format: BookFormat = BookFormat.RTF

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.RTF

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument =
        withContext(Dispatchers.IO) {
            try {
                val bytes = source.openInput(context.applicationContext).use { it.readBytes() }
                val blocks = RtfReader(String(bytes, Charsets.ISO_8859_1)).parse()
                val title = source.displayName.substringBeforeLast('.').ifBlank { source.displayName }
                val sections = ChapterSplitter.split(blocks, title).map { section ->
                    val html = BlockHtml.render(section.blocks)
                    HtmlBasedDocument.Section(section.title, section.level) { html }
                }
                Logs.d("RTF 打开：$title 段落=${blocks.size}")
                HtmlBasedDocument(
                    format = BookFormat.RTF,
                    metadata = DocumentMetadata(title = title),
                    sections = sections,
                )
            } catch (throwable: Throwable) {
                throw throwable.toParseException("无法打开 RTF 文件")
            }
        }
}

/** Tokeniser + converter for RTF. The input is byte-per-char (ISO-8859-1) text. */
internal class RtfReader(private val source: String) {

    private data class Format(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val superscript: Boolean = false,
        val subscript: Boolean = false,
        val halfPoints: Int = 0,
    )

    private class Run(val format: Format) {
        val text = StringBuilder()
    }

    private var position = 0
    private var format = Format()
    private var alignment: TextAlignment? = null
    private var codePage = 1252
    private var unicodeSkip = 1
    private var skipCharacters = 0

    private val blocks = ArrayList<Block>()
    private val runs = ArrayList<Run>()
    private val hexBytes = ByteArrayOutputStream()

    fun parse(): List<Block> {
        val formatStack = ArrayDeque<Format>()
        val alignmentStack = ArrayDeque<TextAlignment?>()

        while (position < source.length) {
            when (val char = source[position]) {
                '{' -> {
                    position++
                    if (peekIsSkippableDestination()) {
                        skipGroup()
                    } else {
                        formatStack.addLast(format)
                        alignmentStack.addLast(alignment)
                    }
                }

                '}' -> {
                    position++
                    flushHex()
                    if (formatStack.isNotEmpty()) format = formatStack.removeLast()
                    if (alignmentStack.isNotEmpty()) alignment = alignmentStack.removeLast()
                }

                '\\' -> readControl()

                '\r', '\n' -> {
                    position++
                    flushHex()
                }

                else -> {
                    position++
                    flushHex()
                    appendText(char.toString())
                }
            }
        }
        flushHex()
        flushParagraph()
        return blocks
    }

    private fun peekIsSkippableDestination(): Boolean {
        var index = position
        while (index < source.length && source[index] == ' ') index++
        if (index >= source.length || source[index] != '\\') return false
        if (index + 1 < source.length && source[index + 1] == '*') return true
        return readWordAt(index + 1) in skippedDestinations
    }

    private fun readWordAt(index: Int): String {
        var cursor = index
        val builder = StringBuilder()
        while (cursor < source.length && source[cursor].isLetter()) {
            builder.append(source[cursor])
            cursor++
        }
        return builder.toString()
    }

    private fun skipGroup() {
        var nesting = 0
        while (position < source.length) {
            when (source[position]) {
                '{' -> nesting++
                '}' -> {
                    if (nesting == 0) {
                        position++
                        return
                    }
                    nesting--
                }
            }
            position++
        }
    }

    private fun readControl() {
        position++ // backslash
        if (position >= source.length) return
        val char = source[position]
        if (!char.isLetter()) {
            position++
            when (char) {
                '\\', '{', '}' -> {
                    flushHex()
                    appendText(char.toString())
                }

                '\'' -> {
                    val hex = source.substring(
                        position.coerceAtMost(source.length),
                        (position + 2).coerceAtMost(source.length),
                    )
                    position += 2
                    val value = hex.toIntOrNull(16)
                    if (value != null) hexBytes.write(value) else flushHex()
                }

                '~' -> {
                    flushHex()
                    appendText("\u00A0")
                }

                '\n', '\r' -> {
                    flushHex()
                    flushParagraph()
                }

                else -> Unit
            }
            return
        }

        val word = StringBuilder()
        while (position < source.length && source[position].isLetter()) {
            word.append(source[position])
            position++
        }
        var negative = false
        if (position < source.length && source[position] == '-') {
            negative = true
            position++
        }
        val number = StringBuilder()
        while (position < source.length && source[position].isDigit()) {
            number.append(source[position])
            position++
        }
        if (position < source.length && source[position] == ' ') position++

        val parameter = number.toString().toIntOrNull()?.let { if (negative) -it else it }
        applyControl(word.toString(), parameter)
    }

    private fun applyControl(word: String, parameter: Int?) {
        when (word) {
            "par" -> {
                flushHex()
                flushParagraph()
            }

            "line" -> {
                flushHex()
                appendText("\n")
            }

            "tab" -> {
                flushHex()
                appendText("\t")
            }

            "pard" -> alignment = null
            "ql" -> alignment = TextAlignment.START
            "qj" -> alignment = TextAlignment.JUSTIFY
            "qc" -> alignment = TextAlignment.CENTER
            "qr" -> alignment = TextAlignment.END

            "b" -> format = format.copy(bold = parameter != 0)
            "i" -> format = format.copy(italic = parameter != 0)
            "ul" -> format = format.copy(underline = parameter != 0)
            "ulnone" -> format = format.copy(underline = false)
            "strike" -> format = format.copy(strike = parameter != 0)
            "super" -> format = format.copy(superscript = true, subscript = false)
            "sub" -> format = format.copy(subscript = true, superscript = false)
            "nosupersub" -> format = format.copy(superscript = false, subscript = false)
            "fs" -> if (parameter != null && parameter > 0) format = format.copy(halfPoints = parameter)
            "plain" -> format = Format()
            "ansicpg" -> if (parameter != null) codePage = parameter
            "uc" -> if (parameter != null) unicodeSkip = parameter.coerceIn(0, 4)

            "u" -> {
                flushHex()
                if (parameter != null) {
                    val value = if (parameter < 0) parameter + 65536 else parameter
                    appendText(value.toChar().toString())
                    skipCharacters = unicodeSkip
                }
            }

            else -> Unit
        }
    }

    private fun appendText(raw: String) {
        if (raw.isEmpty()) return
        var text = raw
        if (skipCharacters > 0) {
            val skipped = text.take(skipCharacters)
            skipCharacters -= skipped.length
            text = text.drop(skipped.length)
            if (text.isEmpty()) return
        }
        val last = runs.lastOrNull()
        if (last != null && last.format == format) {
            last.text.append(text)
        } else {
            Run(format).also { it.text.append(text); runs.add(it) }
        }
    }

    private fun flushHex() {
        if (hexBytes.size() == 0) return
        val bytes = hexBytes.toByteArray()
        hexBytes.reset()
        val decoder = charsetForCodePage(codePage).newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        appendText(decoder.decode(ByteBuffer.wrap(bytes)).toString())
    }

    private fun flushParagraph() {
        flushHex()
        if (runs.isEmpty()) return
        val spans = runs.map { run ->
            Span(
                text = run.text.toString(),
                bold = run.format.bold,
                italic = run.format.italic,
                underline = run.format.underline,
                strikethrough = run.format.strike,
                superscript = run.format.superscript,
                subscript = run.format.subscript,
                fontSizeRatio = if (run.format.halfPoints > 0) {
                    (run.format.halfPoints / 24f).coerceIn(0.5f, 3f)
                } else {
                    1f
                },
            )
        }.toMutableList()
        runs.clear()

        // Trim the paragraph as a whole so no stray spaces appear around the text.
        if (spans.isNotEmpty()) {
            spans[0] = spans[0].copy(text = spans[0].text.trimStart())
            val lastIndex = spans.lastIndex
            spans[lastIndex] = spans[lastIndex].copy(text = spans[lastIndex].text.trimEnd())
        }
        val meaningful = spans.filter { it.text.isNotEmpty() }
        if (meaningful.isEmpty()) return
        blocks.add(Block.Paragraph(meaningful, alignment))
    }

    private fun charsetForCodePage(codePage: Int): Charset = when (codePage) {
        932 -> Charset.forName("Shift_JIS")
        936 -> Charset.forName("GBK")
        949 -> Charset.forName("EUC-KR")
        950 -> Charset.forName("Big5")
        65001 -> Charsets.UTF_8
        else -> runCatching { Charset.forName("windows-$codePage") }
            .getOrDefault(Charsets.ISO_8859_1)
    }

    private companion object {
        val skippedDestinations = setOf(
            "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "themedata",
            "header", "footer", "headerl", "headerr", "footerl", "footerr", "footnote",
            "xmlnstbl", "listtable", "listoverridetable", "revtbl", "rsidtbl", "generator",
            "filetbl", "datastore", "latentstyles", "mmathpr", "shppict", "nonshppict",
            "bkmkstart", "bkmkend", "datafield", "annotation", "atnauthor", "atnid",
        )
    }
}
