package com.lightreader.core.format.html

import android.content.Context
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.HtmlSanitizer
import com.lightreader.core.format.toParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.OpenedDocument
import com.lightreader.core.util.TextDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** Reads a standalone HTML / XHTML file. */
class HtmlParser : DocumentParser {

    override val format: BookFormat = BookFormat.HTML

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.HTML

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument =
        withContext(Dispatchers.IO) {
            try {
                val bytes = source.openInput(context.applicationContext).use { it.readBytes() }
                val raw = TextDecoding.decode(bytes)
                val parsed = Jsoup.parse(raw)
                val title = parsed.title().trim().ifBlank {
                    source.displayName.substringBeforeLast('.')
                }
                val cleaned = HtmlSanitizer.cleanToBodyHtml(parsed.body().html())
                HtmlBasedDocument(
                    format = BookFormat.HTML,
                    metadata = DocumentMetadata(
                        title = title,
                        language = parsed.selectFirst("html[lang]")?.attr("lang")?.takeIf { it.isNotBlank() },
                    ),
                    sections = HtmlChapterizer.split(cleaned, title),
                )
            } catch (throwable: Throwable) {
                throw throwable.toParseException("无法打开 HTML 文件")
            }
        }
}
