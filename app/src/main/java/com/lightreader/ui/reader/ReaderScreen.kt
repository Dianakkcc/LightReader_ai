package com.lightreader.ui.reader

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightreader.AppContainer
import com.lightreader.core.model.FontItem
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.ThemePreference
import com.lightreader.core.util.Formatters
import com.lightreader.ui.theme.ReaderTheme
import com.lightreader.ui.theme.ThemeMapping
import com.lightreader.ui.theme.readerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.lightreader.ui.theme.LocalAppIcons

internal enum class ReaderPanel { NONE, TOC, SEARCH, BOOKMARKS, SETTINGS, THEME }

private val TopBarHeight: Dp = 56.dp
private val BottomBarHeight: Dp = 126.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: ReaderViewModel = viewModel(
        key = "reader-$bookId",
        factory = viewModelFactory {
            initializer { ReaderViewModel(container, bookId) }
        },
    )
    // The raw holder is kept so callbacks can read the latest state without subscribing the whole
    // screen to every progress tick.
    val stateHolder = viewModel.state.collectAsStateWithLifecycle()
    val state by stateHolder
    val settings = state.settings
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (state.appThemeMode) {
        com.lightreader.core.model.AppThemeMode.DARK -> true
        com.lightreader.core.model.AppThemeMode.LIGHT -> false
        com.lightreader.core.model.AppThemeMode.SYSTEM -> systemDark
    }
    val readerTheme = ThemeMapping.readerThemeOf(settings.theme, effectiveDark)

    val palette = readerPalette(readerTheme)

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val expanded = configuration.screenWidthDp >= 840
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var barsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(ReaderPanel.NONE) }
    // The panel that is still on screen while it slides away, plus how far the slide is.
    var visiblePanel by remember { mutableStateOf(ReaderPanel.NONE) }
    val panelVisible = panel != ReaderPanel.NONE && !(expanded && panel == ReaderPanel.TOC)
    if (panelVisible) visiblePanel = panel
    // Keep the animated State unread here: only draw/layer code reads each frame.
    // Reading it in ReaderScreen recomposed the entire reader and panel on every animation frame.
    val panelProgress = animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "readerPanel",
    )
    LaunchedEffect(panelVisible) {
        if (!panelVisible) {
            androidx.compose.runtime.snapshotFlow { panelProgress.value }.first { it <= 0f }
            visiblePanel = ReaderPanel.NONE
        }
    }
    var seekValue by remember { mutableStateOf<Float?>(null) }
    val controller = remember { WebReaderController() }

    val fonts by produceState(initialValue = emptyList<FontItem>(), container) {
        container.readingRepository.observeFonts().collect { value = it }
    }

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFont(uri) { success ->
                scope.launch {
                    snackbarHostState.showToast(
                        if (success) AppStrings.get(R.string.ui_font_imported_114) else AppStrings.get(R.string.settings_font_invalid),
                    )
                }
            }
        }
    }

    fun openPanel(target: ReaderPanel) {
        panel = target
        barsVisible = false
    }

    fun closePanel(showBars: Boolean = true) {
        panel = ReaderPanel.NONE
        if (showBars) barsVisible = true
    }

    val rememberOffsetProvider = remember { { viewModel.initialOffsetForRender() } }
    val resolverImpl: (String) -> com.lightreader.core.model.ReaderResource? =
        { path -> viewModel.resolveResource(path) }
    val rememberResolver = remember { resolverImpl }
    val rememberLocationCallback = remember {
        { index: Int, offset: Int, fraction: Float, pages: Int, page: Int ->
            viewModel.onLocationReported(index, offset, fraction, pages, page)
        }
    }
    val rememberReadyCallback = remember { { _: Int -> viewModel.onRenderReady() } }
    val rememberReachEndCallback = remember { { viewModel.onReachEnd() } }
    val rememberReachStartCallback = remember { { viewModel.onReachStart() } }
    val rememberComicAspect = remember { { page: Int -> viewModel.pagedPageAspect(page) } }
    val rememberVisiblePageCallback = remember { { page: Int -> viewModel.goToPage(page) } }
    val rememberLinkCallback = remember { { url: String -> viewModel.openInternalLink(url) } }
    val rememberEdgeSwipeCallback = remember { { openPanel(ReaderPanel.TOC) } }
    val rememberTapCallback = remember {
        { x: Float, _: Float, width: Int, _: Int ->
            if (panel != ReaderPanel.NONE) {
                // Tablets keep the table of contents open next to the text; tapping closes it.
                closePanel()
            } else if (barsVisible) {
                // While the toolbars are open, a tap anywhere on the page closes them.
                barsVisible = false
                panel = ReaderPanel.NONE
            } else if (stateHolder.value.settings.readingMode == ReadingMode.PAGED) {
                val fraction = x / width.coerceAtLeast(1)
                when {
                    fraction < 0.32f -> controller.prevPage()
                    fraction > 0.68f -> controller.nextPage()
                    else -> barsVisible = true
                }
            } else {
                barsVisible = true
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushProgress()
        }
    }

    BackHandler {
        when {
            panel != ReaderPanel.NONE -> closePanel()
            barsVisible -> barsVisible = false
            else -> {
                viewModel.flushProgress()
                onBack()
            }
        }
    }

    // Chapter anchors (EPUB internal links, table of contents entries).
    LaunchedEffect(state.pendingAnchor, state.chapter?.index) {
        val anchor = state.pendingAnchor ?: return@LaunchedEffect
        if (state.chapter != null) {
            controller.scrollToId(anchor)
            viewModel.consumeAnchor()
        }
    }

    // Keep in-book search highlights in sync with the freshly rendered chapter.
    LaunchedEffect(state.renderTick, state.query) {
        if (state.renderTick > 0 && state.query.isNotBlank()) {
            controller.highlight(state.query)
        }
    }

    // The reading area keeps a *constant* inset for the system bars only. The toolbars slide over
    // the page instead of resizing it: re-laying out a chapter every time the UI appears makes the
    // text jump around (and forces a full re-pagination), which is worse than briefly covering a
    // few lines - the page itself never moves, so the eye keeps its place.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = statusBarTop
    val bottomInset = navigationBarBottom

    // A comic's chapters are page ranges inside one continuous flow, so the chapter that is named
    // and highlighted is derived from the current page instead of a stored chapter index.
    val comicChapterStart = comicChapterStartFor(state.pageIndex, state.comicChapterAtPage)
    val subtitleText = when {
        state.isComicFlow -> state.comicChapterAtPage[comicChapterStart].orEmpty()
        state.isPagedDocument -> ""
        else -> state.currentChapterTitle
    }
    val tocCurrentIndex = if (state.isComicFlow) comicChapterStart else state.chapterIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
                val chapter = state.chapter
                Box(
                    // padding() before fillMaxSize(): the other order forces an exact size first and
                    // the reading surface then keeps the *window* height while being pushed down by
                    // the status bar - 136px of the page (two lines of text, the hint pill) ended up
                    // below the window edge and was simply clipped away.
                    modifier = Modifier
                        .padding(top = topInset, bottom = bottomInset)
                        .fillMaxSize(),
                ) {
                    when {
                        state.errorMessage != null -> Unit

                        // A comic archive reads as one continuous vertical flow (webtoon style):
                        // scrolling never stops at a chapter edge, chapters are separators in the
                        // flow and entries in the table of contents.
                        state.isComicFlow -> ComicFlowView(
                            pageCount = state.pageCount,
                            startPage = state.pageIndex,
                            chapterTitleAt = { page -> state.comicChapterAtPage[page] },
                            background = palette.background,
                            loadPage = { page, width, _ ->
                                viewModel.loadPageBitmap(page, width, 0)
                            },
                            pageAspect = rememberComicAspect,
                            onVisiblePage = rememberVisiblePageCallback,
                            onTapCenter = {
                                if (panel != ReaderPanel.NONE) {
                                    closePanel()
                                } else {
                                    barsVisible = !barsVisible
                                }
                            },
                            onPageStep = { forward ->
                                if (forward) viewModel.nextPage() else viewModel.prevPage()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        state.isPagedDocument -> PagedReaderView(
                            pageIndex = state.pageIndex,
                            pageCount = state.pageCount,
                            dualPage = settings.dualPage,
                            background = palette.background,
                            loadPage = { page, width, maxHeight ->
                                viewModel.loadPageBitmap(page, width, maxHeight)
                            },
                            onGoToPage = { page -> viewModel.goToPage(page) },
                            onTapCenter = {
                                if (panel != ReaderPanel.NONE) {
                                    closePanel()
                                } else {
                                    barsVisible = !barsVisible
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        chapter != null -> ReflowableReaderView(
                            chapter = chapter,
                            settings = settings,
                            palette = palette,
                            fontScale = density.fontScale,
                            // All callbacks are remembered so that this composable - and with it the
                            // WebView - is skipped when only the progress numbers change.
                            initialOffsetProvider = rememberOffsetProvider,
                            resourceResolver = rememberResolver,
                            onLocation = rememberLocationCallback,
                            onTap = rememberTapCallback,
                            onInternalLink = rememberLinkCallback,
                            onReady = rememberReadyCallback,
                            onReachEnd = rememberReachEndCallback,
                            onReachStart = rememberReachStartCallback,
                            nextChapterTitle = state.chapters
                                .getOrNull(state.chapterIndex + 1)
                                ?.title
                                ?.takeIf { it.isNotBlank() },
                            previousChapterTitle = state.chapters
                                .getOrNull(state.chapterIndex - 1)
                                ?.title
                                ?.takeIf { it.isNotBlank() },
                            onEdgeSwipe = rememberEdgeSwipeCallback,
                            controller = controller,
                            // A panel covers the page: the WebView stops drawing and its timers
                            // pause, so the panel's own animation and scrolling stay smooth.
                            pageHidden = panel != ReaderPanel.NONE || visiblePanel != ReaderPanel.NONE,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                if (state.loading && chapter == null && !state.isPagedDocument) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(palette.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                    }
                }

            }

                BarsOverlay(
                    visible = barsVisible,
                    fromTop = true,
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = state.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (subtitleText.isNotBlank()) {
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                viewModel.flushProgress()
                                onBack()
                            }) {
                                Icon(
                                    LocalAppIcons.current.back,
                                    contentDescription = AppStrings.get(R.string.ui_back_to_library_116),
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { openPanel(ReaderPanel.SEARCH) }) {
                                Icon(LocalAppIcons.current.search, contentDescription = AppStrings.get(R.string.reader_search))
                            }
                            IconButton(onClick = { openPanel(ReaderPanel.BOOKMARKS) }) {
                                Icon(LocalAppIcons.current.bookmarkOutline, contentDescription = AppStrings.get(R.string.ui_bookmark_list_117))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        ),
                    )
                }

                BarsOverlay(
                    visible = barsVisible,
                    fromTop = false,
                ) {
                    ReaderBottomBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        palette = palette,
                        chapterLabel = if (state.isPagedDocument) {
                            AppStrings.get(R.string.ui_page_118, state.pageIndex + 1, state.pageCount.coerceAtLeast(1))
                        } else {
                            state.currentChapterTitle.ifBlank { AppStrings.get(R.string.ui_section_77, state.chapterIndex + 1) }
                        },
                        percentage = state.percentage,
                        seekValue = seekValue,
                        onSeek = { seekValue = it },
                        onSeekFinished = {
                            seekValue?.let { viewModel.seekTo(it) }
                            seekValue = null
                        },
                        bookmarked = state.bookmarks.any { bookmark ->
                            if (state.isPagedDocument) {
                                bookmark.pageIndex == state.pageIndex
                            } else {
                                bookmark.chapterIndex == state.chapterIndex &&
                                    kotlin.math.abs(bookmark.charOffset - state.currentOffset) < 64
                            }
                        },
                        onToc = { openPanel(ReaderPanel.TOC) },
                        onBookmark = { viewModel.toggleBookmark() },
                        onFontSize = { openPanel(ReaderPanel.SETTINGS) },
                        onTheme = { openPanel(ReaderPanel.THEME) },
                        onMore = { openPanel(ReaderPanel.SETTINGS) },
                    )
                }
            }

            // The table of contents is an overlay, never a column of the layout: taking space from
            // the reading area would re-lay out the chapter and make the text jump.
            if (expanded) {
                AnimatedVisibility(
                    visible = panel == ReaderPanel.TOC,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.26f))
                            .clickable { closePanel() },
                    )
                }
                AnimatedVisibility(
                    visible = panel == ReaderPanel.TOC,
                    enter = slideInHorizontally { width -> -width } + fadeIn(),
                    exit = slideOutHorizontally { width -> -width } + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    TocPanel(
                        entries = state.toc,
                        currentChapterIndex = tocCurrentIndex,
                        analysing = state.analysingToc,
                        onSelect = { entry ->
                            viewModel.goToTocEntry(entry)
                            closePanel()
                        },
                        onClose = { closePanel() },
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight(),
                    )
                }
            }

            // Reader panels are drawn inside this window instead of a ModalBottomSheet. That sheet
            // opened its own dialog window for every panel - contents, settings, theme alike - and
            // the window setup blocked the main thread for a few hundred ms on phones (the WebView
            // renderer even had to be unfrozen first), while its capped height left a strip of
            // dimmed backdrop under the sheet that looked broken. This overlay reaches the bottom
            // edge and costs nothing to open.
            if (panelVisible || (visiblePanel != ReaderPanel.NONE && !(expanded && visiblePanel == ReaderPanel.TOC))) {
                ReaderPanelOverlay(progress = { panelProgress.value }, onDismiss = { closePanel() }) {
                    when (visiblePanel) {
                        ReaderPanel.TOC -> TocPanel(
                            entries = state.toc,
                            currentChapterIndex = tocCurrentIndex,
                            analysing = state.analysingToc,
                            onSelect = { entry ->
                                viewModel.goToTocEntry(entry)
                                closePanel()
                            },
                            onClose = { closePanel() },
                        )

                        ReaderPanel.SEARCH -> SearchPanel(
                            query = state.query,
                            results = state.results,
                            searching = state.searching,
                            currentIndex = state.chapterIndex,
                            onQueryChange = { query -> viewModel.search(query) },
                            onSelect = { hit ->
                                viewModel.jumpToSearchHit(hit)
                                closePanel()
                            },
                            onClose = {
                                viewModel.clearSearch()
                                closePanel()
                            },
                        )

                        ReaderPanel.BOOKMARKS -> BookmarksPanel(
                            bookmarks = state.bookmarks,
                            onSelect = { bookmark ->
                                viewModel.jumpToBookmark(bookmark)
                                closePanel()
                            },
                            onDelete = { bookmark -> viewModel.deleteBookmark(bookmark) },
                            onClose = { closePanel() },
                        )

                        ReaderPanel.SETTINGS -> ReaderSettingsPanel(
                            settings = settings,
                            fonts = fonts,
                            onUpdate = { transform -> viewModel.updateSettings(transform) },
                            onSelectFont = { font -> viewModel.selectFont(font) },
                            onImportFont = { fontPicker.launch(arrayOf("font/*", "*/*")) },
                            onClose = { closePanel() },
                        )

                        ReaderPanel.THEME -> ReaderThemePanel(
                            current = settings.theme,
                            onSelect = { theme ->
                                viewModel.updateSettings { it.copy(theme = theme) }
                            },
                            onClose = { closePanel() },
                        )

                        ReaderPanel.NONE -> Unit
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

    state.errorMessage?.let { message ->
        OpenErrorDialog(
            message = message,
            onPickAnotherFile = onBack,
            onClose = onBack,
        )
    }
}

/** Test tag of the reader's bottom sheet, so its geometry can be asserted from a device test. */
internal const val ReaderPanelOverlayTag = "readerPanelSheet"

/**
 * The reader's bottom panel, drawn in the same window as the page.
 *
 * Deliberately not a `ModalBottomSheet`: that opens a separate dialog window, which blocked the
 * main thread for a few hundred milliseconds on phones every time a panel was shown (the WebView's
 * renderer even had to be unfrozen for it), and its capped height left a strip of dimmed backdrop
 * under the sheet that looked broken. This one slides up and stops flush with the bottom edge.
 */
@Composable
internal fun ReaderPanelOverlay(
    progress: () -> Float,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress() }
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .testTag(ReaderPanelOverlayTag)
                .graphicsLayer { translationY = (1f - progress()) * size.height },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun BarsOverlay(
    visible: Boolean,
    fromTop: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (fromTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { height -> if (fromTop) -height else height },
            exit = fadeOut() + slideOutVertically { height -> if (fromTop) -height else height },
        ) {
            content()
        }
    }
}

@Composable
private fun ReaderBottomBar(
    modifier: Modifier = Modifier,
    palette: com.lightreader.ui.theme.ReaderPalette,
    chapterLabel: String,
    percentage: Float,
    seekValue: Float?,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    bookmarked: Boolean,
    onToc: () -> Unit,
    onBookmark: () -> Unit,
    onFontSize: () -> Unit,
    onTheme: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickAction(
                    LocalAppIcons.current.list,
                    AppStrings.get(R.string.reader_toc),
                    onToc,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    if (bookmarked) LocalAppIcons.current.bookmark else LocalAppIcons.current.bookmarkOutline,
                    if (bookmarked) AppStrings.get(R.string.ui_bookmark_added_120) else AppStrings.get(R.string.reader_bookmarks),
                    onBookmark,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    LocalAppIcons.current.fontSize,
                    AppStrings.get(R.string.settings_font_size),
                    onFontSize,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    LocalAppIcons.current.palette,
                    AppStrings.get(R.string.ui_background_82),
                    onTheme,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    LocalAppIcons.current.more,
                    AppStrings.get(R.string.action_more),
                    onMore,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            com.lightreader.ui.components.MinimalSlider(
                value = seekValue ?: percentage.coerceIn(0f, 1f),
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapterLabel,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.percent(percentage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 2.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.height(34.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun OpenErrorDialog(
    message: String,
    onPickAnotherFile: () -> Unit,
    onClose: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(AppStrings.get(R.string.error_open_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(AppStrings.get(R.string.error_reason_title))
                Text(AppStrings.get(R.string.ui_the_file_is_damaged_or_incomplete_123))
                Text(AppStrings.get(R.string.ui_the_format_is_unsupported_124))
                Text(AppStrings.get(R.string.ui_file_permission_is_no_longer_valid_125))
                Text(AppStrings.get(R.string.ui_the_file_was_moved_or_deleted_126))
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) AppStrings.get(R.string.ui_hide_technical_details_127) else AppStrings.get(R.string.error_details))
                }
                if (showDetails) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onPickAnotherFile) { Text(AppStrings.get(R.string.error_pick_again)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(AppStrings.get(R.string.action_close)) } },
    )
}

private suspend fun SnackbarHostState.showToast(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
