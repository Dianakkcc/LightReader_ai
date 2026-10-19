package com.lightreader.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Small bitmap loader with an LRU cache measured in bytes.
 *
 * Covers, comic pages and PDF thumbnails all go through here, so the app never decodes the same
 * image twice and never keeps a full resolution scan in memory.
 */
class ImageLoader(private val context: Context) {
    private val decodeSlots = Semaphore(2)

    private val maxBytes = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(96L * 1024 * 1024)

    private val cache = object : LruCache<String, Bitmap>(maxBytes.toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun load(
        uri: String,
        targetWidth: Int,
        targetHeight: Int,
        lowMemory: Boolean = false,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "$uri@$targetWidth x $targetHeight" + if (lowMemory) "#565" else ""
            cache.get(key)?.let { return@withContext it }
            decodeSlots.withPermit {
                cache.get(key)?.let { return@withPermit it }
                val bitmap = decode(uri, targetWidth, targetHeight, lowMemory) ?: return@withPermit null
                bitmap.prepareToDraw()
                cache.put(key, bitmap)
                bitmap
            }
        }

    suspend fun decodeBytes(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }.getOrNull()
        }

    private fun decode(
        uri: String,
        targetWidth: Int,
        targetHeight: Int,
        lowMemory: Boolean,
    ): Bitmap? = runCatching {
        val parsed = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            // Shelf covers are opaque: 16 bit colour halves both the memory and the GPU upload.
            inPreferredConfig = if (lowMemory) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(parsed)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()

    /** Downscales an arbitrary bitmap so the cache stays bounded. */
    suspend fun scale(bitmap: Bitmap, maxWidth: Int): Bitmap = withContext(Dispatchers.Default) {
        if (bitmap.width <= maxWidth) return@withContext bitmap
        val ratio = maxWidth.toFloat() / bitmap.width.toFloat()
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
    }

    fun clear() {
        cache.evictAll()
    }

    fun cacheSizeBytes(): Long {
        val snapshot = cache.snapshot()
        return snapshot.keys.sumOf { key -> (snapshot[key]?.allocationByteCount ?: 0).toLong() }
    }

    private fun sampleSize(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int = imageSampleSize(width, height, targetWidth, targetHeight)

    companion object {
        fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
