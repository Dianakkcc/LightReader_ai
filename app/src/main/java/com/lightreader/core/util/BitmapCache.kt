package com.lightreader.core.util

import android.graphics.Bitmap
import android.util.LruCache

/** Byte sized LRU cache for rendered pages (PDF, comics). */
class BitmapCache(maxBytes: Long) {

    private val cache = object : LruCache<String, Bitmap>(maxBytes.toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() = cache.evictAll()

    fun sizeBytes(): Long {
        val snapshot = cache.snapshot()
        return snapshot.keys.sumOf { key -> (snapshot[key]?.allocationByteCount ?: 0).toLong() }
    }
}
