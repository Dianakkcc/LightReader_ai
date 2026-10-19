package com.lightreader.core.format.pdf

import android.content.Context
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.SearchHit
import com.lightreader.core.model.TocEntry
import com.lightreader.core.util.Logs
import com.lightreader.core.util.TextUtil
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Text side of PDF support: outlines, metadata and full text search.
 *
 * PDFBox is only used for text; pages are always rasterised by the platform renderer, which is
 * much faster. Documents are opened with a temp-file memory setting so that even large files do
 * not end up on the heap, and every operation closes its handle again.
 */
class PdfTextEngine(
    private val context: Context,
    private val source: DocumentSource,
    private val tempDir: File,
) {

    fun initialise() {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private suspend fun <T> withDocument(block: suspend (PDDocument) -> T): T? =
        withContext(Dispatchers.IO) {
        initialise()
        var document: PDDocument? = null
        try {
            val setting = runCatching {
                MemoryUsageSetting.setupTempFileOnly(TEMP_FILE_LIMIT_BYTES)
            }.getOrNull()
            document = if (setting != null) {
                source.openInput(context).use { PDDocument.load(it, setting) }
            } else {
                source.openInput(context).use { PDDocument.load(it) }
            }
            block(document)
        } catch (throwable: Throwable) {
            Logs.w("PDF 文本处理失败：${throwable.message}", throwable)
            null
        } finally {
            runCatching { document?.close() }
        }
    }

    suspend fun metadata(): DocumentMetadata? = withDocument { document ->
        val info = runCatching { document.documentInformation }.getOrNull()
        DocumentMetadata(
            title = info?.title?.trim()?.takeIf { it.isNotEmpty() },
            author = info?.author?.trim()?.takeIf { it.isNotEmpty() },
            identifier = runCatching { document.documentId?.toString() }.getOrNull(),
        )
    }

    suspend fun outline(): List<TocEntry> = withDocument { document ->
        val root = runCatching { document.documentCatalog?.documentOutline }.getOrNull()
            ?: return@withDocument emptyList()
        val entries = ArrayList<TocEntry>()
        var child = runCatching { root.firstChild }.getOrNull()
        while (child != null) {
            collectOutline(document, child, 0, entries)
            child = runCatching { child.nextSibling }.getOrNull()
        }
        entries
    } ?: emptyList()

    private fun collectOutline(
        document: PDDocument,
        item: PDOutlineItem,
        level: Int,
        out: MutableList<TocEntry>,
    ) {
        if (out.size > 2000) return
        val title = runCatching { item.title }.getOrNull()?.trim().orEmpty()
        val pageIndex = runCatching {
            val destination = item.destination
            if (destination is PDPageDestination) {
                val page = runCatching { destination.page }.getOrNull()
                    ?: return@runCatching -1
                document.pages.indexOf(page)
            } else {
                -1
            }
        }.getOrDefault(-1)
        if (title.isNotEmpty() && pageIndex >= 0) {
            out.add(TocEntry(title, pageIndex, null, level))
        }
        var child = runCatching { item.firstChild }.getOrNull()
        while (child != null) {
            collectOutline(document, child, level + 1, out)
            child = runCatching { child.nextSibling }.getOrNull()
        }
    }

    suspend fun pageText(pageIndex: Int): String? = withDocument { document ->
        val total = document.numberOfPages
        if (pageIndex < 0 || pageIndex >= total) return@withDocument null
        val stripper = PDFTextStripper().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
            sortByPosition = true
        }
        runCatching { stripper.getText(document) }.getOrNull()
    }

    suspend fun search(query: String, limit: Int): List<SearchHit> {
        val needle = TextUtil.normaliseForSearch(query.trim())
        if (needle.isEmpty()) return emptyList()
        return withDocument { document ->
            val hits = ArrayList<SearchHit>()
            val total = document.numberOfPages
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            for (pageIndex in 0 until total) {
                coroutineContext.ensureActive()
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                val text = runCatching { stripper.getText(document) }.getOrNull() ?: continue
                if (text.isBlank()) continue
                val haystack = TextUtil.normaliseForSearch(text)
                var from = 0
                while (true) {
                    val found = haystack.indexOf(needle, from)
                    if (found < 0) break
                    hits.add(
                        SearchHit(
                            chapterIndex = pageIndex,
                            charOffset = found,
                            pageIndex = pageIndex,
                            snippet = TextUtil.snippet(text, found),
                            title = "第 ${pageIndex + 1} 页",
                        ),
                    )
                    if (hits.size >= limit) return@withDocument hits
                    from = found + needle.length
                }
            }
            hits
        } ?: emptyList()
    }

    private companion object {
        /** PDFBox may spill to temp files; keep that bounded and inside the app cache. */
        const val TEMP_FILE_LIMIT_BYTES = 96L * 1024 * 1024
    }
}
