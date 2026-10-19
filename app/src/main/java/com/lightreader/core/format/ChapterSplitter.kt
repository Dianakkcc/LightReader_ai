package com.lightreader.core.format

import com.lightreader.core.model.ChapterRef

/**
 * Turns a flat block list into readable chapters: a new chapter starts at a top level heading,
 * or after [maxChars] when the document has no headings at all (a 5 MB TXT without chapters is
 * still perfectly readable, and the reader must not choke on it).
 */
object ChapterSplitter {

    const val DEFAULT_MAX_CHARS = 24_000

    data class Section(val title: String, val level: Int, val blocks: List<Block>) {
        val charCount: Int get() = BlockHtml.plainText(blocks).length
    }

    fun split(
        blocks: List<Block>,
        fallbackTitle: String,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): List<Section> {
        val sections = ArrayList<Section>()
        var current = ArrayList<Block>()
        var currentTitle = fallbackTitle
        var baseTitle = fallbackTitle
        var currentLevel = 0
        var currentChars = 0
        var partIndex = 1

        fun flush() {
            if (current.isEmpty()) return
            sections.add(Section(currentTitle, currentLevel, current))
            current = ArrayList()
            currentChars = 0
        }

        for (block in blocks) {
            val heading = block as? Block.Heading
            val headingText = heading?.let { BlockHtml.plainText(listOf(it)).trim() }.orEmpty()
            val isTopLevelHeading = heading != null && heading.level <= 2 && headingText.isNotEmpty()

            if (isTopLevelHeading) {
                val hasBodyText = current.any { it !is Block.Heading }
                if (hasBodyText) {
                    flush()
                } else if (current.isNotEmpty()) {
                    // Only headings so far: the new one replaces them as the chapter title.
                    current.clear()
                    currentChars = 0
                }
                currentTitle = headingText
                baseTitle = headingText
                currentLevel = heading.level
                partIndex = 1
            } else if (currentChars >= maxChars) {
                flush()
                partIndex++
                currentTitle = "$baseTitle（续 $partIndex）"
            }

            current.add(block)
            currentChars += lengthOf(block)
        }
        flush()

        if (sections.isEmpty()) {
            sections.add(Section(fallbackTitle, 0, emptyList()))
        }
        return sections
    }

    fun toChapterRefs(sections: List<Section>): List<ChapterRef> =
        sections.mapIndexed { index, section ->
            ChapterRef(
                index = index,
                title = section.title,
                level = section.level,
                weight = section.charCount.toLong().coerceAtLeast(1L),
            )
        }

    private fun lengthOf(block: Block): Int = when (block) {
        is Block.Paragraph -> block.spans.sumOf { it.text.length }
        is Block.Heading -> block.spans.sumOf { it.text.length }
        is Block.ListItem -> block.spans.sumOf { it.text.length }
        is Block.Quote -> block.spans.sumOf { it.text.length }
        is Block.Code -> block.text.length
        is Block.ImageBlock -> 1
        is Block.Table -> block.rows.sumOf { row -> row.cells.sumOf { cell -> cell.blocks.sumOf { lengthOf(it) } } }
        Block.Divider -> 1
    }
}

/** Percentage maths shared by all reflowable documents. */
object ProgressMath {

    /**
     * @param chapterFraction position inside the current chapter, 0..1
     */
    fun percent(
        chapters: List<ChapterRef>,
        chapterIndex: Int,
        chapterFraction: Float,
    ): Float {
        if (chapters.isEmpty()) return 0f
        val total = chapters.sumOf { it.weight }.coerceAtLeast(1L)
        val before = chapters.take(chapterIndex.coerceIn(0, chapters.size - 1)).sumOf { it.weight }
        val current = chapters.getOrNull(chapterIndex)?.weight ?: 0L
        val fraction = chapterFraction.coerceIn(0f, 1f)
        val position = before + (current * fraction).toLong()
        return (position.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun chapterFraction(charOffset: Int, chapterChars: Int): Float =
        if (chapterChars <= 0) 0f else (charOffset.toFloat() / chapterChars).coerceIn(0f, 1f)

    /** Inverse of [percent]: which chapter (and how far into it) a percentage points at. */
    fun chapterAt(chapters: List<ChapterRef>, percent: Float): Pair<Int, Int> {
        if (chapters.isEmpty()) return 0 to 0
        val total = chapters.sumOf { it.weight }.coerceAtLeast(1L)
        val target = (percent.coerceIn(0f, 1f) * total).toLong()
        var accumulated = 0L
        chapters.forEachIndexed { index, chapter ->
            if (target < accumulated + chapter.weight || index == chapters.lastIndex) {
                val inside = (target - accumulated).coerceAtLeast(0L)
                val ratio = if (chapter.weight <= 0) 0f else inside.toFloat() / chapter.weight
                return index to (ratio * 1000).toInt()
            }
            accumulated += chapter.weight
        }
        return chapters.lastIndex to 1000
    }
}
