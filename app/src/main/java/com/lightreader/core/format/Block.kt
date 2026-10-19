package com.lightreader.core.format

import com.lightreader.core.model.TextAlignment

/**
 * Format independent block model. Every reflowable parser (TXT, DOCX, RTF, HTML, Markdown)
 * produces this, and one renderer turns it into the HTML the reader displays.
 */
sealed interface Block {
    data class Paragraph(
        val spans: List<Span>,
        val alignment: TextAlignment? = null,
    ) : Block

    data class Heading(val level: Int, val spans: List<Span>) : Block

    data class ListItem(
        val spans: List<Span>,
        val ordered: Boolean = false,
        val number: Int = 0,
        val level: Int = 0,
    ) : Block

    data class Quote(val spans: List<Span>) : Block

    data class Code(val text: String, val language: String? = null) : Block

    data class ImageBlock(
        val src: String,
        val alt: String? = null,
        val widthPx: Int? = null,
        val heightPx: Int? = null,
    ) : Block

    data class Table(val rows: List<Row>) : Block {
        data class Row(val cells: List<Cell>)
        data class Cell(
            val blocks: List<Block>,
            val header: Boolean = false,
            val colSpan: Int = 1,
            val rowSpan: Int = 1,
        )
    }

    data object Divider : Block
}

data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val fontSizeRatio: Float = 1f,
    val link: String? = null,
) {
    val isPlain: Boolean
        get() = !bold && !italic && !underline && !strikethrough && !code &&
            !superscript && !subscript && fontSizeRatio == 1f && link == null
}

object SpanBuilder {
    fun of(text: String): Span = Span(text)
    fun plain(text: String): List<Span> = if (text.isEmpty()) emptyList() else listOf(Span(text))
}
