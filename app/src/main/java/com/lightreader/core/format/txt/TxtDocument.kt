package com.lightreader.core.format.txt

import android.content.Context
import android.os.ParcelFileDescriptor
import com.lightreader.core.util.closeIfOpeningFails
import com.lightreader.core.format.BlockHtml
import com.lightreader.core.format.ChapterSearch
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.ParseErrorReason
import com.lightreader.core.format.ParseException
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.ReaderResource
import com.lightreader.core.model.ReflowableDocument
import com.lightreader.core.model.SearchHit
import com.lightreader.core.util.EncodingDetector
import com.lightreader.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext

/**
 * Streaming TXT reader.
 *
 * The file is never loaded as a whole: a chapter is a byte range that is read, decoded and
 * rendered on demand, and the table of contents is refined by a background scan that only looks
 * at line boundaries (0x0A, a single byte in every charset we support). A 500 MB novel therefore
 * opens as fast as a 5 KB note.
 */
class TxtDocument private constructor(
    private val context: Context,
    private val source: DocumentSource,
    val charset: Charset,
    private val bomLength: Long,
    private val dataSize: Long,
    initialSections: List<Section>,
) : ReflowableDocument {

    data class Section(
        val start: Long,
        val end: Long,
        val title: String,
        val level: Int,
    ) {
        val byteLength: Long get() = (end - start).coerceAtLeast(1L)
    }

    override val format: BookFormat = BookFormat.TXT
    override val metadata: DocumentMetadata = DocumentMetadata(title = defaultTitle(source))

    private var sections: List<Section> = initialSections

    @Volatile
    private var indexed = false

    override val chapters: List<ChapterRef>
        get() = sections.mapIndexed { index, section ->
            ChapterRef(index, section.title, section.level, section.byteLength)
        }

    /** Byte size is the progress weight: exact, cheap and stable across re-layouts. */
    override val totalChars: Long get() = dataSize

    val isIndexed: Boolean get() = indexed

    override suspend fun loadChapter(index: Int): ChapterContent = withContext(Dispatchers.IO) {
        val section = sections.getOrNull(index) ?: sections.first()
        val length = (section.end - section.start).coerceAtLeast(0L)
        val bytes = if (length == 0L) ByteArray(0) else readRange(section.start, length)
        val blocks = TxtBlocks.parse(decode(bytes))
        ChapterContent(
            index = index,
            title = section.title,
            html = BlockHtml.render(blocks),
            text = BlockHtml.plainText(blocks),
            baseUrl = ReaderAssets.BASE_URL,
        )
    }

    override fun resolveResource(path: String): ReaderResource? = null

    override suspend fun search(query: String, limit: Int): List<SearchHit> = ChapterSearch.find(
        query = query,
        chapterCount = sections.size,
        limit = limit,
        titleOf = { sections.getOrNull(it)?.title.orEmpty() },
        textLoader = { loadChapter(it).text },
    )

    override fun close() = Unit

    /**
     * Scans the file once and replaces the provisional chunks with the real chapters found by
     * [ChapterPatterns]. Runs off the main thread and is cancellable at every block.
     */
    suspend fun buildIndex(): List<ChapterRef> = withContext(Dispatchers.IO) {
        if (indexed) return@withContext chapters
        val marks = scanHeadings()
        val refined = sectionsFromMarks(marks)
        if (refined != null) {
            sections = refined
            Logs.d("TXT 目录分析完成：${refined.size} 章")
        }
        indexed = true
        chapters
    }

    private data class Mark(val offset: Long, val title: String, val level: Int)

    private suspend fun scanHeadings(): List<Mark> {
        val marks = ArrayList<Mark>(256)
        var carry = ByteArray(0)
        var carryStart = bomLength
        openAt(bomLength).use { input ->
            val chunk = ByteArray(SCAN_CHUNK_BYTES)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(chunk)
                if (read <= 0) break
                val merged = ByteArray(carry.size + read)
                System.arraycopy(carry, 0, merged, 0, carry.size)
                System.arraycopy(chunk, 0, merged, carry.size, read)

                var lineStart = 0
                var index = 0
                while (index < merged.size) {
                    if (merged[index] == NEW_LINE) {
                        addMarkIfHeading(merged, lineStart, index, carryStart, marks)
                        lineStart = index + 1
                    }
                    index++
                }
                carry = merged.copyOfRange(lineStart, merged.size)
                carryStart += lineStart
            }
        }
        if (carry.isNotEmpty()) addMarkIfHeading(carry, 0, carry.size, carryStart, marks)
        return marks
    }

    private fun addMarkIfHeading(
        buffer: ByteArray,
        from: Int,
        toExclusive: Int,
        bufferStart: Long,
        marks: MutableList<Mark>,
    ) {
        val length = toExclusive - from
        if (length <= 0 || length > MAX_HEADING_BYTES) return
        val line = decode(buffer.copyOfRange(from, toExclusive)).trim('\r', ' ', '\t', '\uFEFF')
        if (line.isEmpty()) return
        val level = ChapterPatterns.headingLevel(line) ?: return
        marks.add(Mark(bufferStart + from, line.take(60), level))
    }

    /** @return null when no usable heading was found, so the provisional chunks are kept. */
    private fun sectionsFromMarks(marks: List<Mark>): List<Section>? {
        if (marks.size < 2) return null
        val documentEnd = bomLength + dataSize
        val boundaries = ArrayList<Mark>(marks.size + 1)
        if (marks.first().offset > bomLength + 512) {
            boundaries.add(Mark(bomLength, defaultTitle(source), 0))
        }
        boundaries.addAll(marks)

        val sections = ArrayList<Section>(boundaries.size)
        boundaries.forEachIndexed { index, mark ->
            val end = boundaries.getOrNull(index + 1)?.offset ?: documentEnd
            if (end <= mark.offset) return@forEachIndexed
            if (end - mark.offset <= MAX_SECTION_BYTES) {
                sections.add(Section(mark.offset, end, mark.title, mark.level))
            } else {
                var start = mark.offset
                var part = 1
                while (start < end) {
                    val tentative = (start + CHUNK_BYTES).coerceAtMost(end)
                    val stop = if (tentative >= end) end else alignBoundary(tentative)
                    val title = if (part == 1) mark.title else "${mark.title}（续 $part）"
                    sections.add(Section(start, stop, title, mark.level))
                    start = stop
                    part++
                }
            }
        }
        return sections.ifEmpty { null }
    }

    /** Moves a byte offset onto the next character boundary of the detected charset. */
    private fun alignBoundary(offset: Long): Long {
        val relative = offset - bomLength
        if (charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE) {
            return if (relative % 2L == 0L) offset else offset + 1
        }
        return runCatching {
            val window = readRangeSync(offset, 4)
            if (window.isEmpty()) return@runCatching offset
            val previous = readRangeSync(offset - 1, 1).firstOrNull()?.toInt()?.and(0xFF) ?: 0
            if (charset == Charsets.UTF_8) {
                var shift = 0
                while (shift < window.size && window[shift].toInt() and 0xC0 == 0x80) shift++
                offset + shift
            } else {
                val candidate = window[0].toInt() and 0xFF
                if (previous >= 0x81 && candidate in 0x40..0xFE) offset + 1 else offset
            }
        }.getOrDefault(offset)
    }

    private suspend fun readRange(start: Long, length: Long): ByteArray =
        withContext(Dispatchers.IO) { readRangeSync(start, length) }

    private fun readRangeSync(start: Long, length: Long): ByteArray {
        if (length <= 0) return ByteArray(0)
        val size = length.coerceAtMost(MAX_SECTION_BYTES.toLong()).toInt()
        val bytes = ByteArray(size)
        openAt(start).use { input ->
            var read = 0
            while (read < size) {
                val count = input.read(bytes, read, size - read)
                if (count <= 0) break
                read += count
            }
            return if (read == size) bytes else bytes.copyOf(read)
        }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun openAt(start: Long): FileInputStream {
        val descriptor = context.contentResolver.openFileDescriptor(source.androidUri, "r")
            ?: throw ParseException(ParseErrorReason.PERMISSION, "无法读取文件：${source.uri}")
        val stream = descriptor.closeIfOpeningFails { ParcelFileDescriptor.AutoCloseInputStream(it) }
        return stream.closeIfOpeningFails {
            if (start > 0) {
                runCatching { it.channel.position(start) }
                    .onFailure { _ -> skipFully(it, start) }
            }
            it
        }
    }

    private fun skipFully(stream: FileInputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    companion object {
        private const val CHUNK_BYTES = 256 * 1024
        private const val MAX_SECTION_BYTES = 1024 * 1024
        private const val SCAN_CHUNK_BYTES = 64 * 1024
        private const val SAMPLE_BYTES = 64 * 1024
        private const val MAX_HEADING_BYTES = 160
        private const val NEW_LINE: Byte = 0x0A

        fun defaultTitle(source: DocumentSource): String =
            source.displayName.substringBeforeLast('.').ifBlank { source.displayName }

        suspend fun open(context: Context, source: DocumentSource): TxtDocument =
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                val detection = source.openInput(appContext).use { input ->
                    val sample = ByteArray(SAMPLE_BYTES)
                    var read = 0
                    while (read < SAMPLE_BYTES) {
                        val count = input.read(sample, read, SAMPLE_BYTES - read)
                        if (count <= 0) break
                        read += count
                    }
                    EncodingDetector.detect(if (read == SAMPLE_BYTES) sample else sample.copyOf(read))
                }
                val bom = detection.bomLength.toLong()
                val size = if (source.sizeBytes > 0) source.sizeBytes else measure(context, source)
                val dataSize = (size - bom).coerceAtLeast(0L)
                Logs.d(
                    "TXT 打开：${source.displayName} 大小=${dataSize}B 编码=${detection.charset.name()}",
                )
                TxtDocument(
                    context = appContext,
                    source = source,
                    charset = detection.charset,
                    bomLength = bom,
                    dataSize = dataSize,
                    initialSections = provisionalSections(dataSize, bom),
                )
            }

        private fun measure(context: Context, source: DocumentSource): Long =
            runCatching {
                context.contentResolver.openAssetFileDescriptor(source.androidUri, "r")
                    ?.use { it.length }
            }.getOrNull()?.takeIf { it > 0 } ?: 0L

        private fun provisionalSections(dataSize: Long, bom: Long): List<Section> {
            if (dataSize <= 0) return listOf(Section(bom, bom, "正文", 0))
            val sections = ArrayList<Section>()
            var start = bom
            var index = 1
            val end = bom + dataSize
            while (start < end) {
                val stop = (start + CHUNK_BYTES).coerceAtMost(end)
                sections.add(Section(start, stop, "第 $index 节", 0))
                start = stop
                index++
            }
            return sections
        }
    }
}
