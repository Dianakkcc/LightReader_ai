package com.lightreader.ui.reader

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.AppContainer
import com.lightreader.core.format.ParseErrorReason
import com.lightreader.core.format.ParseException
import com.lightreader.core.format.ProgressMath
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.format.epub.EpubDocument
import com.lightreader.core.format.pdf.PdfDocument
import com.lightreader.core.format.comic.ComicDocument
import com.lightreader.core.model.ShelfKind
import com.lightreader.core.format.txt.TxtDocument
import com.lightreader.core.model.BitmapPagedDocument
import com.lightreader.core.model.Book
import com.lightreader.core.model.Bookmark
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ChapterRef
import com.lightreader.core.model.DocumentKind
import com.lightreader.core.model.ImagePagedDocument
import com.lightreader.core.model.OpenedDocument
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.ReaderResource
import com.lightreader.core.model.ReadingLocation
import com.lightreader.core.model.ReflowableDocument
import com.lightreader.core.model.SearchHit
import com.lightreader.core.model.TocEntry
import com.lightreader.core.util.Logs
import com.lightreader.core.util.TextUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Page shape assumed until a decoded page reveals the real one (roughly A4). */
private const val DEFAULT_PAGE_ASPECT = 0.707f

/**
 * Owns one open book: the format independent document, the reading position, bookmarks,
 * in-book search and the reader settings that apply to this session.
 */
class ReaderViewModel(
    private val container: AppContainer,
    private val bookId: Long,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val errorMessage: String? = null,
        val errorReason: ParseErrorReason? = null,
        val book: Book? = null,
        val title: String = "",
        val author: String? = null,
        val chapterIndex: Int = 0,
        val currentOffset: Int = 0,
        val chapter: ChapterContent? = null,
        val chapters: List<ChapterRef> = emptyList(),
        val toc: List<TocEntry> = emptyList(),
        val analysingToc: Boolean = false,
        val pageIndex: Int = 0,
        val pageCount: Int = 0,
        val percentage: Float = 0f,
        val bookmarks: List<Bookmark> = emptyList(),
        val query: String = "",
        val results: List<SearchHit> = emptyList(),
        val searching: Boolean = false,
        val settings: ReaderSettings = ReaderSettings(),
        val kind: DocumentKind = DocumentKind.REFLOWABLE,
        val pendingAnchor: String? = null,
        val renderTick: Int = 0,
        val appThemeMode: com.lightreader.core.model.AppThemeMode =
            com.lightreader.core.model.AppThemeMode.SYSTEM,
        /** The reader is at the very end of the chapter: offer to continue with the next one. */
        val atChapterEnd: Boolean = false,
        val hasNextChapter: Boolean = false,
        /** Comic archives read as one continuous vertical flow of pages, not page by page. */
        val isComicFlow: Boolean = false,
        /** First page of every chapter: separators in the comic flow and entries in the contents. */
        val comicChapterAtPage: Map<Int, String> = emptyMap(),
    ) {
        val isPagedDocument: Boolean get() = kind == DocumentKind.FIXED

        val currentChapterTitle: String
            get() = chapter?.title ?: chapters.getOrNull(chapterIndex)?.title.orEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var document: OpenedDocument? = null
    @Volatile
    private var reflowable: ReflowableDocument? = null
    private var pagedDocument: BitmapPagedDocument? = null
    private var imageDocument: ImagePagedDocument? = null
    private var comicDocument: ComicDocument? = null

    /** Offset the next render must scroll to; null means "stay where the user is". */
    private var restoreOffset: Int? = null
    private var lastReportedOffset: Int = 0
    private var lastLocationLogAt: Long = 0L
    private var lastSaved: ReadingLocation = ReadingLocation()
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var indexJob: Job? = null
    private var prefetchJob: Job? = null
    private val prefetched = HashMap<Int, ChapterContent>()

    /** Set when the reader asked to continue before the chapter index finished. */
    private var pendingContinue = false
    /** A "pull down at the top" request that arrived while the previous chapter was still loading. */
    private var pendingStart = false

    init {
        observePersistentState()
        open()
    }

    private fun observePersistentState() {
        viewModelScope.launch {
            container.settingsStore.settings.collect { settings ->
                _state.update {
                    it.copy(settings = settings.reader, appThemeMode = settings.themeMode)
                }
            }
        }
        viewModelScope.launch {
            container.readingRepository.observeBookmarks(bookId).collect { bookmarks ->
                _state.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    private fun open() {
        viewModelScope.launch {
            val book = container.libraryRepository.findBook(bookId)
            if (book == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = AppStrings.get(R.string.ui_this_book_is_no_longer_in_your_library_130),
                        errorReason = ParseErrorReason.MISSING,
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(book = book, title = book.title, author = book.author, loading = true)
            }
            container.libraryRepository.markOpened(bookId)

            val result = runCatching { container.openDocument(book) }
            result.onFailure { throwable ->
                Logs.w(AppStrings.get(R.string.ui_could_not_open_book_131, throwable.message), throwable)
                _state.update {
                    it.copy(
                        loading = false,
                        errorReason = (throwable as? ParseException)?.reason
                            ?: ParseErrorReason.UNKNOWN,
                        errorMessage = throwable.message ?: AppStrings.get(R.string.error_open_title),
                    )
                }
            }
            result.onSuccess { opened ->
                document = opened
                val progress = container.readingRepository.loadProgress(bookId)
                lastSaved = progress
                applyMetadata(opened, book)
                when (opened) {
                    is ReflowableDocument -> {
                        reflowable = opened
                        prepareReflowable(opened, progress)
                    }

                    is BitmapPagedDocument -> {
                        pagedDocument = opened
                        comicDocument = opened as? ComicDocument
                        // PDF is the one bitmap document with its own outline; comics bring their
                        // chapter list with them.
                        preparePaged(opened, progress, isPdf = opened is PdfDocument)
                    }

                    is ImagePagedDocument -> {
                        imageDocument = opened
                        preparePaged(opened, progress, isPdf = false)
                    }

                    else -> _state.update {
                        it.copy(loading = false, errorMessage = AppStrings.get(R.string.ui_this_format_is_not_supported_132))
                    }
                }
            }
        }
    }

    private suspend fun applyMetadata(opened: OpenedDocument, book: Book) {
        val title = opened.metadata.title?.takeIf { it.isNotBlank() } ?: book.title
        val author = opened.metadata.author?.takeIf { it.isNotBlank() } ?: book.author
        _state.update { it.copy(title = title, author = author) }
        if (title != book.title || author != book.author) {
            container.libraryRepository.updateMetadata(bookId, title, author)
        }
    }

    private suspend fun prepareReflowable(opened: ReflowableDocument, progress: ReadingLocation) {
        val chapters = opened.chapters
        val index = progress.chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        restoreOffset = progress.charOffset.takeIf { index == progress.chapterIndex }
        lastReportedOffset = progress.charOffset
        val indexing = opened is TxtDocument && !opened.isIndexed
        _state.update {
            it.copy(
                kind = DocumentKind.REFLOWABLE,
                chapters = chapters,
                toc = opened.toc,
                chapterIndex = index,
                percentage = progress.percentage,
                pageCount = 0,
                loading = true,
                // Raised before the first chapter is rendered: a continue request that arrives
                // while the provisional chunk list is still on screen must be queued, otherwise
                // "reached the end" is mistaken for "this book has only one chapter" and the
                // request is dropped on the floor.
                analysingToc = indexing,
            )
        }
        loadChapter(index)

        if (indexing) {
            indexJob?.cancel()
            indexJob = viewModelScope.launch {
                runCatching { opened.buildIndex() }
                _state.update {
                    // The chapter list just changed (provisional chunks -> real chapters), so every
                    // value derived from it has to be recomputed. Forgetting this is what made
                    // "continue to the next chapter" silently do nothing on a TXT file.
                    it.copy(
                        chapters = opened.chapters,
                        toc = opened.toc,
                        analysingToc = false,
                        hasNextChapter = it.chapterIndex + 1 < opened.chapters.size,
                    )
                }
                if (pendingContinue) {
                    // The reader reached the end while the index was still running: honour it now.
                    pendingContinue = false
                    onReachEnd()
                }
            }
        }

        // A progress bar should say how much *text* is left. File size is only a first guess, so the
        // real character counts are measured in the background and the percentage is refreshed.
        viewModelScope.launch {
            val changed = runCatching { opened.refineWeights() }.getOrDefault(false)
            if (!changed) return@launch
            _state.update { state ->
                val fraction = state.chapter?.let { content ->
                    ProgressMath.chapterFraction(state.currentOffset, content.text.length)
                } ?: 0f
                state.copy(
                    chapters = opened.chapters,
                    percentage = ProgressMath.percent(opened.chapters, state.chapterIndex, fraction),
                )
            }
            scheduleSave()
        }
    }

    private suspend fun preparePaged(
        opened: OpenedDocument,
        progress: ReadingLocation,
        isPdf: Boolean,
    ) {
        val pageCount = (opened as? BitmapPagedDocument)?.pageCount
            ?: (opened as? ImagePagedDocument)?.pageCount
            ?: 1
        val pageIndex = progress.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val comic = opened as? ComicDocument
        // The shelf decides how a fixed layout document is read: comic archives always flow, and
        // anything the reader filed under 漫画 (a PDF of scans, a folder of images) flows too.
        val asComicFlow = comic != null || _state.value.book?.shelfKind == ShelfKind.COMIC
        _state.update {
            it.copy(
                kind = DocumentKind.FIXED,
                pageCount = pageCount,
                pageIndex = pageIndex,
                percentage = if (pageCount > 0) (pageIndex + 1f) / pageCount else 0f,
                chapters = opened.chapters,
                toc = opened.toc,
                isComicFlow = asComicFlow,
                comicChapterAtPage = when {
                    comic != null -> comic.toc.associate { it.chapterIndex to it.title }
                    // A PDF outline doubles as the chapter separators of the flow.
                    asComicFlow -> opened.toc
                        .filter { it.title.isNotBlank() && it.chapterIndex in 0 until pageCount }
                        .associate { it.chapterIndex to it.title }
                    else -> emptyMap()
                },
                loading = false,
            )
        }
        if (isPdf) {
            (opened as? com.lightreader.core.format.pdf.PdfDocument)?.let { pdf ->
                viewModelScope.launch {
                    val outline = runCatching { pdf.loadOutline() }.getOrDefault(emptyList())
                    if (outline.isNotEmpty()) {
                        _state.update { it.copy(toc = outline) }
                    }
                }
            }
        }
    }

    private suspend fun loadChapter(index: Int) {
        val opened = reflowable ?: return
        _state.update { it.copy(loading = true) }
        val result = runCatching { opened.loadChapter(index) }
        result.onSuccess { chapter ->
            _state.update {
                it.copy(
                    chapter = chapter,
                    chapterIndex = index,
                    loading = false,
                    atChapterEnd = false,
                    hasNextChapter = index + 1 < opened.chapters.size,
                )
            }
            scheduleSave()
            prefetch(index + 1)
            if (pendingContinue) {
                pendingContinue = false
                onReachEnd()
            }
            if (pendingStart) {
                pendingStart = false
                onReachStart()
            }
        }
        result.onFailure { throwable ->
            Logs.w(AppStrings.get(R.string.ui_could_not_load_chapter_133, throwable.message), throwable)
            _state.update {
                it.copy(loading = false, errorMessage = throwable.message ?: AppStrings.get(R.string.ui_could_not_load_chapter_134))
            }
        }
    }

    // ------------------------------------------------------------- reading

    fun onLocationReported(
        chapterIndex: Int,
        charOffset: Int,
        fraction: Float,
        pages: Int,
        page: Int,
    ) {
        val previous = _state.value
        val unchanged = previous.chapterIndex == chapterIndex &&
            kotlin.math.abs(previous.currentOffset - charOffset) < 6 &&
            previous.pageIndex == page &&
            previous.pageCount == pages
        if (unchanged) return
        lastReportedOffset = charOffset
        _state.update { it.copy(currentOffset = charOffset) }
        val opened = reflowable
        val percentage = if (opened != null) {
            ProgressMath.percent(opened.chapters, chapterIndex, fraction)
        } else {
            _state.value.percentage
        }
        val now = System.currentTimeMillis()
        if (now - lastLocationLogAt > 1000) {
            lastLocationLogAt = now
            Logs.d(
                "阅读位置：第 ${chapterIndex + 1} 章 offset=$charOffset " +
                    "fraction=${"%.3f".format(fraction)} page=$page/$pages 全书=${"%.1f".format(percentage * 100)}%",
            )
        }
        _state.update {
            it.copy(
                percentage = percentage,
                pageCount = if (opened != null) pages else it.pageCount,
                pageIndex = if (opened != null) page else it.pageIndex,
                atChapterEnd = if (opened != null) {
                    if (pages > 1) page >= pages - 1 && fraction > 0.9f else fraction > 0.9f
                } else {
                    it.atChapterEnd
                },
            )
        }
        scheduleSave()
    }

    /** Loads the following chapter in the background so continuing never waits. */
    private fun prefetch(index: Int) {
        val opened = reflowable ?: return
        if (index >= opened.chapters.size || prefetched.containsKey(index)) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            runCatching { opened.loadChapter(index) }
                .getOrNull()
                ?.let { prefetched[index] = it }
        }
    }

    fun goToChapter(index: Int, anchor: String? = null, offset: Int? = null) {
        val opened = reflowable ?: return
        if (index !in opened.chapters.indices) return
        restoreOffset = offset ?: 0
        lastReportedOffset = offset ?: 0
        _state.update { it.copy(pendingAnchor = anchor) }
        viewModelScope.launch { loadChapter(index) }
    }

    fun consumeAnchor() {
        _state.update { it.copy(pendingAnchor = null) }
    }

    fun resolveResource(path: String): ReaderResource? =
        reflowable?.resolveResource(path)

    /** Called by the reading surface once a chapter has finished laying out. */
    fun onRenderReady() {
        _state.update { it.copy(renderTick = it.renderTick + 1, loading = false) }
    }

    /**
     * Follows a link inside the book (EPUB chapter cross reference). External links are ignored:
     * opening a browser is not what a reader should do behind the user's back.
     */
    fun openInternalLink(url: String) {
        val path = url.removePrefix(ReaderAssets.BASE_URL)
        val fragment = path.substringAfterLast('#', "").takeIf { '#' in path }
        val cleanPath = path.substringBefore('#')
        val epub = reflowable as? EpubDocument ?: return
        val index = epub.chapterIndexForResourcePath(cleanPath) ?: return
        goToChapter(index, anchor = fragment)
    }

    fun goToTocEntry(entry: TocEntry) {
        if (_state.value.isPagedDocument) {
            goToPage(entry.chapterIndex)
        } else {
            goToChapter(entry.chapterIndex, entry.anchor)
        }
    }

    /**
     * The reader reached the bottom of the current chapter (continuous scrolling) or tried to turn
     * past the last page. Continue with the next chapter so reading never stops at a chapter edge.
     */
    fun onReachEnd() {
        val current = _state.value
        if (current.isPagedDocument) {
            Logs.d("续读：分页文档（PDF/图片），不做换章")
            return
        }
        val next = current.chapterIndex + 1
        val count = current.chapters.size
        Logs.d(
            "续读请求：当前第 ${current.chapterIndex + 1} 章 / 共 $count 章 " +
                "loading=${current.loading} analysingToc=${current.analysingToc}",
        )
        if (count == 0 || next >= count) {
            if (current.analysingToc) {
                Logs.d("章节索引尚未完成，先记下续读请求")
                pendingContinue = true
                return
            }
            Logs.d("已到最后一章，没有下一章可续读")
            return
        }
        if (current.loading) {
            // A chapter is still being rendered; continue as soon as it settles.
            Logs.d("正在加载中，稍后续读下一章")
            // A flag is used instead of polling: the hand over can then neither be missed nor
            // fire twice, no matter how slow the chapter load is.
            pendingContinue = true
            return
        }
        Logs.d("续读下一章：$next / $count")
        val ready = prefetched.remove(next)
        if (ready == null) {
            goToChapter(next, offset = 0)
            return
        }
        // Seamless hand over: the next chapter is already parsed, so the reader just swaps pages.
        restoreOffset = 0
        lastReportedOffset = 0
        _state.update {
            it.copy(
                chapter = ready,
                chapterIndex = next,
                currentOffset = 0,
                atChapterEnd = false,
                hasNextChapter = next + 1 < it.chapters.size,
            )
        }
        scheduleSave()
        prefetch(next + 1)
    }

    /**
     * The reader pulled down at the very top of a chapter: continue with the chapter before this
     * one, landing at its end - that is where the reader was heading.
     */
    fun onReachStart() {
        val current = _state.value
        if (current.isPagedDocument) return
        val previous = current.chapterIndex - 1
        Logs.d(
            "向前续读请求：当前第 ${current.chapterIndex + 1} 章 / 共 ${current.chapters.size} 章",
        )
        if (previous < 0 || current.loading) {
            if (current.loading) {
                // The previous chapter is already being rendered; remember the request.
                pendingStart = true
            }
            return
        }
        Logs.d("向前续读上一章：$previous / ${current.chapters.size}")
        goToChapter(previous, offset = Int.MAX_VALUE)
    }

    fun goToPage(index: Int) {
        val pageCount = _state.value.pageCount
        if (pageCount <= 0) return
        val target = index.coerceIn(0, pageCount - 1)
        _state.update {
            it.copy(
                pageIndex = target,
                percentage = (target + 1f) / pageCount,
            )
        }
        scheduleSave()
    }

    fun nextPage() = goToPage(_state.value.pageIndex + 1)

    fun prevPage() = goToPage(_state.value.pageIndex - 1)

    fun previousPage() = goToPage(_state.value.pageIndex - 1)

    /** Coarse seek used by the progress bar at the bottom of the reader. */
    fun seekTo(percent: Float) {
        val current = _state.value
        if (current.isPagedDocument) {
            val pageCount = current.pageCount
            if (pageCount > 0) goToPage((percent * pageCount).toInt().coerceIn(0, pageCount - 1))
            return
        }
        val opened = reflowable ?: return
        val (index, _) = ProgressMath.chapterAt(opened.chapters, percent)
        goToChapter(index, offset = 0)
    }

    fun initialOffsetForRender(): Int {
        val pending = restoreOffset
        restoreOffset = null
        return pending ?: lastReportedOffset
    }

    // ------------------------------------------------------------ progress

    fun currentLocation(): ReadingLocation {
        val current = _state.value
        return if (current.isPagedDocument) {
            ReadingLocation(
                chapterIndex = 0,
                charOffset = 0,
                pageIndex = current.pageIndex,
                zoom = 1f,
                percentage = current.percentage,
                pageCount = current.pageCount,
            )
        } else {
            ReadingLocation(
                chapterIndex = current.chapterIndex,
                charOffset = lastReportedOffset,
                pageIndex = 0,
                percentage = current.percentage,
            )
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveProgressNow()
        }
    }

    private suspend fun saveProgressNow() {
        val location = currentLocation()
        if (location.isCloseTo(lastSaved)) return
        container.readingRepository.saveProgress(bookId, location)
        lastSaved = location
    }

    /**
     * Called when the reader is left or the app goes to the background; saved on an application
     * scope so the write completes even though this ViewModel is being cleared.
     */
    fun flushProgress() {
        val location = currentLocation()
        container.appScope.launch {
            runCatching { container.readingRepository.saveProgress(bookId, location) }
        }
    }

    // ----------------------------------------------------------- bookmarks

    fun currentSnippet(): String {
        val text = _state.value.chapter?.text.orEmpty()
        if (text.isEmpty()) return ""
        return TextUtil.snippet(text, lastReportedOffset)
    }

    fun addBookmark(note: String? = null) {
        val current = _state.value
        val location = currentLocation()
        val title = if (current.isPagedDocument) {
            AppStrings.get(R.string.ui_page_145, current.pageIndex + 1)
        } else {
            current.currentChapterTitle.ifBlank { AppStrings.get(R.string.ui_section_77, current.chapterIndex + 1) }
        }
        viewModelScope.launch {
            container.readingRepository.addBookmark(
                bookId = bookId,
                location = location,
                title = title,
                snippet = currentSnippet(),
                note = note,
            )
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { container.readingRepository.deleteBookmark(bookmark.id) }
    }

    fun toggleBookmark() {
        val current = currentLocation()
        val existing = _state.value.bookmarks.firstOrNull { bookmark ->
            if (current.chapterIndex == 0 && _state.value.isPagedDocument) {
                bookmark.pageIndex == current.pageIndex
            } else {
                bookmark.chapterIndex == current.chapterIndex &&
                    kotlin.math.abs(bookmark.charOffset - current.charOffset) < 64
            }
        }
        if (existing != null) {
            deleteBookmark(existing)
        } else {
            addBookmark()
        }
    }

    fun jumpToBookmark(bookmark: Bookmark) {
        if (_state.value.isPagedDocument) {
            goToPage(bookmark.pageIndex)
        } else {
            goToChapter(bookmark.chapterIndex, offset = bookmark.charOffset)
        }
    }

    // -------------------------------------------------------------- search

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(query = "", results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, query = query, results = emptyList()) }
            val opened = document
            val results = when (opened) {
                is ReflowableDocument -> runCatching { opened.search(query, 200) }
                    .getOrDefault(emptyList())

                is com.lightreader.core.format.pdf.PdfDocument -> runCatching {
                    opened.search(query, 200)
                }.getOrDefault(emptyList())

                else -> emptyList()
            }
            _state.update { it.copy(searching = false, results = results) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(query = "", results = emptyList(), searching = false) }
    }

    fun jumpToSearchHit(hit: SearchHit) {
        if (_state.value.isPagedDocument) {
            goToPage(hit.pageIndex)
        } else {
            goToChapter(hit.chapterIndex, offset = hit.charOffset)
        }
    }

    // ------------------------------------------------------------ settings

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { container.settingsStore.updateReader(transform) }
    }

    /** Selecting a shipped font unpacks it out of the APK first. */
    fun selectFont(font: com.lightreader.core.model.FontItem) {
        viewModelScope.launch {
            val ready = container.readingRepository.prepareFont(font)
            container.settingsStore.updateReader {
                it.copy(
                    fontId = ready.id,
                    fontFamily = ready.displayName,
                    fontPath = ready.path,
                    fontDisplayName = ready.displayName,
                )
            }
        }
    }

    fun importFont(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val font = runCatching { container.readingRepository.importFont(uri) }.getOrNull()
            if (font != null) {
                container.settingsStore.updateReader {
                    it.copy(
                        fontId = font.id,
                        fontFamily = font.displayName,
                        fontPath = font.path,
                        fontDisplayName = font.displayName,
                    )
                }
            }
            onResult(font != null)
        }
    }

    fun useSystemFont() {
        updateSettings { it.copy(fontId = null, fontFamily = null, fontPath = null, fontDisplayName = null) }
    }

    // --------------------------------------------------------------- pages

    private val pageDecodeSlots = Semaphore(2)

    suspend fun loadPageBitmap(pageIndex: Int, targetWidth: Int, maxHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            if (targetWidth <= 0 || maxHeight < 0) return@withContext null
            val key = "page:$bookId:$pageIndex:$targetWidth x $maxHeight"
            container.pageCache.get(key)?.let { return@withContext it }
            pageDecodeSlots.withPermit {
                container.pageCache.get(key)?.let { return@withPermit it }
                val bitmap = when {
                    pagedDocument != null -> renderPdfPage(pageIndex, targetWidth, maxHeight)
                    imageDocument != null -> renderImagePage(pageIndex, targetWidth, maxHeight)
                    else -> null
                }
                if (bitmap != null) {
                    bitmap.prepareToDraw()
                    container.pageCache.put(key, bitmap)
                    // A decoded page is the ground truth for the flow's layout, so remember its shape.
                    if (bitmap.height > 0) {
                        learnedAspects[pageIndex] = bitmap.width.toFloat() / bitmap.height.toFloat()
                    }
                }
                bitmap
            }
        }

    /** Ratios learned from decoded bitmaps, for documents that cannot tell us up front. */
    private val learnedAspects = java.util.concurrent.ConcurrentHashMap<Int, Float>()

    /**
     * Width/height ratio of a page, known *before* it is decoded: a comic archive reads it from the
     * image header and a PDF from its page box. Everything else starts from a default and is
     * corrected the moment the page lands (the flow re-measures on the decoded bitmap).
     */
    fun pagedPageAspect(pageIndex: Int): Float {
        val comic = comicDocument
        if (comic != null) return comic.pageAspect(pageIndex)
        val pdf = pagedDocument as? com.lightreader.core.format.pdf.PdfDocument
        if (pdf != null) return pdf.pageAspect(pageIndex)
        return learnedAspects[pageIndex] ?: DEFAULT_PAGE_ASPECT
    }

    private suspend fun renderPdfPage(
        pageIndex: Int,
        targetWidth: Int,
        maxHeight: Int,
    ): Bitmap? {
        val document = pagedDocument ?: return null
        if (pageIndex !in 0 until document.pageCount) return null
        val pdf = document as? com.lightreader.core.format.pdf.PdfDocument
        if (pdf == null) {
            // Comics and other bitmap documents already know the shape of their own pages.
            return runCatching { document.renderPage(pageIndex, targetWidth, maxHeight) }.getOrNull()
        }
        val aspect = pdf.pageAspect(pageIndex)
        var width = targetWidth
        var height = (width / aspect.coerceAtLeast(0.05f)).toInt()
        if (maxHeight > 0 && height > maxHeight) {
            height = maxHeight
            width = (height * aspect).toInt()
        }
        return runCatching { document.renderPage(pageIndex, width, height) }.getOrNull()
    }

    private suspend fun renderImagePage(
        pageIndex: Int,
        targetWidth: Int,
        maxHeight: Int,
    ): Bitmap? {
        val document = imageDocument ?: return null
        val uri = document.imageUris.getOrNull(pageIndex) ?: return null
        return runCatching { container.imageLoader.load(uri, targetWidth, maxHeight) }.getOrNull()
    }

    override fun onCleared() {
        saveJob?.cancel()
        searchJob?.cancel()
        indexJob?.cancel()
        prefetchJob?.cancel()
        runCatching { document?.close() }
        document = null
        super.onCleared()
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 700L
    }
}
