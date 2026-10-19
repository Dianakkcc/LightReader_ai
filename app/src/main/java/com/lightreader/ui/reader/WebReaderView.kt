package com.lightreader.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ReaderResource
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.util.Logs
import com.lightreader.ui.theme.ReaderPalette
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Handle used by the host screen to talk to the reading surface. */
class WebReaderController {
    private var webView: WebView? = null
    private var pendingHtml: Pair<String, String>? = null

    /** Called by the surface once the WebView exists; replays a load that happened too early. */
    internal fun attach(view: WebView) {
        if (webView === view) return
        webView = view
        pendingHtml?.let { (html, baseUrl) ->
            pendingHtml = null
            view.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
        }
    }

    fun load(html: String, baseUrl: String) {
        val view = webView
        if (view == null) {
            pendingHtml = html to baseUrl
        } else {
            view.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
        }
    }

    fun evaluate(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    fun goToOffset(offset: Int) = evaluate("window.LrReader && LrReader.goToOffset(${offset.coerceAtLeast(0)});")

    fun nextPage() = evaluate("window.LrReader && LrReader.nextPage();")

    fun prevPage() = evaluate("window.LrReader && LrReader.prevPage();")

    fun scrollToId(id: String) =
        evaluate("window.LrReader && LrReader.scrollToId(${quote(id)});")

    fun highlight(query: String) =
        evaluate("window.LrReader && LrReader.highlight(${quote(query)});")

    fun requestLocation() = evaluate("window.LrReader && LrReader.report();")

    /** Re-measures the layout after the reading area was resized (toolbars, rotation). */
    fun refreshLayout() = evaluate("window.LrReader && LrReader.refresh();")

    fun reportLocation() = evaluate("window.LrReader && LrReader.report();")

    /**
     * Asks the page what it currently is, through the JavaScript evaluation channel instead of the
     * bridge. When a reading surface looks alive but never reports anything, this is what tells
     * apart "the script never ran" from "the script ran and could not talk to the host".
     */
    fun probe(tag: String) {
        if (!com.lightreader.BuildConfig.DEBUG) return
        val view = webView ?: return
        // Where the surface actually sits on the screen: a reading area that runs under the
        // navigation bar hides everything the page pins to its bottom edge.
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val windowHeight = view.resources.displayMetrics.heightPixels
        Logs.d(
            // The window itself starts below the status bar on this device (the system insets it),
            // so top + height should equal the window height exactly - that is the check.
            "阅读区几何：top=${location[1]} height=${view.height} 窗口高=$windowHeight " +
                "底部余量=${windowHeight - location[1] - view.height}",
        )
        val script = "(function(){try{return JSON.stringify({tag:'" + tag + "'," +
            "bridge:typeof window.LrBridge,bridgeMetrics:!!(window.LrBridge&&window.LrBridge.onMetrics)," +
            "engine:!!window.LrReader,state:document.readyState," +
            "scrolling:document.scrollingElement?document.scrollingElement.scrollTop:null," +
            "diag:(window.__lrDiag||[]).join(' | ')," +
            "handshake:(function(){try{return String(window.LrBridge.ping('probe'));}" +
            "catch(e){return 'failed: '+e;}})()," +
            "hint:(function(){var h=document.getElementById('lr-hint');if(!h)return null;" +
            "var r=h.getBoundingClientRect();var cs=getComputedStyle(h);" +
            "return {text:h.textContent,inline:h.style.opacity,opacity:cs.opacity," +
            "top:Math.round(r.top),left:Math.round(r.left),w:Math.round(r.width),h:Math.round(r.height)};})()," +
            "metrics:(window.LrReader&&LrReader.metrics)?LrReader.metrics():null," +
            "view:(function(){var v=document.getElementById('lr-view');if(!v)return null;" +
            "var r=v.getBoundingClientRect();return {w:Math.round(r.width),h:Math.round(r.height)};})()});" +
            "}catch(e){return 'probe failed: '+e;}})()"
        view.evaluateJavascript(script) { result -> Logs.d("引擎探针（$tag）：$result") }
    }

    /** Labels the "keep pulling to continue" hint with the next chapter title. */
    fun setNextChapter(title: String?) = evaluate(
        "window.LrReader && LrReader.setNextChapter(${quote(title ?: "")});",
    )

    /** Labels the banner that drops in when the reader pulls down at the top of a chapter. */
    fun setPreviousChapter(title: String?) = evaluate(
        "window.LrReader && LrReader.setPreviousChapter(${quote(title ?: "")});",
    )

    /**
     * Replaces the stylesheet of the page that is already loaded (font size, spacing, colours,
     * margins). The chapter HTML, its images and the reading position all stay in place.
     */
    fun applyStyle(css: String, horizontalPaddingPx: Int, verticalPaddingPx: Int) {
        val payload = org.json.JSONObject.quote(css)
        evaluate(
            "window.LrReader && LrReader.setStyle($payload, $horizontalPaddingPx, $verticalPaddingPx);",
        )
    }

    /** Aborts an in-flight gesture, used when the host takes the gesture over. */
    fun cancelGesture() = evaluate("window.LrReader && LrReader.cancelGesture();")

    fun destroy() {
        pendingHtml = null
        webView?.let { view ->
            runCatching { view.stopLoading() }
            runCatching { view.destroy() }
        }
        webView = null
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}

/**
 * The reflowable reading surface: a WebView that owns text layout, CSS, images, text selection
 * and the position bridge. Everything it fetches is served from the book itself - the app has
 * no network permission at all.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReflowableReaderView(
    chapter: ChapterContent,
    settings: ReaderSettings,
    palette: ReaderPalette,
    fontScale: Float,
    initialOffsetProvider: () -> Int,
    resourceResolver: (String) -> ReaderResource?,
    onLocation: (chapterIndex: Int, charOffset: Int, fraction: Float, pages: Int, page: Int) -> Unit,
    onTap: (x: Float, y: Float, width: Int, height: Int) -> Unit,
    onInternalLink: (String) -> Unit,
    onReady: (pages: Int) -> Unit,
    onReachEnd: () -> Unit,
    onReachStart: () -> Unit,
    nextChapterTitle: String?,
    previousChapterTitle: String?,
    onEdgeSwipe: () -> Unit,
    controller: WebReaderController,
    /**
     * True while a reader panel covers the page. The surface then stops drawing and its timers are
     * paused: leaving a live WebView under the panel meant every panel frame also composited (and
     * the engine kept producing) frames nobody could see, which is what made the panel's animation
     * and scrolling drop frames on phones. Nothing is reloaded - the page is resumed in place.
     */
    pageHidden: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // The margin setting is tuned for a tablet sized reading column; on a phone the same value
    // eats a fifth of the line, so it scales with the screen width and keeps a sane floor so text
    // never touches the bezel.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val marginScale = (screenWidthDp / 800f).coerceIn(0.5f, 1f)
    val effectiveMarginDp = (settings.marginDp * marginScale).coerceAtLeast(10f)
    val horizontalPadding = with(density) { effectiveMarginDp.dp.toPx().toInt() }
    val verticalPadding = with(density) { (settings.marginDp / 2 + 16).dp.toPx().toInt() }

    // CSS pixels inside a WebView are density independent, so the sp value is used as is; the
    // system font scale is applied explicitly so accessibility settings keep working.
    val effectiveFontScale = fontScale

    val currentResolver = rememberUpdatedState(resourceResolver)
    val currentLocation = rememberUpdatedState(onLocation)
    val currentTap = rememberUpdatedState(onTap)
    val currentLink = rememberUpdatedState(onInternalLink)
    val currentReady = rememberUpdatedState(onReady)
    val currentReachEnd = rememberUpdatedState(onReachEnd)
    val currentReachStart = rememberUpdatedState(onReachStart)
    val currentEdgeSwipe = rememberUpdatedState(onEdgeSwipe)

    val bridge: ReaderBridge = remember {
        ReaderBridge(
            locationCallback = { chapterIndex, offset, fraction, pages, page ->
                currentLocation.value(chapterIndex, offset, fraction, pages, page)
            },
            tapCallback = { x, y, width, height -> currentTap.value(x, y, width, height) },
            readyCallback = { pages -> currentReady.value(pages) },
            reachEndCallback = { currentReachEnd.value() },
            reachStartCallback = { currentReachStart.value() },
            metricsCallback = { json -> Logs.d("阅读引擎几何：$json") },
        )
    }

    var lastBackgroundColor by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var hiddenApplied by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.onSizeChanged { size ->
            Logs.d("阅读区（Compose）：${size.width}x${size.height}")
        },
        factory = { ctx ->
            var touchDownX = 0f
            var touchDownY = 0f
            var touchDownAt = 0L
            var touchMoved = false
            var lastLinkAt = 0L
            var edgeSwipeFired = false
            val edgeZone = 26f * ctx.resources.displayMetrics.density
            val edgeDistance = 56f * ctx.resources.displayMetrics.density
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                installReaderBridge(bridge)
                this.settings.apply {
                    setJavaScriptEnabled(true)
                    setDomStorageEnabled(false)
                    setAllowFileAccess(false)
                    setAllowContentAccess(false)
                    setLoadsImagesAutomatically(true)
                    // Pictures are served by this app through shouldInterceptRequest, but to Blink
                    // they still look like network images: with network image loading disabled the
                    // renderer never issues the request, so every illustrated page - and every
                    // cover page - came out blank. Interception is what keeps the reader offline,
                    // and the app has no INTERNET permission at all.
                    setBlockNetworkImage(false)
                    setBlockNetworkLoads(true)
                    setCacheMode(WebSettings.LOAD_NO_CACHE)
                    setTextZoom(100)
                    setUseWideViewPort(false)
                    setLoadWithOverviewMode(false)
                    setMediaPlaybackRequiresUserGesture(true)
                    setSupportZoom(false)
                    setBuiltInZoomControls(false)
                    setDefaultTextEncodingName("utf-8")
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        if (!url.startsWith(ReaderAssets.BASE_URL)) return null
                        val path = url.removePrefix(ReaderAssets.BASE_URL)
                        if (path.startsWith("brand/")) {
                            // Decorative artwork that ships inside the APK (Diana / 嘉然 theme).
                            val name = path.removePrefix("brand/").substringBefore('?')
                            val bytes = runCatching {
                                view.context.assets.open("brand/$name").use { it.readBytes() }
                            }.getOrNull()
                            if (bytes == null) {
                                Logs.w("品牌图缺失：$name")
                                return null
                            }
                            return WebResourceResponse(
                                if (name.endsWith(".png")) "image/png" else "image/jpeg",
                                null,
                                ByteArrayInputStream(bytes),
                            )
                        }
                        // This callback already runs on WebView's resource worker. Resolve local
                        // bytes directly rather than blocking a coroutine dispatch for each image.
                        val resource = runCatching { currentResolver.value(path) }.getOrNull() ?: run {
                            Logs.w("资源缺失：$path")
                            return null
                        }
                        return resource.toResponse()
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith(ReaderAssets.BASE_URL)) {
                            lastLinkAt = System.currentTimeMillis()
                            currentLink.value(url)
                        }
                        // External links never navigate inside the reader.
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        Logs.d("章节渲染完成：$url")
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        Logs.w("资源加载失败：${request.url} ${error.errorCode} ${error.description}")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Logs.d("页面控制台：${message.message()} @${message.lineNumber()}")
                        return true
                    }
                }
                // Taps are detected on the Android side instead of through the JavaScript bridge:
                // the touch listener never consumes the event, so scrolling, link taps and text
                // selection keep working exactly as the WebView intends.
                val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
                setOnTouchListener { touched, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            touchDownX = event.x
                            touchDownY = event.y
                            touchDownAt = System.currentTimeMillis()
                            touchMoved = false
                            edgeSwipeFired = false
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = kotlin.math.abs(event.x - touchDownX)
                            val dy = kotlin.math.abs(event.y - touchDownY)
                            if (dx > touchSlop || dy > touchSlop) touchMoved = true
                            // Drawer gesture: start at the very left edge and pull right,
                            // clearly horizontal and far enough to never fire by accident.
                            if (!edgeSwipeFired && touchDownX <= edgeZone &&
                                event.x - touchDownX >= edgeDistance &&
                                dx > kotlin.math.abs(event.y - touchDownY) * 1.6f
                            ) {
                                edgeSwipeFired = true
                                controller.cancelGesture()
                                currentEdgeSwipe.value()
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                            val elapsed = System.currentTimeMillis() - touchDownAt
                            if (!touchMoved && elapsed < 320 &&
                                System.currentTimeMillis() - lastLinkAt > 400
                            ) {
                                currentTap.value(
                                    event.x,
                                    event.y,
                                    touched.width,
                                    touched.height,
                                )
                            }
                        }
                    }
                    false
                }
                controller.attach(this)
            }
        },
        update = { web ->
            controller.attach(web)
            val color = palette.background.toArgb()
            // Repainting the WebView background on every recomposition is surprisingly expensive
            // for a full screen surface, so it only happens when the colour really changed.
            if (lastBackgroundColor != color) {
                lastBackgroundColor = color
                web.setBackgroundColor(color)
            }
            if (hiddenApplied != pageHidden) {
                hiddenApplied = pageHidden
                if (pageHidden) {
                    web.onPause()
                    web.visibility = View.INVISIBLE
                } else {
                    web.visibility = View.VISIBLE
                    web.onResume()
                }
            }
        },
    )

    // Only a different chapter (or a different layout engine) needs the page rebuilt from scratch.
    // Font size, spacing, margins and colours are a stylesheet change: they are pushed into the page
    // that is already on screen, so a slider drag no longer re-parses the chapter and re-decodes its
    // pictures - that was what made the settings sheet stutter on phones.
    val pageKey = remember(chapter.index, settings.readingMode, settings.dualPage) {
        arrayOf<Any>(chapter.index, settings.readingMode, settings.dualPage).contentHashCode()
    }
    val styleKey = remember(
        settings.fontSizeSp,
        settings.lineHeightMultiplier,
        settings.letterSpacingEm,
        settings.paragraphSpacingEm,
        settings.marginDp,
        settings.alignment,
        settings.indentMode,
        settings.theme,
        settings.fontId,
        settings.useSerif,
        palette.background.value,
        effectiveFontScale,
        chapter.css,
    ) {
        arrayOf(
            settings.fontSizeSp,
            settings.lineHeightMultiplier,
            settings.letterSpacingEm,
            settings.paragraphSpacingEm,
            settings.marginDp,
            settings.alignment,
            settings.indentMode,
            settings.theme,
            settings.fontId,
            settings.useSerif,
            palette.background.value,
            effectiveFontScale,
            chapter.css,
        ).contentHashCode()
    }

    var renderedPageKey by remember { mutableIntStateOf(Int.MIN_VALUE) }

    LaunchedEffect(pageKey) {
        // The offset is consumed on the main thread (it is ViewModel state), the document itself is
        // assembled off it: building ~40 KB of HTML inline blocked the frame that draws the new
        // chapter, which is what the reader felt as a small freeze right after the pull gesture.
        val initialOffset = initialOffsetProvider()
        val html = withContext(kotlinx.coroutines.Dispatchers.Default) {
            ChapterHtml.build(
                content = chapter,
                settings = settings,
                palette = palette,
                fontScale = effectiveFontScale,
                verticalPaddingPx = verticalPadding,
                horizontalPaddingPx = horizontalPadding,
                initialOffset = initialOffset,
                nextChapterTitle = nextChapterTitle,
                previousChapterTitle = previousChapterTitle,
            )
        }
        renderedPageKey = pageKey
        controller.load(html, ReaderAssets.BASE_URL)
    }

    LaunchedEffect(styleKey) {
        if (renderedPageKey != pageKey) return@LaunchedEffect
        // Coalesce a slider drag: only the value the finger settles on is laid out.
        kotlinx.coroutines.delay(90)
        val css = ReaderCss.build(
            settings = settings,
            palette = palette,
            paged = settings.readingMode == com.lightreader.core.model.ReadingMode.PAGED,
            fontScale = effectiveFontScale,
            bookCss = chapter.css,
            horizontalPaddingPx = horizontalPadding,
        )
        controller.applyStyle(css, horizontalPadding, verticalPadding)
        Logs.d("阅读样式就地更新：${settings.fontSizeSp}sp / 行距 ${settings.lineHeightMultiplier}")
    }

    LaunchedEffect(pageKey) {
        // A short while after every render, ask the page to describe itself: if the engine failed
        // to start, this is the only place that says so.
        kotlinx.coroutines.delay(2_400)
        controller.probe("render")
    }

    LaunchedEffect(nextChapterTitle) {
        controller.setNextChapter(nextChapterTitle)
    }

    LaunchedEffect(previousChapterTitle) {
        controller.setPreviousChapter(previousChapterTitle)
    }

    // Keep the surface alive with the composition but release the WebView when leaving.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { controller.destroy() }
    }
}

private fun ReaderResource.toResponse(): WebResourceResponse {
    val encoding = if (mimeType.startsWith("text/") || mimeType.contains("xml")) "UTF-8" else null
    return WebResourceResponse(mimeType, encoding, ByteArrayInputStream(bytes)).apply {
        responseHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "no-cache",
        )
    }
}

/** JavaScript → Kotlin callbacks, always re-posted onto the main thread. */
/**
 * JavaScript to Kotlin callbacks.
 *
 * The class and its methods are public on purpose: the WebView bridge invokes them through
 * reflection, and a non-public class makes those calls fail silently on some Android versions.
 */
class ReaderBridge(
    private val locationCallback: (Int, Int, Float, Int, Int) -> Unit,
    private val tapCallback: (Float, Float, Int, Int) -> Unit,
    private val readyCallback: (Int) -> Unit,
    private val reachEndCallback: () -> Unit,
    private val reachStartCallback: () -> Unit,
    private val metricsCallback: (String) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastEndLogAt = 0L

    @JavascriptInterface
    fun onLocation(chapter: Int, offset: Int, fraction: Double, pages: Int, page: Int) {
        // The callbacks must not share the name of the method that invokes them: a parameter called
        // `onLocation` is shadowed by this member function inside the body, so `onLocation(...)`
        // called *itself* - a post storm that pinned the main thread at 100% CPU and swallowed
        // every position, ready and metrics event on the way.
        handler.post { locationCallback(chapter, offset, fraction.toFloat(), pages, page) }
    }

    @JavascriptInterface
    fun onTap(x: Float, y: Float, width: Int, height: Int) {
        handler.post { tapCallback(x, y, width, height) }
    }

    @JavascriptInterface
    fun onReady(pages: Int, page: Int) {
        handler.post { readyCallback(pages) }
    }

    @JavascriptInterface
    fun onReachEnd(state: String) {
        // Rate limited: this is diagnostics, and a misbehaving page must not flood the log.
        val now = System.currentTimeMillis()
        if (now - lastEndLogAt > 1000) {
            lastEndLogAt = now
            Logs.d("阅读引擎：收到章末事件 $state")
        }
        handler.post { reachEndCallback() }
    }

    @JavascriptInterface
    fun onReachStart(state: String) {
        val now = System.currentTimeMillis()
        if (now - lastEndLogAt > 1000) {
            lastEndLogAt = now
            Logs.d("阅读引擎：收到章首事件 $state")
        }
        handler.post { reachStartCallback() }
    }

    @JavascriptInterface
    fun onMetrics(json: String) {
        handler.post { metricsCallback(json) }
    }

    /** Synchronous round trip used by [WebReaderController.probe] to test the bridge itself. */
    @JavascriptInterface
    fun ping(tag: String): String {
        Logs.d("引擎握手：$tag")
        return "pong"
    }
}

/** Explicit bridge type also lets lint inspect every exposed method annotation. */
private fun WebView.installReaderBridge(bridge: ReaderBridge) {
    addJavascriptInterface(bridge, "LrBridge")
}
