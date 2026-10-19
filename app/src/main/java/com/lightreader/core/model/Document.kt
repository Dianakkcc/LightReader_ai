package com.lightreader.core.model

import android.graphics.Bitmap

/**
 * A single entry of the rendered table of contents. For reflowable documents it points at a
 * chapter index, for paged documents at a page index, so navigation code stays format agnostic.
 */
data class ChapterRef(
    val index: Int,
    val title: String,
    val level: Int = 0,
    val weight: Long = 1L,
)

/** Fully rendered content of one reflowable chapter. */
data class ChapterContent(
    val index: Int,
    val title: String,
    /** Body HTML, already sanitised and with resources rewritten to the reader asset host. */
    val html: String,
    /** Plain text of the same chapter, used for search and progress maths. */
    val text: String,
    val baseUrl: String,
    /** Stylesheet shipped with the chapter (EPUB), inserted before the user's own settings. */
    val css: String? = null,
)

data class SearchHit(
    val chapterIndex: Int,
    val charOffset: Int,
    val pageIndex: Int = 0,
    val snippet: String,
    val title: String,
)

/**
 * One table of contents entry. [chapterIndex] targets a chapter for reflowable documents and a
 * page for paged ones; [anchor] is an optional element id inside that chapter (EPUB / HTML).
 */
data class TocEntry(
    val title: String,
    val chapterIndex: Int,
    val anchor: String? = null,
    val level: Int = 0,
)

/** Base type of everything the format layer can hand to the reader. */
sealed interface OpenedDocument {
    val format: BookFormat
    val metadata: DocumentMetadata
    val kind: DocumentKind
    val chapters: List<ChapterRef>

    val toc: List<TocEntry>
        get() = chapters.map { TocEntry(it.title, it.index, null, it.level) }

    fun close()
}

/** Documents that can produce a cover image for the library shelf. */
interface CoverProvider {
    suspend fun loadCover(): ByteArray?
}

/** A document the user can restyle: EPUB, TXT, DOCX, RTF, HTML, Markdown. */
interface ReflowableDocument : OpenedDocument {
    override val kind: DocumentKind get() = DocumentKind.REFLOWABLE

    /** Character count of the whole document; used for percentage maths. */
    val totalChars: Long

    /**
     * Optional second pass that makes the per-chapter progress weights reflect how much *text* a
     * chapter holds instead of how large its file happens to be. Runs in the background after the
     * book is already readable; returns true when the weights changed.
     */
    suspend fun refineWeights(): Boolean = false

    suspend fun loadChapter(index: Int): ChapterContent

    /** Resolves resources (images, CSS, fonts) referenced by the generated chapter HTML. */
    suspend fun resolveResource(path: String): ReaderResource?

    /**
     * Streams the document and reports every occurrence of [query]. Implementations must stay
     * cancellable and must not load the whole document into memory.
     */
    suspend fun search(query: String, limit: Int = 200): List<SearchHit>
}

/** A document whose original layout is preserved: PDF and image collections. */
interface PagedDocument : OpenedDocument {
    override val kind: DocumentKind get() = DocumentKind.FIXED
    val pageCount: Int
    fun pageTitle(pageIndex: Int): String
}

/** Documents the reader renders onto a bitmap surface (PDF). */
interface BitmapPagedDocument : PagedDocument {
    /** Renders [pageIndex] at (at least) the requested width; callers own the bitmap. */
    suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap

    /** Text layer of a page, or null when the page has no extractable text. */
    suspend fun pageText(pageIndex: Int): String?
}

/** Documents made of one image per page (single image files, and later CBZ/CBR). */
interface ImagePagedDocument : PagedDocument {
    val imageUris: List<String>
}

data class ReaderResource(
    val bytes: ByteArray,
    val mimeType: String,
)
