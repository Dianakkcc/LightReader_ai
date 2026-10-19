package com.lightreader.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Fixed layout reading surface (PDF, images, comics).
 *
 * Pages keep their original geometry. Turning works with a tap on either side, with a horizontal
 * swipe that follows the finger, and from the progress bar. Zooming is a pinch; while a page is
 * zoomed, dragging pans instead of turning, and a double tap returns to the fitted view.
 */
@Composable
fun PagedReaderView(
    pageIndex: Int,
    pageCount: Int,
    dualPage: Boolean,
    background: Color,
    loadPage: suspend (pageIndex: Int, targetWidth: Int, maxHeight: Int) -> Bitmap?,
    onGoToPage: (Int) -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.background(background)) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val spread = if (dualPage && containerWidthPx >= 700f) 2 else 1

        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var pageDrag by remember { mutableFloatStateOf(0f) }
        var lastDirection by remember { mutableStateOf(1) }

        val currentOnGoToPage by rememberUpdatedState(onGoToPage)
        val currentOnTapCenter by rememberUpdatedState(onTapCenter)

        LaunchedEffect(pageIndex, dualPage, spread) {
            zoom = 1f
            pan = Offset.Zero
            pageDrag = 0f
        }

        // Resolve a horizontal drag into a page turn once the finger settles.
        LaunchedEffect(pageDrag) {
            if (pageDrag == 0f) return@LaunchedEffect
            delay(150)
            val threshold = containerWidthPx * 0.12f
            when {
                pageDrag <= -threshold -> {
                    lastDirection = 1
                    currentOnGoToPage((pageIndex + spread).coerceAtMost(pageCount - 1))
                }

                pageDrag >= threshold -> {
                    lastDirection = -1
                    currentOnGoToPage((pageIndex - spread).coerceAtLeast(0))
                }
            }
            pageDrag = 0f
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            if (zoom <= 1.01f && abs(panChange.x) > abs(panChange.y) && zoomChange == 1f) {
                pageDrag += panChange.x
            } else {
                zoom = (zoom * zoomChange).coerceIn(1f, 6f)
                pan = if (zoom <= 1.01f) Offset.Zero else pan + panChange
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(transformState)
                .pointerInput(pageIndex, pageCount, spread, zoom) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoom > 1.01f) {
                                zoom = 1f
                                pan = Offset.Zero
                            } else {
                                zoom = 2.2f
                            }
                        },
                        onTap = { position ->
                            val fraction = position.x / size.width.coerceAtLeast(1)
                            when {
                                zoom > 1.01f -> currentOnTapCenter()
                                fraction < 0.3f -> {
                                    lastDirection = -1
                                    currentOnGoToPage((pageIndex - spread).coerceAtLeast(0))
                                }

                                fraction > 0.7f -> {
                                    lastDirection = 1
                                    currentOnGoToPage(
                                        (pageIndex + spread).coerceAtMost(pageCount - 1),
                                    )
                                }

                                else -> currentOnTapCenter()
                            }
                        },
                    )
                },
        ) {
            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    val forward = targetState >= initialState || lastDirection >= 0
                    val enter = slideInHorizontally(
                        animationSpec = tween(260),
                    ) { width -> if (forward) width else -width } + fadeIn(tween(180))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(260),
                    ) { width -> if (forward) -width else width } + fadeOut(tween(180))
                    enter togetherWith exit
                },
                label = "pageTurn",
                modifier = Modifier.fillMaxSize(),
            ) { currentPage ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (spread == 2) 4.dp else 0.dp)
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = pan.x + pageDrag,
                            translationY = pan.y,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (slot in 0 until spread) {
                        val index = currentPage + slot
                        if (index >= pageCount) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                            )
                            continue
                        }
                        val slotWidth = containerWidthPx / spread
                        val renderWidth = (slotWidth * zoom.coerceIn(1f, 3f))
                            .toInt()
                            .coerceAtLeast(1)
                        val bitmap by produceState<Bitmap?>(
                            initialValue = null,
                            index,
                            renderWidth,
                        ) {
                            value = loadPage(index, renderWidth, containerHeightPx.toInt())
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val image = bitmap
                            if (image == null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Image(
                                    bitmap = image.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
