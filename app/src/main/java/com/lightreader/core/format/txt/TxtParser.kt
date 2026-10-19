package com.lightreader.core.format.txt

import android.content.Context
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.toParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.OpenedDocument

class TxtParser : DocumentParser {

    override val format: BookFormat = BookFormat.TXT

    override fun canHandle(source: DocumentSource): Boolean =
        source.format == BookFormat.TXT || source.format == BookFormat.UNKNOWN

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument = try {
        TxtDocument.open(context, source)
    } catch (throwable: Throwable) {
        throw throwable.toParseException("无法打开文本文件")
    }
}
