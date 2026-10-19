package com.lightreader.core.format.docx

import com.lightreader.core.format.Block
import com.lightreader.core.format.Span
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.util.Logs
import org.jsoup.nodes.Element

/**
 * Converts WordprocessingML into the reader's block model.
 *
 * Supported: headings and their style based detection, bold/italic/underline/strike, superscript
 * and subscript, font size, colours are dropped, paragraph alignment, ordered and unordered lists
 * (including nesting), hyperlinks, inline images and tables.
 */
internal class DocxBlocks(
    private val relations: DocxRelations,
    private val numbering: DocxNumbering,
    private val styleNames: Map<String, String>,
    private val bookKey: String,
) {

    private val headingPattern = Regex("^(?:heading|标题|標題)\\s*([1-6])$", RegexOption.IGNORE_CASE)

    fun convert(body: Element): List<Block> = blockChildrenOf(body, listLevel = 0, ancestors = emptyList())

    private fun blockChildrenOf(
        parent: Element,
        listLevel: Int,
        ancestors: List<String>,
    ): List<Block> {
        val blocks = ArrayList<Block>()
        for (child in parent.children()) {
            when (child.localName()) {
                "p" -> blocks.addAll(paragraph(child, ancestors))
                "tbl" -> blocks.add(table(child))
                "sdt" -> blocks.addAll(sdt(child, ancestors))
                "sdtcontent" -> blocks.addAll(blockChildrenOf(child, listLevel, ancestors))
                "tc" -> blocks.addAll(blockChildrenOf(child, listLevel, ancestors))
                "tr", "tblgrid", "tblpr", "sectpr", "body", "tblprEx" -> Unit
                else -> {
                    if (child.children().any { it.localName() in setOf("p", "tbl") }) {
                        blocks.addAll(blockChildrenOf(child, listLevel, ancestors))
                    }
                }
            }
        }
        return blocks
    }

    private fun sdt(element: Element, ancestors: List<String>): List<Block> {
        val content = element.localChild("sdtContent") ?: return emptyList()
        return blockChildrenOf(content, 0, ancestors)
    }

    private fun paragraph(element: Element, ancestors: List<String>): List<Block> {
        val properties = element.localChild("pPr")
        val styleId = properties?.localChild("pStyle")?.ooxmlVal()?.ifBlank { null }
        val styleName = styleId?.let { styleNames[it] }
        val headingLevel = headingLevelOf(styleId, styleName)

        val alignment = when (
            properties?.localChild("jc")?.ooxmlVal()?.lowercase()
        ) {
            "center" -> TextAlignment.CENTER
            "right", "end" -> TextAlignment.END
            "both", "justify", "distribute" -> TextAlignment.JUSTIFY
            else -> null
        }

        val numPr = properties?.localChild("numPr")
        val numId = numPr?.localChild("numId")?.ooxmlVal()?.ifBlank { null }
        val ilvl = numPr?.localChild("ilvl")?.ooxmlVal()?.toIntOrNull() ?: 0

        val linkId = element.attr("r:id").takeIf { it.isNotBlank() }
            ?: element.firstByLocal("hyperlink")?.attr("r:id")?.takeIf { it.isNotBlank() }
        val link = linkId?.let { relations.target(it) }?.takeIf { !it.startsWith("#") }

        val spans = ArrayList<Span>()
        collectSpans(element, Span(""), link, spans, depth = 0)

        val blocks = ArrayList<Block>()
        if (spans.any { it.text.isNotBlank() }) {
            when {
                headingLevel != null -> blocks.add(Block.Heading(headingLevel, spans))
                numId != null -> blocks.add(
                    Block.ListItem(
                        spans = spans,
                        ordered = numbering.isOrdered(numId, ilvl),
                        number = 1,
                        level = ilvl,
                    ),
                )
                else -> blocks.add(Block.Paragraph(spans, alignment))
            }
        }

        element.allByLocal("drawing").forEach { drawing ->
            imageFromDrawing(drawing)?.let { blocks.add(it) }
        }
        element.allByLocal("pict").forEach { pict ->
            imageFromPict(pict)?.let { blocks.add(it) }
        }
        return blocks
    }

    private fun headingLevelOf(styleId: String?, styleName: String?): Int? {
        listOfNotNull(styleName, styleId).forEach { candidate ->
            headingPattern.find(candidate.trim())?.let { match ->
                return match.groupValues[1].toIntOrNull()
            }
        }
        return null
    }

    private fun collectSpans(
        element: Element,
        inherited: Span,
        link: String?,
        out: MutableList<Span>,
        depth: Int,
    ) {
        if (depth > 6) return
        for (child in element.children()) {
            when (child.localName()) {
                "r" -> out.addAll(runSpans(child, inherited, link))
                "hyperlink" -> {
                    val relId = child.attr("r:id").takeIf { it.isNotBlank() }
                    val target = relId?.let { relations.target(it) }
                    collectSpans(child, inherited, target ?: link, out, depth + 1)
                }

                "ins", "smarttag", "sdtcontent" -> collectSpans(child, inherited, link, out, depth + 1)
                "del" -> Unit
                else -> {
                    if (child.children().isNotEmpty()) {
                        collectSpans(child, inherited, link, out, depth + 1)
                    }
                }
            }
        }
    }

    private fun runSpans(run: Element, inherited: Span, link: String?): List<Span> {
        val properties = run.localChild("rPr")
        val bold = properties?.localChild("b")?.let { element ->
            val value = element.ooxmlVal()
            value != "0" && !value.equals("false", true)
        }
            ?: false
        val italic = properties?.localChild("i")?.let { element ->
            val value = element.ooxmlVal()
            value != "0" && !value.equals("false", true)
        }
            ?: false
        val underline = properties?.localChild("u")?.let { it.ooxmlVal().lowercase() != "none" } ?: false
        val strike = properties?.localChild("strike") != null
        val vertical = properties?.localChild("vertAlign")?.ooxmlVal()?.lowercase()
        val halfPoints = properties?.localChild("sz")?.ooxmlVal()?.toFloatOrNull()
        val style = Span(
            text = "",
            bold = bold || inherited.bold,
            italic = italic || inherited.italic,
            underline = underline || inherited.underline,
            strikethrough = strike || inherited.strikethrough,
            superscript = vertical == "superscript",
            subscript = vertical == "subscript",
            fontSizeRatio = halfPoints?.let { it / 21f } ?: inherited.fontSizeRatio,
            link = link,
        )

        val spans = ArrayList<Span>()
        for (child in run.children()) {
            when (child.localName()) {
                "t" -> {
                    val text = child.wholeText()
                    if (text.isNotEmpty()) spans.add(style.copy(text = text))
                }

                "tab" -> spans.add(style.copy(text = "\t"))
                "br" -> spans.add(style.copy(text = "\n"))
                "cr" -> spans.add(style.copy(text = "\n"))
                "drawing" -> Unit
                "pict" -> Unit
                else -> Unit
            }
        }
        return spans
    }

    private fun imageFromDrawing(drawing: Element): Block.ImageBlock? {
        val blip = drawing.firstByLocal("blip") ?: return null
        val relId = blip.attr("r:embed").takeIf { it.isNotBlank() }
            ?: blip.attr("r:link").takeIf { it.isNotBlank() }
        val url = relations.imageUrl(relId, bookKey) ?: return null
        val extent = drawing.firstByLocal("extent")
        val width = extent?.attr("cx")?.toLongOrNull()?.let { (it / 9525L).toInt() }
        return Block.ImageBlock(src = url, widthPx = width?.takeIf { it in 8..4000 })
    }

    private fun imageFromPict(pict: Element): Block.ImageBlock? {
        val data = pict.firstByLocal("imagedata") ?: return null
        val relId = data.attr("r:id").takeIf { it.isNotBlank() } ?: return null
        val url = relations.imageUrl(relId, bookKey) ?: return null
        return Block.ImageBlock(src = url)
    }

    private fun table(element: Element): Block {
        val rows = ArrayList<Block.Table.Row>()
        element.localChildren("tr").forEach { row ->
            val cells = ArrayList<Block.Table.Cell>()
            row.localChildren("tc").forEach { cell ->
                val properties = cell.localChild("tcPr")
                val span = properties?.localChild("gridSpan")?.ooxmlVal()?.toIntOrNull() ?: 1
                val blocks = blockChildrenOf(cell, 0, emptyList())
                cells.add(
                    Block.Table.Cell(
                        blocks = blocks,
                        header = properties?.localChild("shd") != null && rows.isEmpty(),
                        colSpan = span.coerceIn(1, 16),
                    ),
                )
            }
            if (cells.isNotEmpty()) rows.add(Block.Table.Row(cells))
        }
        if (rows.isEmpty()) {
            Logs.d("跳过空表格")
        }
        return Block.Table(rows)
    }
}
