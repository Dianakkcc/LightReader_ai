package com.lightreader.core.format.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.model.BitmapPagedDocument
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.CoverProvider
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.SearchHit
import com.lightreader.core.model.TocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * PDF is a fixed layout format: the original page geometry is preserved and the user zooms and
 * pans instead of re-flowing. Pages are rasterised on demand by the platform renderer.
 */
class PdfDocument(
    private val source: DocumentSource,
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor?,
    private val textEngine: PdfTextEngine,
    override val metadata: DocumentMetadata,
    override val toc: List<TocEntry>,
) : BitmapPagedDocument, CoverProvider {

    override val format: BookFormat = BookFormat.PDF
    override val pageCount: Int = renderer.pageCount
    override val chapters: List<ChapterRef>
        get() = List(pageCount) { index -> ChapterRef(index, pageTitle(index), 0, 1L) }

    private val lock = ReentrantLock()

    override fun pageTitle(pageIndex: Int): String = "第 ${pageIndex + 1} 页"

    /** Width/height ratio of a page in its own coordinate space. */
    fun pageAspect(pageIndex: Int): Float = lock.withLock {
        if (pageIndex !in 0 until pageCount) return 1f
        renderer.openPage(pageIndex).use { page ->
            if (page.height == 0) 1f else page.width.toFloat() / page.height.toFloat()
        }
    }

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap =
        withContext(Dispatchers.Default) {
            lock.withLock {
                val width = targetWidth.coerceIn(16, MAX_BITMAP_EDGE)
                val height = targetHeight.coerceIn(16, MAX_BITMAP_EDGE)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                if (pageIndex !in 0 until pageCount) {
                    bitmap.eraseColor(Color.WHITE)
                    return@withLock bitmap
                }
                renderer.openPage(pageIndex).use { page ->
                    bitmap.eraseColor(Color.WHITE)
                    runCatching {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
                bitmap
            }
        }

    override suspend fun pageText(pageIndex: Int): String? = textEngine.pageText(pageIndex)

    suspend fun search(query: String, limit: Int = 200): List<SearchHit> =
        textEngine.search(query, limit)

    suspend fun loadOutline(): List<TocEntry> = textEngine.outline()

    suspend fun loadMetadata(): DocumentMetadata? = textEngine.metadata()

    /** First page rendered small, used as the shelf cover. */
    override suspend fun loadCover(): ByteArray? = withContext(Dispatchers.IO) {
        val width = 480
        val aspect = pageAspect(0)
        val height = (width / aspect.coerceAtLeast(0.1f)).toInt().coerceIn(64, 4096)
        val bitmap = renderPage(0, width, height)
        ByteArrayOutputStream().use { output ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
            bitmap.recycle()
            if (ok) output.toByteArray() else null
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor?.close() }
    }

    private companion object {
        /** Guards against absurd zoom levels allocating impossible bitmaps. */
        const val MAX_BITMAP_EDGE = 6000
    }
}
