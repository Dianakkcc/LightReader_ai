package com.lightreader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.core.model.Book
import com.lightreader.core.util.ImageLoader

/**
 * Cover art.
 *
 * No drop shadow on purpose: shadows on every tile of a scrolling grid are one of the most
 * reliable ways to lose frames on a mid range phone. A hairline border plus a Morandi gradient
 * placeholder gives the same "printed object" feel for free.
 */
@Composable
fun BookCover(
    book: Book,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
) {
    val coverUri = book.coverPath?.let { "file://$it" }
    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    // Bucket sizes to reuse thumbnails without decoding a separate bitmap for each pixel.
    val width = ((coverSize.width + 63) / 64) * 64
    val height = ((coverSize.height + 63) / 64) * 64
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, coverUri, width, height) {
        value = null
        if (width > 0 && height > 0) {
            value = coverUri?.let { uri -> imageLoader.load(uri, width, height, lowMemory = true) }
        }
    }
    val brush = remember(book.title) { placeholderBrush(book) }
    Box(
        modifier = modifier
            .onSizeChanged { coverSize = it }
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(brush)
            .border(
                width = 0.6.dp,
                color = Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(cornerRadius.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = book.title.take(20),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.format.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun placeholderBrush(book: Book): Brush {
    // Morandi gradients: dusty, low saturation, never competing with the text.
    val palettes = listOf(
        Color(0xFF9AA8B2) to Color(0xFF7C8A94),
        Color(0xFFA3AFA0) to Color(0xFF86927F),
        Color(0xFFB0A6AE) to Color(0xFF8E858D),
        Color(0xFFBFB0A0) to Color(0xFF97897A),
        Color(0xFF9BAEB0) to Color(0xFF7C8E90),
        Color(0xFFB3A79C) to Color(0xFF8F8478),
    )
    val index = Math.floorMod(book.title.hashCode(), palettes.size)
    val (start, end) = palettes[index]
    return Brush.linearGradient(
        colors = listOf(start, end),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}
