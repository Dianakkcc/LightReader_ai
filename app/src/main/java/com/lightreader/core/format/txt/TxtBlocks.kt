package com.lightreader.core.format.txt

import com.lightreader.core.format.Block
import com.lightreader.core.format.Span

/**
 * Turns plain text into paragraphs.
 *
 * Two very common layouts are handled: hard wrapped text (long lines that belong to the same
 * paragraph) and one paragraph per line (short lines, typical for Chinese novels).
 */
object TxtBlocks {

    private const val JOIN_THRESHOLD = 60

    private val indentChars = charArrayOf('\u3000', '\t')

    fun parse(text: String): List<Block> {
        val lines = text.split('\n')
        val blocks = ArrayList<Block>()
        val paragraph = StringBuilder()
        var paragraphIndented = false

        fun flush() {
            val content = paragraph.toString().trim()
            paragraph.setLength(0)
            if (content.isNotEmpty()) {
                blocks.add(Block.Paragraph(listOf(Span(content))))
            }
            paragraphIndented = false
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) {
                flush()
                continue
            }
            val trimmed = line.trimStart()
            val indented = line.length != trimmed.length &&
                (line.first() in indentChars || line.startsWith("  "))
            val isShort = trimmed.length < JOIN_THRESHOLD
            val startsNew = paragraph.isEmpty() || indented || paragraphIndented

            if (startsNew && paragraph.isNotEmpty()) flush()
            if (!startsNew) paragraph.append(' ')
            paragraph.append(trimmed)
            paragraphIndented = indented

            // Short lines are complete paragraphs on their own (novel style).
            if (isShort && !trimmed.endsWith(",") && !trimmed.endsWith("，")) {
                flush()
            }
        }
        flush()
        return blocks
    }
}
