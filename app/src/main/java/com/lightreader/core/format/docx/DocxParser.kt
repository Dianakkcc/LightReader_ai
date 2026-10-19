package com.lightreader.core.format.docx

import android.content.Context
import com.lightreader.core.data.BookWorkspace
import com.lightreader.core.format.Block
import com.lightreader.core.format.BlockHtml
import com.lightreader.core.format.ChapterSplitter
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.ParseErrorReason
import com.lightreader.core.format.ParseException
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.format.html.HtmlBasedDocument
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.DocumentMetadata
import com.lightreader.core.model.OpenedDocument
import com.lightreader.core.model.ReaderResource
import com.lightreader.core.util.Logs
import com.lightreader.core.util.TextDecoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * DOCX (WordprocessingML) reader.
 *
 * Word's own layout engine is not reimplemented: the document is converted into the unified
 * block model and then laid out by the reader, so the font size, theme and margins the user
 * chooses still apply.
 */
class DocxParser : DocumentParser {

    override val format: BookFormat = BookFormat.DOCX

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.DOCX

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument =
        withContext(Dispatchers.IO) {
            val workspace = BookWorkspace(context)
            val key = workspace.keyFor(source.uri)
            val file = workspace.materialise(source, key, "docx")
            val zip = try {
                ZipFile(file)
            } catch (exception: ZipException) {
                workspace.dropBookCache(key)
                throw ParseException(ParseErrorReason.CORRUPT, "DOCX 文件已损坏", exception)
            } catch (throwable: Throwable) {
                throw ParseException(ParseErrorReason.UNKNOWN, "无法打开 DOCX 文件", throwable)
            }

            try {
                val documentXml = readEntry(zip, "word/document.xml")
                    ?: throw ParseException(ParseErrorReason.CORRUPT, "DOCX 缺少 word/document.xml")
                val relations = DocxRelations(parseRelations(zip))
                val numbering = parseNumbering(zip)
                val styles = parseStyleNames(zip)

                val document = Jsoup.parse(documentXml, "", Parser.xmlParser())
                val body = document.allElements.firstOrNull { it.localName() == "body" }
                    ?: throw ParseException(ParseErrorReason.CORRUPT, "DOCX 正文为空")

                val blocks = DocxBlocks(relations, numbering, styles, key).convert(body)
                val title = parseTitle(zip, document, source)
                val sections = ChapterSplitter.split(blocks, title).map { section ->
                    val html = BlockHtml.render(section.blocks)
                    HtmlBasedDocument.Section(section.title, section.level) { html }
                }
                Logs.d("DOCX 打开：$title 区块=${blocks.size} 章节=${sections.size}")

                HtmlBasedDocument(
                    format = BookFormat.DOCX,
                    metadata = DocumentMetadata(title = title),
                    sections = sections,
                    resourceResolver = { path ->
                        val prefix = "${ReaderAssets.MEDIA_PREFIX}$key/"
                        if (!path.startsWith(prefix)) {
                            null
                        } else {
                            val entryPath = path.removePrefix(prefix)
                            readEntryBytes(zip, entryPath)?.let {
                                ReaderResource(it, mimeTypeOf(entryPath))
                            }
                        }
                    },
                    closer = { runCatching { zip.close() } },
                )
            } catch (throwable: Throwable) {
                runCatching { zip.close() }
                throw when (throwable) {
                    is ParseException -> throwable
                    else -> ParseException(ParseErrorReason.CORRUPT, "无法解析 DOCX 内容", throwable)
                }
            }
        }

    private fun readEntry(zip: ZipFile, path: String): String? {
        val entry = zip.getEntry(path) ?: return null
        return zip.getInputStream(entry).use { TextDecoding.decode(it.readBytes()) }
    }

    private fun readEntryBytes(zip: ZipFile, path: String): ByteArray? {
        val entry = zip.getEntry(path) ?: return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun parseRelations(zip: ZipFile): Map<String, String> {
        val xml = readEntry(zip, "word/_rels/document.xml.rels") ?: return emptyMap()
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        return document.allByLocal("relationship").mapNotNull { element ->
            val id = element.attr("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val target = element.attr("Target").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val external = element.attr("TargetMode").equals("External", true)
            id to if (external) target else target
        }.toMap()
    }

    private fun parseStyleNames(zip: ZipFile): Map<String, String> {
        val xml = readEntry(zip, "word/styles.xml") ?: return emptyMap()
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        return document.allByLocal("style").mapNotNull { style ->
            val id = style.attr("w:styleId").takeIf { it.isNotBlank() }
                ?: style.attr("styleId").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val name = style.firstByLocal("name")?.let { element ->
                element.attr("w:val").ifBlank { element.attr("val") }
            }
            if (name.isNullOrBlank()) null else id to name
        }.toMap()
    }

    private fun parseNumbering(zip: ZipFile): DocxNumbering {
        val xml = readEntry(zip, "word/numbering.xml") ?: return DocxNumbering.EMPTY
        val document = Jsoup.parse(xml, "", Parser.xmlParser())

        val abstractFormats = HashMap<String, MutableMap<Int, String>>()
        document.allByLocal("abstractnum").forEach { abstract ->
            val id = abstract.attr("w:abstractNumId").ifBlank { abstract.attr("abstractNumId") }
            if (id.isBlank()) return@forEach
            val levels = HashMap<Int, String>()
            abstract.allByLocal("lvl").forEach { level ->
                val index = level.attr("w:ilvl").ifBlank { level.attr("ilvl") }.toIntOrNull() ?: 0
                val format = level.firstByLocal("numfmt")?.let { element ->
                    element.attr("w:val").ifBlank { element.attr("val") }.ifBlank { null }
                }
                if (format != null) levels[index] = format
            }
            abstractFormats[id] = levels
        }

        val formats = HashMap<Pair<String, Int>, String>()
        document.allByLocal("num").forEach { num ->
            val numId = num.attr("w:numId").ifBlank { num.attr("numId") }
            val abstractId = num.firstByLocal("abstractnumid")?.let { element ->
                element.attr("w:val").ifBlank { element.attr("val") }.ifBlank { null }
            }
            if (numId.isBlank() || abstractId == null) return@forEach
            abstractFormats[abstractId]?.forEach { (level, format) ->
                formats[numId to level] = format
            }
        }
        return DocxNumbering(formats)
    }

    private fun parseTitle(zip: ZipFile, document: Document, source: DocumentSource): String {
        readEntry(zip, "docProps/core.xml")?.let { xml ->
            val core = Jsoup.parse(xml, "", Parser.xmlParser())
            core.firstByLocal("title")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        val firstHeading = document.allElements.filter { it.localName() == "p" }
            .firstOrNull { paragraph ->
            val styleId = paragraph.allElements.firstOrNull { it.localName() == "pstyle" }
                ?.let { it.attr("w:val").ifBlank { it.attr("val") } }
                .orEmpty()
            styleId.contains("Heading1", true) || styleId.contains("Heading 1", true) ||
                styleId.contains("标题1", true)
        }?.text()?.trim()
        if (!firstHeading.isNullOrEmpty()) return firstHeading
        return source.displayName.substringBeforeLast('.').ifBlank { source.displayName }
    }

    private fun mimeTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }
}
