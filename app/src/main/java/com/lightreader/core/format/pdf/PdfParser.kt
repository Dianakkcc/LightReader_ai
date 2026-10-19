package com.lightreader.core.format.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.lightreader.core.data.BookWorkspace
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.ParseErrorReason
import com.lightreader.core.format.ParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.OpenedDocument
import com.lightreader.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfParser : DocumentParser {

    override val format: BookFormat = BookFormat.PDF

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.PDF

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument =
        withContext(Dispatchers.IO) {
            val workspace = BookWorkspace(context)
            val key = workspace.keyFor(source.uri)

            val opened = openRenderer(context, source, workspace, key)
                ?: throw ParseException(
                    ParseErrorReason.CORRUPT,
                    "无法打开 PDF：文件可能已损坏或没有权限",
                )

            val (renderer, descriptor) = opened
            val title = source.displayName.substringBeforeLast('.').ifBlank { source.displayName }
            Logs.d("PDF 打开：$title 页数=${renderer.pageCount}")
            PdfDocument(
                source = source,
                renderer = renderer,
                descriptor = descriptor,
                textEngine = PdfTextEngine(context.applicationContext, source, workspace.pdfCacheDir),
                metadata = DocumentMetadata(title = title),
                toc = emptyList(),
            )
        }

    /**
     * Most document providers hand out a seekable file descriptor, which PdfRenderer needs.
     * When they do not, the file is copied into the cache once and opened from there.
     */
    private fun openRenderer(
        context: Context,
        source: DocumentSource,
        workspace: BookWorkspace,
        key: String,
    ): Pair<PdfRenderer, ParcelFileDescriptor?>? {
        runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(source.androidUri, "r")
            if (descriptor != null) {
                val renderer = PdfRenderer(descriptor)
                return renderer to descriptor
            }
        }.onFailure { Logs.w("直接打开 PDF 失败：${it.message}", it) }

        return runCatching {
            val file: File = kotlinx.coroutines.runBlocking {
                workspace.materialise(source, key, "pdf")
            }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            renderer to descriptor
        }.onFailure { Logs.w("缓存后打开 PDF 失败：${it.message}", it) }
            .getOrNull()
    }
}
