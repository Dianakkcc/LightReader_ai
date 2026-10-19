package com.lightreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.lightreader.core.util.Logs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/**
 * First page of the chapter that [pageIndex] belongs to.
 *
 * A comic's chapters are page ranges inside one continuous flow, so "which chapter am I in" is not
 * a stored index but a question about the current page. The table of contents highlights this page
 * and the top bar names that chapter.
 */
internal fun comicChapterStartFor(pageIndex: Int, chapterAtPage: Map<Int, String>): Int =
    chapterAtPage.keys.filter { it <= pageIndex }.maxOrNull() ?: 0

/**
 * Continuous ("webtoon") comic reading: pages follow one another vertically in a single scroll
 * surface, so a chapter never interrupts the flow. Chapters are still visible - a small title is
 * drawn above the first page of each chapter - and used by the table of contents.
 *
 * Pages are decoded windowed: only the pages near the viewport are alive, and bitmaps live in the
 * shared page cache, so a 400 page archive does not grow past a few screens of memory.
 */
@Composable
fun ComicFlowView(
    pageCount: Int,
    startPage: Int,
    chapterTitleAt: (Int) -> String?,
    background: Color,
    loadPage: suspend (page: Int, width: Int, maxHeight: Int) -> Bitmap?,
    pageAspect: (page: Int) -> Float,
    onVisiblePage: (Int) -> Unit,
    onTapCenter: () -> Unit,
    onPageStep: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = startPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
    )

    // The page the flow reported last: what the reader actually scrolled to.
    var reportedPage by remember { mutableIntStateOf(startPage) }
    val currentOnVisiblePage by rememberUpdatedState(onVisiblePage)
    val currentOnTapCenter by rememberUpdatedState(onTapCenter)
    val currentOnPageStep by rememberUpdatedState(onPageStep)

    // Progress follows the first page of the viewport.
    LaunchedEffect(listState, pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val safe = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                reportedPage = safe
                currentOnVisiblePage(safe)
            }
    }

    // An external jump (table of contents, progress bar, restored position) arrives as a new
    // [startPage]. A LazyColumn only takes its start index on first composition, so without this
    // the page counter changed but the strip stayed where it was - and the next scroll overwrote
    // the request again. Scrolling by hand reports its own page first, so this never fights the
    // finger during flings.
    LaunchedEffect(startPage, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        val target = startPage.coerceIn(0, pageCount - 1)
        if (target != reportedPage) {
            listState.scrollToItem(target)
            reportedPage = target
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pageWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> currentOnPageStep(false)
                            offset.x > third * 2 -> currentOnPageStep(true)
                            else -> currentOnTapCenter()
                        }
                    }
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(pageCount) { page ->
                    chapterTitleAt(page)?.let { title ->
                        ChapterSeparator(title)
                    }
                    ComicPage(
                        page = page,
                        widthPx = pageWidthPx.toInt(),
                        pageAspect = pageAspect,
                        loadPage = loadPage,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterSeparator(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComicPage(
    page: Int,
    widthPx: Int,
    pageAspect: (Int) -> Float,
    loadPage: suspend (page: Int, width: Int, maxHeight: Int) -> Bitmap?,
) {
    var aspect by remember(page) { androidx.compose.runtime.mutableFloatStateOf(0.707f) }
    val bitmap by produceState<Bitmap?>(initialValue = null, page, widthPx) {
        value = null
        try {
            // Header reads / PDF renderer locks must never run during UI composition.
            aspect = withContext(Dispatchers.IO) { pageAspect(page) }
            value = loadPage(page, widthPx, 0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Logs.w("漫画页解码失败：$page ${failure.message}")
        }
    }
    // Once the page is decoded its own shape wins: documents that cannot report a ratio up front
    // (a PDF without a page box, a set of images) are laid out correctly from that moment on.
    val decoded = bitmap
    val ratio = decoded?.takeIf { it.height > 0 }?.let { it.width.toFloat() / it.height.toFloat() }
        ?: aspect
    val safeAspect = ratio.coerceIn(0.2f, 4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(androidx.compose.ui.platform.LocalDensity.current) {
                (widthPx / safeAspect).toInt().toDp()
            }),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            )
        }
    }
}
