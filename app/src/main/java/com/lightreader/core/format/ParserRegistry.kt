package com.lightreader.core.format

import android.content.Context
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.OpenedDocument

/**
 * Picks the parser for a source and produces the unified document model. Adding MOBI/FB2/CBZ
 * later means registering one more [DocumentParser] here.
 */
class ParserRegistry(private val parsers: List<DocumentParser>) {

    fun parserFor(source: DocumentSource): DocumentParser? =
        parsers.firstOrNull { it.canHandle(source) }

    suspend fun open(
        context: Context,
        source: DocumentSource,
        formatOverride: BookFormat? = null,
    ): Pair<OpenedDocument, BookFormat> {
        val detected = formatOverride?.takeIf { it != BookFormat.UNKNOWN }
            ?: FormatDetector.detect(context, source)
        val parser = parsers.firstOrNull { it.format == detected }
            ?: throw ParseException(
                ParseErrorReason.UNSUPPORTED,
                "暂不支持此格式：${source.displayName}",
            )
        val document = parser.parse(context, source)
        return document to detected
    }
}
