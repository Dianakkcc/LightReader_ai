package com.lightreader.core.data

import android.content.Context
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.util.Logs
import java.io.File
import java.security.MessageDigest

/**
 * App private storage layout.
 *
 * Books are opened straight from their content URI whenever possible. Only zip based formats
 * (EPUB, DOCX) need random access to a real file, so they get a cached working copy that can be
 * evicted at any time - the user's original file is never moved, modified or required.
 */
class BookWorkspace(context: Context) {

    private val app: Context = context.applicationContext

    val cacheRoot: File get() = File(app.cacheDir, "books").ensure()
    val coversDir: File get() = File(app.filesDir, "covers").ensure()
    val fontsDir: File get() = File(app.filesDir, "fonts").ensure()
    val exportDir: File get() = File(app.filesDir, "export").ensure()
    val pdfCacheDir: File get() = File(app.cacheDir, "pdf").ensure()

    fun keyFor(uri: String): String = sha1(uri).take(20)

    fun bookDir(key: String): File = File(cacheRoot, key).ensure()

    fun coverFile(bookId: Long): File = File(coversDir, "$bookId.jpg")

    fun fontFile(fontId: Long, extension: String): File = File(fontsDir, "$fontId.$extension")

    /**
     * Returns a local file with the full content of [source], copying it once when needed.
     * The copy is only created for formats that need random access.
     */
    suspend fun materialise(
        source: DocumentSource,
        key: String,
        extension: String,
    ): File {
        val target = File(bookDir(key), "source.$extension")
        val expected = source.sizeBytes
        if (target.isFile && target.length() > 0 && (expected <= 0 || target.length() == expected)) {
            return target
        }
        val temporary = File(bookDir(key), "source.$extension.part")
        if (temporary.exists()) temporary.delete()
        source.openInput(app).use { input ->
            temporary.outputStream().use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE * 4)
            }
        }
        if (target.exists()) target.delete()
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        Logs.d("已缓存 ${source.displayName} → ${target.name} (${target.length()}B)")
        return target
    }

    fun dropBookCache(key: String) {
        val dir = File(cacheRoot, key)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun clearCaches() {
        listOf(cacheRoot, pdfCacheDir).forEach { dir ->
            runCatching { dir.deleteRecursively() }
        }
        cacheRoot.ensure()
        pdfCacheDir.ensure()
    }

    fun cacheSizeBytes(): Long {
        fun sizeOf(dir: File): Long =
            if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return sizeOf(cacheRoot) + sizeOf(pdfCacheDir)
    }

    private fun File.ensure(): File = apply { if (!exists()) mkdirs() }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
