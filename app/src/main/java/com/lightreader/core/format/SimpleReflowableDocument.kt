package com.lightreader.core.format

import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.ReaderResource
import com.lightreader.core.model.ReflowableDocument
import com.lightreader.core.model.SearchHit

/**
 * Reflowable document backed by lambdas. All text based formats (TXT, DOCX, RTF, HTML,
 * Markdown, EPUB) share this implementation so the reader only has to know one contract.
 */
class SimpleReflowableDocument(
    override val format: BookFormat,
    override val metadata: DocumentMetadata,
    chapters: List<ChapterRef>,
    override val totalChars: Long,
    private val chapterLoader: suspend (Int) -> ChapterContent,
    private val resourceResolver: ((String) -> ReaderResource?)? = null,
    private val closer: (() -> Unit)? = null,
) : ReflowableDocument {

    private var chapterList: List<ChapterRef> = chapters

    override val chapters: List<ChapterRef> get() = chapterList

    /** Used by documents that refine their table of contents in the background (TXT). */
    fun updateChapters(newChapters: List<ChapterRef>) {
        chapterList = newChapters
    }

    override suspend fun loadChapter(index: Int): ChapterContent = chapterLoader(index)

    override fun resolveResource(path: String): ReaderResource? =
        resourceResolver?.invoke(path)

    override suspend fun search(query: String, limit: Int): List<SearchHit> =
        ChapterSearch.find(
            query = query,
            chapterCount = chapterList.size,
            limit = limit,
            titleOf = { chapterList.getOrNull(it)?.title ?: "" },
            textLoader = { chapterLoader(it).text },
        )

    override fun close() {
        closer?.invoke()
    }
}
