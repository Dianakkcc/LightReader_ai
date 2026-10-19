package com.lightreader.core.format

import com.lightreader.core.model.TextAlignment
import com.lightreader.core.util.TextUtil

/**
 * Renders the block model to sanitised HTML for the reading surface.
 */
object BlockHtml {

    fun render(blocks: List<Block>): String {
        val out = StringBuilder()
        var index = 0
        while (index < blocks.size) {
            when (val block = blocks[index]) {
                is Block.ListItem -> {
                    // Consecutive list items share one <ul>/<ol>.
                    val ordered = block.ordered
                    val start = block.number
                    out.append(if (ordered) "<ol start=\"$start\">" else "<ul>")
                    while (index < blocks.size) {
                        val item = blocks[index] as? Block.ListItem ?: break
                        if (item.ordered != ordered) break
                        out.append("<li>").append(renderSpans(item.spans)).append("</li>")
                        index++
                    }
                    out.append(if (ordered) "</ol>" else "</ul>")
                    continue
                }

                else -> {
                    out.append(renderBlock(block))
                    index++
                }
            }
        }
        return out.toString()
    }

    private fun renderBlock(block: Block): String = when (block) {
        is Block.Paragraph -> {
            val style = block.alignment?.let { " style=\"text-align:${cssAlign(it)}\"" } ?: ""
            "<p$style>${renderSpans(block.spans)}</p>"
        }

        is Block.Heading -> {
            val level = block.level.coerceIn(1, 6)
            "<h$level>${renderSpans(block.spans)}</h$level>"
        }

        is Block.ListItem -> "<li>${renderSpans(block.spans)}</li>"

        is Block.Quote -> "<blockquote>${renderSpans(block.spans)}</blockquote>"

        is Block.Code -> "<pre><code>${TextUtil.escapeHtml(block.text)}</code></pre>"

        is Block.ImageBlock -> {
            val width = block.widthPx?.let { " width=\"$it\"" } ?: ""
            val height = block.heightPx?.let { " height=\"$it\"" } ?: ""
            val alt = TextUtil.escapeHtml(block.alt ?: "")
            "<figure><img src=\"${TextUtil.escapeHtml(block.src)}\" alt=\"$alt\"$width$height loading=\"lazy\"></figure>"
        }

        is Block.Table -> renderTable(block)

        Block.Divider -> "<hr>"
    }

    private fun renderTable(table: Block.Table): String {
        val out = StringBuilder("<table>")
        for (row in table.rows) {
            out.append("<tr>")
            for (cell in row.cells) {
                val tag = if (cell.header) "th" else "td"
                val span = buildString {
                    if (cell.colSpan > 1) append(" colspan=\"${cell.colSpan}\"")
                    if (cell.rowSpan > 1) append(" rowspan=\"${cell.rowSpan}\"")
                }
                out.append("<$tag$span>")
                cell.blocks.forEach { out.append(renderBlock(it)) }
                out.append("</$tag>")
            }
            out.append("</tr>")
        }
        return out.append("</table>").toString()
    }

    fun renderSpans(spans: List<Span>): String {
        if (spans.isEmpty()) return ""
        val out = StringBuilder()
        for (span in spans) {
            out.append(renderSpan(span))
        }
        return out.toString()
    }

    private fun renderSpan(span: Span): String {
        var html = if (span.code) {
            "<code>${TextUtil.escapeHtml(span.text)}</code>"
        } else {
            TextUtil.escapeHtml(span.text).replace("\n", "<br>")
        }
        if (span.bold) html = "<strong>$html</strong>"
        if (span.italic) html = "<em>$html</em>"
        if (span.underline) html = "<u>$html</u>"
        if (span.strikethrough) html = "<s>$html</s>"
        if (span.superscript) html = "<sup>$html</sup>"
        if (span.subscript) html = "<sub>$html</sub>"
        if (span.fontSizeRatio != 1f) {
            val percent = (span.fontSizeRatio * 100).toInt().coerceIn(50, 300)
            html = "<span style=\"font-size:${percent}%\">$html</span>"
        }
        if (!span.link.isNullOrBlank()) {
            val href = TextUtil.escapeHtml(span.link)
            html = "<a href=\"$href\">$html</a>"
        }
        return html
    }

    private fun cssAlign(alignment: TextAlignment): String = when (alignment) {
        TextAlignment.START -> "left"
        TextAlignment.JUSTIFY -> "justify"
        TextAlignment.CENTER -> "center"
        TextAlignment.END -> "right"
    }

    /** Plain text of a block list, used for search and progress estimation. */
    fun plainText(blocks: List<Block>): String {
        val out = StringBuilder()
        fun walk(list: List<Block>) {
            for (block in list) {
                when (block) {
                    is Block.Paragraph -> out.append(spanText(block.spans)).append('\n')
                    is Block.Heading -> out.append(spanText(block.spans)).append('\n')
                    is Block.ListItem -> out.append(spanText(block.spans)).append('\n')
                    is Block.Quote -> out.append(spanText(block.spans)).append('\n')
                    is Block.Code -> out.append(block.text).append('\n')
                    is Block.ImageBlock -> out.append(block.alt.orEmpty()).append('\n')
                    is Block.Table -> {
                        block.rows.forEach { row ->
                            row.cells.forEach { cell -> walk(cell.blocks) }
                            out.append('\n')
                        }
                    }

                    Block.Divider -> Unit
                }
            }
        }
        walk(blocks)
        return out.toString()
    }

    private fun spanText(spans: List<Span>): String = spans.joinToString("") { it.text }
}
