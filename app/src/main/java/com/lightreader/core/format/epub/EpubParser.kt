package com.lightreader.core.format.epub

import android.content.Context
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.toParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.OpenedDocument

class EpubParser : DocumentParser {

    override val format: BookFormat = BookFormat.EPUB

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.EPUB

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument = try {
        EpubDocument.open(context, source)
    } catch (throwable: Throwable) {
        throw throwable.toParseException("无法打开 EPUB")
    }
}
