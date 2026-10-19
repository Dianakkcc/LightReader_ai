package com.lightreader.core.format.epub

import android.content.Context
import com.lightreader.core.data.BookWorkspace
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.HtmlSanitizer
import com.lightreader.core.format.ParseErrorReason
import com.lightreader.core.format.ParseException
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.CoverProvider
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.ReaderResource
import com.lightreader.core.model.ReflowableDocument
import com.lightreader.core.model.SearchHit
import com.lightreader.core.model.TocEntry
import com.lightreader.core.format.ChapterSearch
import com.lightreader.core.util.Logs
import com.lightreader.core.util.CssRewriter
import com.lightreader.core.util.TextDecoding
import com.lightreader.core.util.ZipPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * EPUB 2 / EPUB 3 reading engine.
 *
 * The container is opened as a zip and only the entries a chapter needs are read, which keeps
 * even a heavily illustrated book out of memory. Original stylesheets are preserved and
 * rewritten so that images, fonts and CSS keep working inside the reader's virtual asset host.
 */
class EpubDocument private constructor(
    private val workspace: BookWorkspace,
    private val bookKey: String,
    private val zip: ZipFile,
    private val opfDir: String,
    private val spine: List<Item>,
    private val manifestById: Map<String, Item>,
    private val manifestByPath: Map<String, Item>,
    override val metadata: DocumentMetadata,
    override val toc: List<TocEntry>,
    private val coverItem: Item?,
) : ReflowableDocument, CoverProvider {

    data class Item(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: String = "",
    ) {
        val isDocument: Boolean
            get() = mediaType.contains("xhtml") || mediaType.contains("html") ||
                mediaType.contains("xml") && path.endsWith(".xhtml", true)
    }

    override val format: BookFormat = BookFormat.EPUB

    private val zipLock = Any()

    private var entryNames: Map<String, String>? = null

    /** File size is the cheap first answer; [refineWeights] replaces it with the real text length. */
    private val baseWeights: List<Long> by lazy {
        spine.map { item ->
            entrySize(item.path)?.coerceAtLeast(512L) ?: 4096L
        }
    }

    @Volatile
    private var measuredWeights: List<Long>? = null

    private val chapterWeights: List<Long> get() = measuredWeights ?: baseWeights

    override val chapters: List<ChapterRef>
        get() = spine.mapIndexed { index, item ->
            ChapterRef(
                index = index,
                title = toc.firstOrNull { it.chapterIndex == index }?.title ?: "第 ${index + 1} 节",
                level = toc.firstOrNull { it.chapterIndex == index }?.level ?: 0,
                weight = chapterWeights.getOrElse(index) { 4096L },
            )
        }

    override val totalChars: Long get() = chapterWeights.sum().coerceAtLeast(1L)

    /**
     * Counts the characters of every chapter so the progress bar reflects the text instead of the
     * markup: in a real book one chapter can be a 2 KB picture page while the next holds 140 KB of
     * prose, which made "how far through the book am I" wildly wrong.
     */
    override suspend fun refineWeights(): Boolean = withContext(Dispatchers.IO) {
        if (measuredWeights != null) return@withContext false
        var budget = WEIGHT_SCAN_BUDGET
        val measured = ArrayList<Long>(spine.size)
        for (item in spine) {
            val size = entrySize(item.path) ?: return@withContext false
            if (size > budget) return@withContext false
            budget -= size
            val bytes = readEntry(item.path) ?: return@withContext false
            measured += textLength(TextDecoding.decode(bytes))
        }
        if (measured.size != spine.size || measured.isEmpty()) return@withContext false
        // A chapter of a few hundred characters (a title page, a plate, the table of contents)
        // must not drag the whole-book percentage down to zero: every chapter counts for at least
        // a quarter of an average chapter.
        val floor = (measured.sum().toDouble() / measured.size * CHAPTER_WEIGHT_FLOOR)
            .toLong()
            .coerceAtLeast(1L)
        measuredWeights = measured.map { it.coerceAtLeast(floor) }
        true
    }

    override suspend fun loadChapter(index: Int): ChapterContent = withContext(Dispatchers.IO) {
        val item = spine.getOrNull(index)
            ?: throw ParseException(ParseErrorReason.CORRUPT, "章节不存在：$index")
        val bytes = readEntry(item.path)
            ?: throw ParseException(ParseErrorReason.CORRUPT, "章节文件缺失：${item.path}")
        val document = Jsoup.parse(TextDecoding.decode(bytes))
        val baseDir = ZipPaths.parentDir(item.path)

        val css = StringBuilder()
        document.select("link").forEach { link ->
            val rel = link.attr("rel")
            if (!rel.contains("stylesheet", ignoreCase = true)) return@forEach
            val href = link.attr("href")
            if (href.isBlank() || ZipPaths.isExternal(href)) return@forEach
            val path = ZipPaths.resolve(baseDir, href)
            val sheetBytes = readEntry(path)
            if (sheetBytes != null) {
                val sheet = TextDecoding.decode(sheetBytes)
                val dir = ZipPaths.parentDir(path)
                css.append(CssRewriter.rewrite(sheet) { ref ->
                    ReaderAssets.epubEntry(bookKey, ZipPaths.resolve(dir, ref))
                }).append('\n')
            }
        }
        document.select("style").forEach { style ->
            css.append(CssRewriter.rewrite(style.data()) { ref ->
                ReaderAssets.epubEntry(bookKey, ZipPaths.resolve(baseDir, ref))
            }).append('\n')
        }

        val body = document.body()
        rewriteResources(body, baseDir)
        val html = HtmlSanitizer.cleanToBodyHtml(body.html())
        ChapterContent(
            index = index,
            title = chapters.getOrNull(index)?.title.orEmpty(),
            html = html,
            text = Jsoup.parse(html).text(),
            baseUrl = ReaderAssets.BASE_URL,
            css = css.toString().takeIf { it.isNotBlank() },
        )
    }

    override suspend fun search(query: String, limit: Int): List<SearchHit> = ChapterSearch.find(
        query = query,
        chapterCount = spine.size,
        limit = limit,
        titleOf = { chapters.getOrNull(it)?.title.orEmpty() },
        textLoader = { loadChapter(it).text },
    )

    /** Maps a resource URL path (as used by the reading surface) to a spine index. */
    fun chapterIndexForResourcePath(path: String): Int? {
        val prefix = "${ReaderAssets.EPUB_PREFIX}$bookKey/"
        if (!path.startsWith(prefix)) return null
        val entryPath = path.removePrefix(prefix)
        return spine.indexOfFirst { it.path == entryPath }.takeIf { it >= 0 }
    }

    override fun resolveResource(path: String): ReaderResource? {
        val prefix = "${ReaderAssets.EPUB_PREFIX}$bookKey/"
        if (!path.startsWith(prefix)) return null
        val entryPath = path.removePrefix(prefix)
        val bytes = readEntry(entryPath) ?: return null
        val mimeType = manifestByPath[entryPath]?.mediaType ?: mimeTypeOf(entryPath)
        if (mimeType == "text/css") {
            val directory = ZipPaths.parentDir(entryPath)
            val rewritten = CssRewriter.rewrite(TextDecoding.decode(bytes)) { reference ->
                ReaderAssets.epubEntry(bookKey, ZipPaths.resolve(directory, reference))
            }
            return ReaderResource(rewritten.toByteArray(Charsets.UTF_8), mimeType)
        }
        return ReaderResource(bytes, mimeType)
    }

    override suspend fun loadCover(): ByteArray? = withContext(Dispatchers.IO) {
        val item = coverItem ?: return@withContext null
        extractCoverBytes(item, depth = 0)
    }

    /**
     * Resolves the cover from whatever the book actually contains: a raster image, an SVG that
     * wraps one, or an HTML cover page that points at one.
     */
    private fun extractCoverBytes(item: Item, depth: Int): ByteArray? {
        if (depth > 3) return null
        val bytes = readEntry(item.path) ?: return null
        val mediaType = item.mediaType.ifBlank { mimeTypeOf(item.path) }.lowercase()

        if (mediaType.startsWith("image/") && !mediaType.contains("svg")) return bytes

        if (mediaType.contains("svg") || item.path.endsWith(".svg", true)) {
            val svg = Jsoup.parse(TextDecoding.decode(bytes), "", Parser.xmlParser())
            val href = svg.getElementsByTag("image").firstOrNull()?.let { element ->
                element.attr("xlink:href").ifBlank { element.attr("href") }
            }
            if (href.isNullOrBlank() || ZipPaths.isExternal(href)) return null
            val path = ZipPaths.resolve(ZipPaths.parentDir(item.path), href)
            return extractCoverBytes(Item("svg-image", path, mimeTypeOf(path)), depth + 1)
        }

        // Cover page: take the first usable image on it.
        val document = Jsoup.parse(TextDecoding.decode(bytes))
        val references = document.select("img[src], image, svg image").mapNotNull { element ->
            val candidate = element.attr("src")
                .ifBlank { element.attr("xlink:href") }
                .ifBlank { element.attr("href") }
            candidate.takeIf { it.isNotBlank() && !ZipPaths.isExternal(it) }
        }
        references.forEach { href ->
            val path = ZipPaths.resolve(ZipPaths.parentDir(item.path), href)
            extractCoverBytes(Item("page-image", path, mimeTypeOf(path)), depth + 1)
                ?.let { return it }
        }
        return null
    }

    override fun close() {
        runCatching { synchronized(zipLock) { zip.close() } }
    }

    private fun rewriteResources(root: Element, baseDir: String) {
        root.select("[src]").forEach { element ->
            val src = element.attr("src")
            if (!ZipPaths.isExternal(src)) {
                element.attr("src", ReaderAssets.epubEntry(bookKey, ZipPaths.resolve(baseDir, src)))
            }
            if (element.tagName().equals("img", ignoreCase = true)) {
                // Deliberately not `loading="lazy"`: inside a multi-column page the lazy loader
                // regularly decides a picture is "off screen" and never fetches it, which turned
                // cover pages and plates into blank pages. A chapter is loaded on its own, so
                // fetching its pictures right away costs little and always paints.
                element.attr("decoding", "async")
            }
        }
        root.select("[xlink\\:href]").forEach { element ->
            val href = element.attr("xlink:href")
            if (!ZipPaths.isExternal(href)) {
                element.attr("xlink:href", ReaderAssets.epubEntry(bookKey, ZipPaths.resolve(baseDir, href)))
            }
        }
        root.select("a[href]").forEach { element ->
            val href = element.attr("href")
            if (ZipPaths.isExternal(href)) return@forEach
            element.attr("href", ReaderAssets.epubEntry(bookKey, ZipPaths.resolve(baseDir, href)))
        }
        root.select("[style]").forEach { element ->
            val style = element.attr("style")
            if (style.isNotEmpty()) {
                element.attr(
                    "style",
                    CssRewriter.rewrite(style) { reference ->
                        ReaderAssets.epubEntry(bookKey, ZipPaths.resolve(baseDir, reference))
                    },
                )
            }
        }
    }

    private fun readEntry(path: String): ByteArray? = synchronized(zipLock) {
        val entry = findEntry(path) ?: return null
        zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun entrySize(path: String): Long? = synchronized(zipLock) {
        findEntry(path)?.size?.takeIf { it > 0 }
    }

    /**
     * Zip lookups tolerate the ways real world EPUBs differ from their own manifest: missing
     * `./` prefixes, backslashes and - very common for books authored on Windows - a different
     * letter case in the archive than in the href.
     */
    private fun findEntry(path: String): ZipEntry? = synchronized(zipLock) {
        zip.getEntry(path)?.let { return it }
        val decoded = ZipPaths.decode(path)
        zip.getEntry(decoded)?.let { return it }
        val normalised = ZipPaths.normalise(decoded).lowercase()
        entryIndex()[normalised]?.let { realName -> zip.getEntry(realName) }
    }

    private fun entryIndex(): Map<String, String> = synchronized(zipLock) {
        entryNames ?: zip.entries().asSequence()
            .filter { !it.isDirectory }
            .associate { ZipPaths.normalise(it.name).lowercase() to it.name }
            .also { entryNames = it }
    }

    companion object {
        private const val CONTAINER = "META-INF/container.xml"

        /** Never spend more than this much decompression on the background weight scan. */
        private const val WEIGHT_SCAN_BUDGET = 12L * 1024 * 1024
        /** Smallest share of an average chapter that any chapter counts for in the progress bar. */
        private const val CHAPTER_WEIGHT_FLOOR = 0.25
        private val tagPattern = Regex("<[^>]*>")
        private val whitespacePattern = Regex("\\s+")

        /** Characters a reader actually sees in one chapter document. */
        private fun textLength(html: String): Long =
            whitespacePattern.replace(tagPattern.replace(html, " "), "").length
                .toLong()
                .coerceAtLeast(1L)

        suspend fun open(context: Context, source: DocumentSource): EpubDocument =
            withContext(Dispatchers.IO) {
                val workspace = BookWorkspace(context)
                val key = workspace.keyFor(source.uri)
                val file = workspace.materialise(source, key, "epub")
                val zip = openZip(workspace, file, key) ?: run {
                    workspace.dropBookCache(key)
                    val fresh = workspace.materialise(source, key, "epub")
                    openZip(workspace, fresh, key)
                        ?: throw ParseException(ParseErrorReason.CORRUPT, "EPUB 文件已损坏")
                }

                try {
                    val opfPath = resolveOpfPath(zip)
                    val opfDir = ZipPaths.parentDir(opfPath)
                    val opf = Jsoup.parse(readText(zip, opfPath), "", Parser.xmlParser())

                    val manifest = parseManifest(opf, opfDir)
                    val manifestById = manifest.associateBy { it.id }
                    val manifestByPath = manifest.associateBy { it.path }
                    val spine = parseSpine(opf, manifestById)
                    if (spine.isEmpty()) {
                        throw ParseException(ParseErrorReason.CORRUPT, "EPUB 中没有可阅读的章节")
                    }
                    val metadata = parseMetadata(opf, source)
                    val toc = parseToc(zip, opf, opfDir, manifestByPath, spine)
                    val cover = findCover(zip, opf, manifest, manifestByPath, spine, opfDir)
                    Logs.d("EPUB 打开：${metadata.title} 章节=${spine.size} 目录=${toc.size}")
                    EpubDocument(
                        workspace = workspace,
                        bookKey = key,
                        zip = zip,
                        opfDir = opfDir,
                        spine = spine,
                        manifestById = manifestById,
                        manifestByPath = manifestByPath,
                        metadata = metadata,
                        toc = toc,
                        coverItem = cover,
                    )
                } catch (throwable: Throwable) {
                    runCatching { zip.close() }
                    throw throwable
                }
            }

        private fun openZip(workspace: BookWorkspace, file: File, key: String): ZipFile? = runCatching {
            ZipFile(file)
        }.getOrElse {
            Logs.w("EPUB 打不开：${it.message}", it)
            workspace.dropBookCache(key)
            null
        }

        private fun readText(zip: ZipFile, path: String): String {
            val entry = zip.getEntry(path)
                ?: throw ParseException(ParseErrorReason.CORRUPT, "EPUB 缺少条目：$path")
            return zip.getInputStream(entry).use { TextDecoding.decode(it.readBytes()) }
        }

        private fun resolveOpfPath(zip: ZipFile): String {
            val containerEntry = zip.getEntry(CONTAINER)
                ?: throw ParseException(ParseErrorReason.CORRUPT, "不是有效的 EPUB：缺少 container.xml")
            val xml = zip.getInputStream(containerEntry).use { TextDecoding.decode(it.readBytes()) }
            val document = Jsoup.parse(xml, "", Parser.xmlParser())
            val rootFile = document.getElementsByTag("rootfile").firstOrNull()
                ?: throw ParseException(ParseErrorReason.CORRUPT, "EPUB container.xml 中没有 rootfile")
            val fullPath = rootFile.attr("full-path")
            if (fullPath.isBlank()) {
                throw ParseException(ParseErrorReason.CORRUPT, "EPUB rootfile 缺少 full-path")
            }
            return ZipPaths.decode(fullPath).trimStart('/')
        }

        private fun parseManifest(opf: Document, opfDir: String): List<Item> =
            opf.getElementsByTag("item").mapNotNull { element ->
                val id = element.attr("id")
                val href = element.attr("href")
                if (id.isBlank() || href.isBlank()) return@mapNotNull null
                val mediaType = element.attr("media-type").ifBlank { "" }
                Item(
                    id = id,
                    path = ZipPaths.resolve(opfDir, href),
                    mediaType = mediaType,
                    properties = element.attr("properties"),
                )
            }

        private fun parseSpine(opf: Document, manifest: Map<String, Item>): List<Item> {
            val refs = opf.getElementsByTag("itemref")
            val items = refs.mapNotNull { ref ->
                val idRef = ref.attr("idref")
                val item = manifest[idRef] ?: return@mapNotNull null
                if (item.mediaType.startsWith("image/")) return@mapNotNull null
                val linear = ref.attr("linear")
                if (linear.equals("no", ignoreCase = true)) return@mapNotNull null
                item
            }
            if (items.isNotEmpty()) return items
            // Broken spine: fall back to every XHTML/HTML document in manifest order.
            return manifest.values.filter { it.isDocument }
        }

        private fun parseMetadata(opf: Document, source: DocumentSource): DocumentMetadata {
            fun firstValue(localName: String): String? {
                val element = opf.allElements.firstOrNull {
                    it.tagName().substringAfter(':').equals(localName, ignoreCase = true)
                } ?: return null
                return element.text().trim().takeIf { it.isNotEmpty() }
            }
            fun metaContent(name: String): String? = opf.getElementsByTag("meta")
                .firstOrNull { it.attr("name").equals(name, ignoreCase = true) }
                ?.attr("content")
                ?.takeIf { it.isNotBlank() }

            val title = firstValue("title") ?: source.displayName.substringBeforeLast('.')
            return DocumentMetadata(
                title = title,
                author = firstValue("creator"),
                language = firstValue("language"),
                description = firstValue("description") ?: metaContent("description"),
                publisher = firstValue("publisher"),
                publishedDate = firstValue("date"),
                identifier = firstValue("identifier"),
            )
        }

        private fun parseToc(
            zip: ZipFile,
            opf: Document,
            opfDir: String,
            manifestByPath: Map<String, Item>,
            spine: List<Item>,
        ): List<TocEntry> {
            val spineIndexByPath = spine.withIndex().associate { (index, item) -> item.path to index }

            /** [path] must already be resolved against the OPF (or the nav document). */
            fun targetOfPath(path: String, fragment: String?): Pair<Int, String?>? {
                val index = spineIndexByPath[path] ?: return null
                return index to fragment
            }

            // EPUB 3 navigation document
            val navItem = opf.getElementsByTag("item").firstOrNull {
                it.attr("properties").split(' ').any { part -> part.equals("nav", true) }
            }
            if (navItem != null) {
                val navPath = ZipPaths.resolve(opfDir, navItem.attr("href"))
                runCatching {
                    val entry = zip.getEntry(navPath) ?: return@runCatching emptyList<TocEntry>()
                    val html = zip.getInputStream(entry).use { TextDecoding.decode(it.readBytes()) }
                    val navDocument = Jsoup.parse(html)
                    val navBase = ZipPaths.parentDir(navPath)
                    val nav = navDocument.select("nav").firstOrNull { element ->
                        element.attr("epub:type").contains("toc", true) ||
                            element.attr("role").contains("doc-toc", true)
                    } ?: navDocument.selectFirst("nav")
                    val list = nav?.selectFirst("ol") ?: nav?.selectFirst("ul") ?: return@runCatching emptyList()
                    val entries = ArrayList<TocEntry>()
                    walkNavList(list, 0, navBase, ::targetOfPath, entries)
                    entries
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            }

            // EPUB 2 NCX
            val ncxItem = opf.getElementsByTag("item").firstOrNull {
                it.attr("media-type").equals("application/x-dtbncx+xml", ignoreCase = true)
            } ?: opf.getElementsByTag("spine").firstOrNull()
                ?.attr("toc")
                ?.let { id -> opf.getElementsByTag("item").firstOrNull { it.attr("id") == id } }

            if (ncxItem != null) {
                val ncxPath = ZipPaths.resolve(opfDir, ncxItem.attr("href"))
                runCatching {
                    val entry = zip.getEntry(ncxPath) ?: return@runCatching emptyList<TocEntry>()
                    val xml = zip.getInputStream(entry).use { TextDecoding.decode(it.readBytes()) }
                    val ncx = Jsoup.parse(xml, "", Parser.xmlParser())
                    val navMap = ncx.getElementsByTag("navMap").firstOrNull()
                        ?: return@runCatching emptyList()
                    val entries = ArrayList<TocEntry>()
                    navMap.children().filter { it.tagName().equals("navPoint", true) }
                        .forEach { walkNavPoint(it, 0, opfDir, ::targetOfPath, entries) }
                    entries
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            }

            // Last resort: the spine itself.
            return spine.mapIndexed { index, item ->
                TocEntry(title = "第 ${index + 1} 节", chapterIndex = index)
            }
        }

        private fun walkNavList(
            list: Element,
            level: Int,
            baseDir: String,
            targetOf: (String, String?) -> Pair<Int, String?>?,
            out: MutableList<TocEntry>,
        ) {
            list.children().filter { it.tagName().equals("li", true) }.forEach { item ->
                val anchor = item.children().firstOrNull { it.tagName().equals("a", true) }
                val label = anchor?.text()?.trim().orEmpty()
                val href = anchor?.attr("href").orEmpty()
                if (label.isNotEmpty() && href.isNotBlank()) {
                    val target = targetOf(
                        ZipPaths.resolve(baseDir, href),
                        ZipPaths.fragment(href),
                    )
                    if (target != null) {
                        out.add(TocEntry(label, target.first, target.second, level))
                    }
                }
                item.children().firstOrNull { it.tagName().equals("ol", true) || it.tagName().equals("ul", true) }
                    ?.let { walkNavList(it, level + 1, baseDir, targetOf, out) }
            }
        }

        private fun walkNavPoint(
            point: Element,
            level: Int,
            baseDir: String,
            targetOf: (String, String?) -> Pair<Int, String?>?,
            out: MutableList<TocEntry>,
        ) {
            val label = point.getElementsByTag("text").firstOrNull()?.text()?.trim().orEmpty()
            val src = point.getElementsByTag("content").firstOrNull()?.attr("src").orEmpty()
            if (label.isNotEmpty() && src.isNotBlank()) {
                val target = targetOf(
                    ZipPaths.resolve(baseDir, src),
                    ZipPaths.fragment(src),
                )
                if (target != null) out.add(TocEntry(label, target.first, target.second, level))
            }
            point.children().filter { it.tagName().equals("navPoint", true) }
                .forEach { walkNavPoint(it, level + 1, baseDir, targetOf, out) }
        }

        private val coverNamePattern = Regex(
            "(^|[^a-z])(cover|front|titlepage|title-page|封面)",
            RegexOption.IGNORE_CASE,
        )

        private val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif")

        /**
         * Cover detection follows every convention found in the wild, in order of reliability:
         * EPUB 3 `properties="cover-image"`, EPUB 2 `<meta name="cover">` (which may hold either a
         * manifest id or a file name), the guide reference, a file name that says "cover", the
         * usual "one image on the first page" cover, and finally the first image of the book.
         */
        private fun findCover(
            zip: ZipFile,
            opf: Document,
            manifest: List<Item>,
            manifestByPath: Map<String, Item>,
            spine: List<Item>,
            opfDir: String,
        ): Item? {
            manifest.firstOrNull { item ->
                item.properties.split(' ').any { part -> part.equals("cover-image", true) }
            }?.let { return it }

            val metaCover = opf.getElementsByTag("meta")
                .firstOrNull { it.attr("name").equals("cover", true) }
                ?.attr("content")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (metaCover != null) {
                manifest.firstOrNull { it.id == metaCover }?.let { return it }
                val asPath = ZipPaths.resolve(opfDir, metaCover)
                manifestByPath[asPath]?.let { return it }
                val asFileName = metaCover.substringAfterLast('/')
                manifest.firstOrNull { item ->
                    item.path.substringAfterLast('/').equals(asFileName, ignoreCase = true)
                }?.let { return it }
            }

            opf.getElementsByTag("reference")
                .firstOrNull { it.attr("type").equals("cover", true) }
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?.let { href ->
                    val path = ZipPaths.resolve(opfDir, href)
                    manifestByPath[path]?.let { return it }
                    return Item("guide-cover", path, mimeTypeOf(path))
                }

            val images = manifest.filter { item ->
                item.mediaType.startsWith("image/") ||
                    item.path.substringAfterLast('.', "").lowercase() in imageExtensions
            }

            images.firstOrNull { item ->
                coverNamePattern.containsMatchIn(item.path.substringAfterLast('/'))
            }?.let { return it }

            val firstSpine = spine.firstOrNull()
            if (firstSpine != null) {
                if (firstSpine.mediaType.startsWith("image/")) return firstSpine
                if (looksLikeCoverPage(zip, firstSpine)) return firstSpine
            }

            return images.firstOrNull()
        }

        /**
         * A cover page is a document with (almost) no text whose whole point is one image - that is
         * what distinguishes it from a real first chapter.
         */
        private fun looksLikeCoverPage(zip: ZipFile, item: Item): Boolean = runCatching {
            val entry = zip.getEntry(item.path)
                ?: zip.entries().asSequence().firstOrNull {
                    it.name.equals(item.path, ignoreCase = true)
                }
                ?: return false
            val html = zip.getInputStream(entry).use { TextDecoding.decode(it.readBytes()) }
            val document = Jsoup.parse(html)
            val hasImage = document.select("img, svg image, image, svg").isNotEmpty()
            val text = document.body().text().trim()
            hasImage && text.length < 48
        }.getOrDefault(false)

    fun mimeTypeOf(path: String): String {
            val extension = path.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                "bmp" -> "image/bmp"
                "css" -> "text/css"
                "js" -> "application/javascript"
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                "xhtml", "html", "htm" -> "application/xhtml+xml"
                "ncx" -> "application/x-dtbncx+xml"
                "xml" -> "application/xml"
                else -> "application/octet-stream"
            }
        }
    }
}
