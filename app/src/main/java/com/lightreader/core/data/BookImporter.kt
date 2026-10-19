package com.lightreader.core.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lightreader.core.data.db.BookDao
import com.lightreader.core.data.db.BookEntity
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.FormatDetector
import com.lightreader.core.format.ParseException
import com.lightreader.core.format.ParserRegistry
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.CoverProvider
import com.lightreader.core.model.ShelfKind
import com.lightreader.core.util.Logs
import com.lightreader.core.util.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns picked content URIs into library entries: format detection, metadata, cover thumbnail
 * and a persisted read permission so the book keeps working after a reboot.
 */
class BookImporter(
    private val context: Context,
    private val registry: ParserRegistry,
    private val bookDao: BookDao,
    private val coverStore: CoverStore,
) {

    data class Result(
        val imported: Int,
        val duplicates: Int,
        val failures: List<Failure>,
    ) {
        data class Failure(val name: String, val message: String)
    }

    suspend fun import(uris: List<Uri>): Result = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        val failures = ArrayList<Result.Failure>()

        for (uri in uris) {
            val source = runCatching { DocumentSource.from(context, uri) }.getOrNull()
            if (source == null) {
                failures.add(Result.Failure(uri.lastPathSegment ?: "?", "无法读取文件信息"))
                continue
            }
            try {
                persistPermission(uri)
                if (bookDao.findByUri(source.uri) != null) {
                    duplicates++
                    continue
                }
                val format = FormatDetector.detect(context, source)
                if (format == BookFormat.UNKNOWN) {
                    failures.add(Result.Failure(source.displayName, "不支持的文件格式"))
                    continue
                }

                var title = source.displayName.substringBeforeLast('.').ifBlank { source.displayName }
                var author: String? = null
                var coverBytes: ByteArray? = null

                runCatching {
                    val (document, detected) = registry.open(context, source, format)
                    document.use { opened ->
                        opened.metadata.title?.takeIf { it.isNotBlank() }?.let { title = it }
                        opened.metadata.author?.takeIf { it.isNotBlank() }?.let { author = it }
                        if (opened is CoverProvider) {
                            coverBytes = opened.loadCover()
                        }
                        Logs.d("识别为 ${detected.displayName}：$title")
                    }
                }.onFailure { throwable ->
                    if (throwable is ParseException) throw throwable
                    Logs.w("导入时读取元数据失败：${throwable.message}", throwable)
                }

                val id = bookDao.insert(
                    BookEntity(
                        uri = source.uri,
                        fileName = source.displayName,
                        title = title,
                        author = author,
                        format = format.id,
                        shelfKind = ShelfKind.defaultFor(format).id,
                        mimeType = source.mimeType,
                        fileSize = source.sizeBytes,
                        coverPath = null,
                        addedAt = System.currentTimeMillis(),
                        lastOpenedAt = null,
                        progressPercent = 0f,
                        lastLocationJson = null,
                    ),
                )
                if (id <= 0) {
                    duplicates++
                    continue
                }
                val coverPath = coverStore.write(id, coverBytes)
                if (coverPath != null) bookDao.updateCover(id, coverPath)
                imported++
            } catch (throwable: Throwable) {
                Logs.w("导入失败：${source.displayName}", throwable)
                val message = (throwable as? ParseException)?.message
                    ?: throwable.message
                    ?: "未知错误"
                failures.add(Result.Failure(source.displayName, message))
            }
        }
        Result(imported, duplicates, failures)
    }

    fun deleteLocalFile(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != ContentResolver.SCHEME_CONTENT) return false
        return runCatching {
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, parsed)
        }.getOrDefault(false)
    }

    private fun persistPermission(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Logs.d("无法持久化权限（可能是临时授权）：${it.message}") }
    }
}
