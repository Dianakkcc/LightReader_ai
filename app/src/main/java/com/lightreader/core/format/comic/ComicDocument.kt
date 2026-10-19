package com.lightreader.core.format.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lightreader.core.data.BookWorkspace
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.ParseErrorReason
import com.lightreader.core.format.ParseException
import com.lightreader.core.model.BitmapPagedDocument
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.CoverProvider
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.TocEntry
import com.lightreader.core.util.Logs
import com.lightreader.core.util.imageSampleSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Comic archive reader (`.cbz`, and any `.zip` that holds page images).
 *
 * Structure rules, taken from how comic archives are actually packed in the wild:
 * * images in the archive root -> **one** chapter (the whole book);
 * * images inside folders -> **one chapter per folder**, folders ordered naturally
 *   ("第2话" before "第10话");
 * * junk entries (`__MACOSX`, dot files, `Thumbs.db`) are ignored;
 * * pages inside a chapter are ordered naturally by file name.
 *
 * Pages are decoded straight out of the archive with subsampling, so a 300 MB archive of 4000px
 * scans never has more than one page in memory.
 */
class ComicDocument private constructor(
    private val zip: ZipFile,
    private val pages: List<Page>,
    override val chapters: List<ChapterRef>,
    override val metadata: DocumentMetadata,
    private val cover: ByteArray?,
) : BitmapPagedDocument, CoverProvider {

    /** One page: its archive entry, plus where it sits inside its chapter. */
    data class Page(val entry: String, val chapterIndex: Int, val numberInChapter: Int)

    override val format: BookFormat = BookFormat.COMIC
    override val pageCount: Int get() = pages.size

    /** First page of every chapter, so the table of contents jumps to the right page. */
    private val chapterFirstPage: List<Int> = chapters.indices.map { chapterIndex ->
        pages.indexOfFirst { it.chapterIndex == chapterIndex }.coerceAtLeast(0)
    }

    override val toc: List<TocEntry>
        get() = chapters.map { chapter ->
            TocEntry(
                title = chapter.title,
                chapterIndex = chapterFirstPage.getOrElse(chapter.index) { 0 },
                anchor = null,
                level = chapter.level,
            )
        }

    private val zipLock = Any()

    /** Aspect ratio (width / height) per page, read once from the image headers and cached. */
    private val aspectCache = HashMap<Int, Float>()

    /** Width/height ratio of a page, used to reserve the right space before it is decoded. */
    fun pageAspect(pageIndex: Int): Float = synchronized(aspectCache) {
        aspectCache.getOrPut(pageIndex) {
            val entry = pages.getOrNull(pageIndex)?.entry ?: return@getOrPut 0.707f
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decodeEntry(entry, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                0.707f
            } else {
                bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
            }
        }
    }

    override fun pageTitle(pageIndex: Int): String {
        val page = pages.getOrNull(pageIndex) ?: return ""
        val chapter = chapters.getOrNull(page.chapterIndex)?.title.orEmpty()
        return if (chapters.size <= 1) {
            "第 ${pageIndex + 1} 页"
        } else {
            "$chapter · 第 ${page.numberInChapter + 1} 页"
        }
    }

    /** Archive entry of a page; used by the tests to pin down page ordering. */
    internal fun entryNameOf(pageIndex: Int): String? = pages.getOrNull(pageIndex)?.entry

    override suspend fun renderPage(
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        val page = pages.getOrNull(pageIndex)
            ?: throw ParseException(ParseErrorReason.CORRUPT, "漫画页不存在：$pageIndex")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeEntry(page.entry, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ParseException(ParseErrorReason.CORRUPT, "漫画页无法读取：${page.entry}")
        }
        synchronized(aspectCache) {
            aspectCache[pageIndex] = bounds.outWidth.toFloat() / bounds.outHeight
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = decodeEntry(page.entry, options)
            ?: throw ParseException(ParseErrorReason.CORRUPT, "漫画页无法解码：${page.entry}")
        scaleToWidth(decoded, targetWidth)
    }

    /** Comics have no text layer. */
    override suspend fun pageText(pageIndex: Int): String? = null

    override suspend fun loadCover(): ByteArray? = cover

    override fun close() {
        runCatching { synchronized(zipLock) { zip.close() } }
    }

    private fun decodeEntry(name: String, options: BitmapFactory.Options): Bitmap? = synchronized(zipLock) {
        val entry = zip.getEntry(name) ?: return null
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {

        /** A cover must stay small enough to decode on the shelf without ceremony. */
        private const val MAX_COVER_BYTES = 24L * 1024 * 1024

        private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")
        private val junkFolderNames = setOf("__macosx", "thumbs.db", ".ds_store")

        suspend fun open(context: Context, source: DocumentSource): ComicDocument = withContext(Dispatchers.IO) {
            val workspace = BookWorkspace(context)
            val key = workspace.keyFor(source.uri)
            val file: File = workspace.materialise(source, key, "cbz")
            val zip = runCatching { ZipFile(file) }.getOrElse {
                workspace.dropBookCache(key)
                throw ParseException(ParseErrorReason.CORRUPT, "漫画压缩包已损坏：${it.message}")
            }

            try {
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name.replace('\\', '/') }
                    .filter { !it.startsWith("__MACOSX/") && !it.startsWith(".") }
                    .filter { path -> path.substringAfterLast('.', "").lowercase() in imageExtensions }
                    .toList()

                if (entries.isEmpty()) {
                    throw ParseException(ParseErrorReason.CORRUPT, "压缩包里没有找到图片页")
                }

                // One chapter per folder; images sitting in the root form a chapter of their own.
                val byFolder = entries.groupBy { it.substringBeforeLast('/', "") }
                    .toSortedMap(naturalOrder())
                val multiChapter = byFolder.size > 1
                val chapters = ArrayList<ChapterRef>(byFolder.size)
                val pages = ArrayList<Page>(entries.size)
                byFolder.forEach { (folder, paths) ->
                    val chapterIndex = chapters.size
                    val title = when {
                        !multiChapter -> source.displayName.substringBeforeLast('.').ifBlank { "漫画" }
                        folder.isEmpty() -> "第 ${chapterIndex + 1} 章"
                        else -> folder.substringAfterLast('/')
                    }
                    var number = 0
                    paths.sortedWith(naturalOrder()).forEach { path ->
                        pages += Page(path, chapterIndex, number)
                        number++
                    }
                    chapters += ChapterRef(
                        index = chapterIndex,
                        title = title,
                        weight = number.toLong().coerceAtLeast(1L),
                    )
                }

                val title = source.displayName.substringBeforeLast('.').ifBlank { "漫画" }
                val metadata = DocumentMetadata(title = title)
                val cover = runCatching {
                    val first = pages.firstOrNull()?.entry ?: return@runCatching null
                    val entry = zip.getEntry(first) ?: return@runCatching null
                    if (entry.size > MAX_COVER_BYTES) return@runCatching null
                    zip.getInputStream(entry).use { stream ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        while (true) {
                            val read = stream.read(chunk)
                            if (read <= 0) break
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toByteArray()
                    }
                }.getOrNull()

                Logs.d("漫画打开：$title 章节=${chapters.size} 页数=${pages.size}")
                ComicDocument(zip, pages, chapters, metadata, cover)
            } catch (throwable: Throwable) {
                runCatching { zip.close() }
                throw throwable
            }
        }

        /**
         * Decodes an entry with the smallest power-of-two subsample that still covers the target,
         * then scales it to [targetWidth]. Handles both very tall pages and very wide spreads.
         */
        private fun scaleToWidth(decoded: Bitmap, targetWidth: Int): Bitmap {
            if (targetWidth <= 0 || decoded.width <= targetWidth) return decoded

            val scale = targetWidth.toFloat() / decoded.width.toFloat()
            val width = targetWidth
            val height = (decoded.height * scale).toInt().coerceAtLeast(1)
            val scaled = runCatching { Bitmap.createScaledBitmap(decoded, width, height, true) }
                .getOrDefault(decoded)
            if (scaled !== decoded) decoded.recycle()
            return scaled
        }

        /**
         * Natural order: "page2" sorts before "page10", which is the order comic archives use.
         * Plain lexicographic order would show page 1, 10, 11, 2, ... which is unusable.
         */
        private fun naturalOrder(): Comparator<String> = Comparator { left, right ->
            var i = 0
            var j = 0
            while (i < left.length && j < right.length) {
                val a = left[i]
                val b = right[j]
                if (a.isDigit() && b.isDigit()) {
                    var endA = i
                    while (endA < left.length && left[endA].isDigit()) endA++
                    var endB = j
                    while (endB < right.length && right[endB].isDigit()) endB++
                    val digitsA = left.substring(i, endA).trimStart('0')
                    val digitsB = right.substring(j, endB).trimStart('0')
                    val byLength = digitsA.length - digitsB.length
                    val compared = if (byLength != 0) byLength else digitsA.compareTo(digitsB)
                    if (compared != 0) return@Comparator compared
                    i = endA
                    j = endB
                } else {
                    val compared = a.lowercaseChar().compareTo(b.lowercaseChar())
                    if (compared != 0) return@Comparator compared
                    i++
                    j++
                }
            }
            (left.length - i) - (right.length - j)
        }
    }
}
