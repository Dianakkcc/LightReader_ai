package com.lightreader.core.format.docx

import com.lightreader.core.format.Block
import com.lightreader.core.format.Span
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.util.ZipPaths
import org.jsoup.nodes.Element

/** Local name of an element, ignoring the OOXML namespace prefix (`w:p` → `p`). */
internal fun Element.localName(): String = tagName().substringAfter(':').lowercase()

internal fun Element.localChild(name: String): Element? =
    children().firstOrNull { it.localName() == name.lowercase() }

internal fun Element.localChildren(name: String): List<Element> =
    children().filter { it.localName() == name.lowercase() }

/** Descendant search that ignores the OOXML namespace prefix (`w:drawing` == `a:blip` style). */
internal fun Element.allByLocal(name: String): List<Element> =
    allElements.filter { it.localName() == name.lowercase() }

internal fun Element.firstByLocal(name: String): Element? =
    allElements.firstOrNull { it.localName() == name.lowercase() }

/** OOXML writes attributes as `w:val`; tolerate documents without the prefix. */
internal fun Element.ooxmlVal(): String = attr("w:val").ifBlank { attr("val") }

/** Numbering information extracted from `word/numbering.xml`. */
internal class DocxNumbering(
    private val formats: Map<Pair<String, Int>, String>,
) {
    fun isOrdered(numId: String?, level: Int): Boolean {
        if (numId == null) return false
        val format = formats[numId to level] ?: formats[numId to 0] ?: return true
        return format !in setOf("bullet", "none")
    }

    companion object {
        val EMPTY = DocxNumbering(emptyMap())
    }
}

/** Relationship id → target path, as found in `word/_rels/document.xml.rels`. */
internal class DocxRelations(private val targets: Map<String, String>) {

    fun target(relId: String?): String? = relId?.let { targets[it] }

    fun imageUrl(relId: String?, bookKey: String): String? {
        val target = target(relId) ?: return null
        if (ZipPaths.isExternal(target)) return null
        val path = ZipPaths.resolve("word", target)
        if (!path.endsWith(".png", true) && !path.endsWith(".jpg", true) &&
            !path.endsWith(".jpeg", true) && !path.endsWith(".gif", true) &&
            !path.endsWith(".webp", true) && !path.endsWith(".bmp", true) &&
            !path.endsWith(".svg", true) && !path.endsWith(".emf", true) &&
            !path.endsWith(".wmf", true) && !path.endsWith(".tiff", true)
        ) {
            return null
        }
        return com.lightreader.core.format.ReaderAssets.mediaFile(bookKey, path)
    }

    companion object {
        val EMPTY = DocxRelations(emptyMap())
    }
}

/** Paragraph formatting options carried down while walking `w:p`. */
internal data class DocxParagraphStyle(
    val headingLevel: Int? = null,
    val alignment: TextAlignment? = null,
    val listItem: Block.ListItem? = null,
    val quote: Boolean = false,
)

internal fun List<Span>.trimEmpty(): List<Span> =
    map { it }.filter { it.text.isNotEmpty() }
