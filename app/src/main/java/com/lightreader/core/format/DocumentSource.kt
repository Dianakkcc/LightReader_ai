package com.lightreader.core.format

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lightreader.core.model.BookFormat
import com.lightreader.core.util.Logs
import java.io.File
import java.io.InputStream

/**
 * A file the user wants to read. Everything stays URI based (SAF friendly): the app never
 * requires the user to copy books into a special folder.
 */
data class DocumentSource(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
) {
    val extension: String
        get() = displayName.substringAfterLast('.', "").lowercase()

    val format: BookFormat
        get() = BookFormat.fromExtension(extension)
            ?: BookFormat.fromMimeType(mimeType)
            ?: BookFormat.UNKNOWN

    val androidUri: Uri get() = Uri.parse(uri)

    fun openInput(context: Context): InputStream =
        context.contentResolver.openInputStream(androidUri)
            ?: throw ParseException(ParseErrorReason.PERMISSION, "无法读取文件：$uri")

    companion object {
        fun from(context: Context, uri: Uri): DocumentSource {
            var name: String? = null
            var size = -1L
            if (uri.scheme == "content") {
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                                name = cursor.getString(nameIndex)
                            }
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                size = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                }.onFailure { Logs.w("查询文件信息失败：${it.message}", it) }
            }
            val path = uri.path
            val fileName = name ?: path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "book"
            if (size <= 0 && uri.scheme == "file" && path != null) {
                size = File(path).length()
            }
            return DocumentSource(
                uri = uri.toString(),
                displayName = fileName,
                sizeBytes = size,
                mimeType = context.contentResolver.getType(uri),
            )
        }

        /** The source of a file inside app-private storage (cached copies, exported data). */
        fun ofFile(file: File, mimeType: String? = null): DocumentSource = DocumentSource(
            uri = Uri.fromFile(file).toString(),
            displayName = file.name,
            sizeBytes = file.length(),
            mimeType = mimeType,
        )
    }
}
