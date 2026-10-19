package com.lightreader.core.format.markdown

import android.content.Context
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.HtmlSanitizer
import com.lightreader.core.format.html.HtmlBasedDocument
import com.lightreader.core.format.html.HtmlChapterizer
import com.lightreader.core.format.toParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.OpenedDocument
import com.lightreader.core.util.TextDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** Renders Markdown (including GFM tables) to HTML and reuses the HTML chapter pipeline. */
class MarkdownParser : DocumentParser {

    override val format: BookFormat = BookFormat.MARKDOWN

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.MARKDOWN

    private val extensions = listOf(TablesExtension.create())

    private val parser = Parser.builder().extensions(extensions).build()

    private val renderer = HtmlRenderer.builder().extensions(extensions).build()

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument =
        withContext(Dispatchers.IO) {
            try {
                val bytes = source.openInput(context.applicationContext).use { it.readBytes() }
                val markdown = TextDecoding.decode(bytes)
                val html = renderer.render(parser.parse(markdown))
                val cleaned = HtmlSanitizer.cleanToBodyHtml(html)
                val title = firstHeading(markdown) ?: source.displayName.substringBeforeLast('.')
                HtmlBasedDocument(
                    format = BookFormat.MARKDOWN,
                    metadata = DocumentMetadata(title = title),
                    sections = HtmlChapterizer.split(cleaned, title),
                )
            } catch (throwable: Throwable) {
                throw throwable.toParseException("无法打开 Markdown 文件")
            }
        }

    private fun firstHeading(markdown: String): String? = markdown.lineSequence()
        .firstOrNull { it.startsWith("# ") }
        ?.removePrefix("# ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
