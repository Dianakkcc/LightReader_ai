package com.lightreader.core.format.comic

import android.content.Context
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.toParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.OpenedDocument

/** Opens `.cbz` / image-only `.zip` archives as a paged comic. */
class ComicParser : DocumentParser {

    override val format: BookFormat = BookFormat.COMIC

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.COMIC

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument = try {
        ComicDocument.open(context, source)
    } catch (throwable: Throwable) {
        throw throwable.toParseException("无法打开漫画压缩包")
    }
}
