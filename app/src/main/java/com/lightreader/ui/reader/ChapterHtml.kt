package com.lightreader.ui.reader

import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.ThemePreference
import com.lightreader.ui.theme.ReaderPalette

/**
 * Assembles the complete HTML document for one chapter: book stylesheet, reader stylesheet,
 * chapter content, and the JavaScript engine that owns pagination, gestures and the position
 * bridge.
 */
object ChapterHtml {

    fun build(
        content: ChapterContent,
        settings: ReaderSettings,
        palette: ReaderPalette,
        fontScale: Float,
        horizontalPaddingPx: Int,
        verticalPaddingPx: Int,
        initialOffset: Int,
        nextChapterTitle: String?,
        previousChapterTitle: String? = null,
    ): String {
        val paged = settings.readingMode == ReadingMode.PAGED
        val css = ReaderCss.build(
            settings = settings,
            palette = palette,
            paged = paged,
            fontScale = fontScale,
            bookCss = content.css,
            horizontalPaddingPx = horizontalPaddingPx,
        )
        val script = ReaderScript.build(
            chapterIndex = content.index,
            paged = paged,
            initialOffset = initialOffset,
            horizontalPaddingPx = horizontalPaddingPx,
            verticalPaddingPx = verticalPaddingPx,
            nextChapterTitle = nextChapterTitle,
            previousChapterTitle = previousChapterTitle,
            twoUp = settings.dualPage,
        )
        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"zh\">\n<head>\n")
            append("<meta charset=\"utf-8\">\n")
            append(
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, " +
                    "maximum-scale=1.0, user-scalable=no\">\n",
            )
            // The id lets the reader restyle the live page instead of reloading the chapter.
            append("<style id=\"lr-style\">\n").append(css).append("\n</style>\n")
            append("</head>\n<body>\n")
            append("<div id=\"lr-view\"><div id=\"lr-content\">")
            append(content.html)
            append("</div></div>\n")
            // The chapter labels that slide in when the reader pulls past a chapter boundary.
            append("<div id=\"lr-edge-prev\" class=\"lr-edge lr-edge-top\"></div>\n")
            append("<div id=\"lr-edge-next\" class=\"lr-edge lr-edge-bottom\"></div>\n")
            if (settings.theme == ThemePreference.JIARAN) {
                // Diana / 嘉然 limited theme: the character watermark of this theme only.
                append("<div id=\"lr-brand\"></div>\n")
            }
            // Registered before the engine so that even a parse error in it says so in the log.
            append(
                "<script>window.__lrDiag=[];window.addEventListener('error',function(e){" +
                    "try{window.__lrDiag.push('page-error:'+e.message+' @'+e.lineno+':'+e.colno);" +
                    "}catch(x){}try{if(window.LrBridge&&LrBridge.onMetrics){LrBridge.onMetrics(" +
                    "JSON.stringify({stage:'page-error',extra:String(e.message)+' @'+e.lineno+" +
                    "':'+e.colno}));}}catch(x){}});</script>\n",
            )
            append("<script>\n").append(script).append("\n</script>\n")
            append("</body>\n</html>")
        }
    }
}
