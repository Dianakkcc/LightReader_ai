package com.lightreader.core.format

import com.lightreader.core.format.docx.DocxParser
import com.lightreader.core.format.comic.ComicParser
import com.lightreader.core.format.epub.EpubParser
import com.lightreader.core.format.html.HtmlParser
import com.lightreader.core.format.image.ImageParser
import com.lightreader.core.format.markdown.MarkdownParser
import com.lightreader.core.format.pdf.PdfParser
import com.lightreader.core.format.rtf.RtfParser
import com.lightreader.core.format.txt.TxtParser

/**
 * Single place that knows every format parser. Future formats (MOBI, AZW3, FB2, CBZ, CBR, DjVu)
 * are added here plus one parser implementation.
 */
object ParserFactory {

    fun createRegistry(): ParserRegistry = ParserRegistry(
        listOf(
            EpubParser(),
            PdfParser(),
            TxtParser(),
            DocxParser(),
            RtfParser(),
            HtmlParser(),
            MarkdownParser(),
            ImageParser(),
            ComicParser(),
        ),
    )
}
