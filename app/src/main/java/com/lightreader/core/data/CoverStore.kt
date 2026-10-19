package com.lightreader.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lightreader.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Cover thumbnails live in app private storage: a few dozen KB each, so the shelf can scroll
 * through hundreds of books without decoding original files.
 */
class CoverStore(private val workspace: BookWorkspace) {

    private val maxWidth = 480

    suspend fun write(bookId: Long, bytes: ByteArray?): String? = withContext(Dispatchers.IO) {
        if (bytes == null || bytes.isEmpty()) return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return@runCatching null
            val target = workspace.coverFile(bookId)
            FileOutputStream(target).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
            }
            bitmap.recycle()
            target.absolutePath
        }.onFailure { Logs.w("写入封面失败：${it.message}", it) }.getOrNull()
    }

    fun delete(bookId: Long) {
        val file: File = workspace.coverFile(bookId)
        if (file.exists()) file.delete()
    }

    private fun sampleSize(width: Int, height: Int, targetWidth: Int): Int {
        var sample = 1
        var current = width
        while (current / 2 >= targetWidth) {
            current /= 2
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
