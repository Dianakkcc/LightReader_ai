package com.lightreader.core.format.image

import android.content.Context
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.CoverProvider
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.ImagePagedDocument

/**
 * Single (or multi) image document: comic pages, scans and photo books. Images are decoded with
 * subsampling by the image loader, so a 40 MP scan never lands in memory at full size.
 */
class ImageDocument(
    private val context: Context,
    private val source: DocumentSource,
    override val imageUris: List<String>,
) : ImagePagedDocument, CoverProvider {

    override val format: BookFormat = BookFormat.IMAGE
    override val pageCount: Int = imageUris.size.coerceAtLeast(1)
    override val metadata: DocumentMetadata =
        DocumentMetadata(title = source.displayName.substringBeforeLast('.').ifBlank { source.displayName })

    override val chapters: List<ChapterRef>
        get() = List(pageCount) { index -> ChapterRef(index, pageTitle(index), 0, 1L) }

    override fun pageTitle(pageIndex: Int): String =
        if (pageCount <= 1) metadata.title.orEmpty() else "第 ${pageIndex + 1} 页"

    override suspend fun loadCover(): ByteArray? = runCatching {
        // Huge scans would waste memory just to build a thumbnail; the shelf falls back to the
        // generated placeholder cover instead.
        if (source.sizeBytes > MAX_COVER_SOURCE_BYTES) return null
        source.openInput(context).use { it.readBytes() }
    }.getOrNull()

    override fun close() = Unit

    private companion object {
        const val MAX_COVER_SOURCE_BYTES = 32L * 1024 * 1024
    }
}
