package com.lightreader.core.format.html

import com.lightreader.core.format.ChapterSearch
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.ReaderResource
import com.lightreader.core.model.ReflowableDocument
import com.lightreader.core.model.SearchHit
import com.lightreader.core.model.TocEntry
import org.jsoup.Jsoup

/**
 * A reflowable document whose chapters are already generated HTML (HTML, Markdown, DOCX, RTF).
 * Text for search is derived once per chapter and cached, so repeated searches are cheap.
 */
class HtmlBasedDocument(
    override val format: BookFormat,
    override val metadata: DocumentMetadata,
    private val sections: List<Section>,
    private val resourceResolver: (suspend (String) -> ReaderResource?)? = null,
    private val closer: (() -> Unit)? = null,
) : ReflowableDocument {

    data class Section(
        val title: String,
        val level: Int,
        val html: () -> String,
    )

    private val textCache = HashMap<Int, String>()

    override val chapters: List<ChapterRef>
        get() = sections.mapIndexed { index, section ->
            ChapterRef(
                index = index,
                title = section.title,
                level = section.level,
                weight = section.html().length.toLong().coerceAtLeast(1L),
            )
        }

    override val totalChars: Long
        get() = sections.sumOf { it.html().length.toLong() }.coerceAtLeast(1L)

    override suspend fun loadChapter(index: Int): ChapterContent {
        val section = sections.getOrNull(index) ?: sections.firstOrNull()
            ?: return ChapterContent(index, "", "", "", ReaderAssets.BASE_URL)
        return ChapterContent(
            index = index,
            title = section.title,
            html = section.html(),
            text = textOf(index),
            baseUrl = ReaderAssets.BASE_URL,
        )
    }

    override suspend fun resolveResource(path: String): ReaderResource? =
        resourceResolver?.invoke(path)

    override suspend fun search(query: String, limit: Int): List<SearchHit> = ChapterSearch.find(
        query = query,
        chapterCount = sections.size,
        limit = limit,
        titleOf = { sections.getOrNull(it)?.title.orEmpty() },
        textLoader = { textOf(it) },
    )

    override fun close() {
        textCache.clear()
        closer?.invoke()
    }

    private fun textOf(index: Int): String = synchronized(textCache) {
        textCache.getOrPut(index) {
            sections.getOrNull(index)?.let { Jsoup.parse(it.html()).text() }.orEmpty()
        }
    }
}

/** Splits a single HTML body into chapters at `<h1>`/`<h2>` boundaries. */
object HtmlChapterizer {

    fun split(bodyHtml: String, fallbackTitle: String): List<HtmlBasedDocument.Section> {
        val body = Jsoup.parse(bodyHtml).body()
        val sections = ArrayList<HtmlBasedDocument.Section>()
        var currentTitle = fallbackTitle
        var currentLevel = 0
        val current = StringBuilder()

        fun flush() {
            if (current.isBlank()) return
            val html = current.toString()
            sections.add(
                HtmlBasedDocument.Section(currentTitle, currentLevel) { html },
            )
            current.setLength(0)
        }

        body.children().forEach { element ->
            val tag = element.tagName().lowercase()
            val isHeading = tag == "h1" || tag == "h2"
            if (isHeading && current.isNotBlank()) {
                flush()
                currentTitle = element.text().trim().ifBlank { fallbackTitle }
                currentLevel = if (tag == "h1") 1 else 2
            } else if (isHeading && current.isBlank() && sections.isEmpty()) {
                currentTitle = element.text().trim().ifBlank { fallbackTitle }
                currentLevel = if (tag == "h1") 1 else 2
            }
            current.append(element.outerHtml())
        }
        flush()

        if (sections.isEmpty()) {
            sections.add(HtmlBasedDocument.Section(fallbackTitle, 0) { bodyHtml })
        }
        return sections
    }
}
