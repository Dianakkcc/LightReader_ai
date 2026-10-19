package com.lightreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A deliberately plain progress control: a 2dp track, a soft round thumb and no Material chrome.
 * Used for the reading progress, where the book should stay the loudest thing on screen.
 */
@Composable
fun MinimalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentValue by rememberUpdatedState(value.coerceIn(0f, 1f))
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val fillColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    var dragging by remember { mutableStateOf(false) }

    fun update(fraction: Float) {
        currentOnChange(fraction.coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    update(offset.x / size.width.coerceAtLeast(1))
                    currentOnFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        currentOnFinished()
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    update(change.position.x / size.width.coerceAtLeast(1))
                }
            },
    ) {
        val centerY = size.height / 2f
        val trackHeight = 2.dp.toPx()
        val radius = (size.height / 2f).coerceAtMost(8.dp.toPx())
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight),
        )
        val filled = size.width * currentValue
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(filled, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight),
        )
        val thumbRadius = if (dragging) radius else radius * 0.72f
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = thumbRadius + 1.5.dp.toPx(),
            center = Offset(filled.coerceIn(thumbRadius, size.width - thumbRadius), centerY),
        )
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(filled.coerceIn(thumbRadius, size.width - thumbRadius), centerY),
        )
    }
}
