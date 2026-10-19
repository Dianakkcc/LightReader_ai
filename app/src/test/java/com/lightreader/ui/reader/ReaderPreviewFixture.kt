package com.lightreader.ui.reader

import com.lightreader.core.format.BlockHtml
import com.lightreader.core.format.txt.TxtBlocks
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.model.ThemePreference
import com.lightreader.ui.theme.ReaderTheme
import com.lightreader.ui.theme.readerPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Writes the exact HTML the reader hands to the WebView, so the layout can be measured and
 * screenshotted in a real Blink engine (see `tools/preview/run-preview.ps1`).
 *
 * This is not an assertion about behaviour - it is the fixture that makes it possible to inspect
 * pagination, margins and typography outside a phone.
 */
class ReaderPreviewFixture {

    @Test
    fun writePreviewPage() {
        val outputDir = File(
            File(System.getProperty("user.dir")).parentFile,
            "tools/preview/pages",
        )
        outputDir.mkdirs()

        val chapter = ChapterContent(
            index = 0,
            title = "第一章 排版预览",
            html = BlockHtml.render(TxtBlocks.parse(sampleText())),
            text = sampleText(),
            baseUrl = "https://reader.local/",
        )

        listOf(
            "chapter-phone.html" to ReaderSettings(
                fontSizeSp = 19f,
                lineHeightMultiplier = 1.8f,
                paragraphSpacingEm = 0.6f,
                letterSpacingEm = 0.02f,
                marginDp = 12, // what the phone adaptive rule ends up with
                alignment = TextAlignment.JUSTIFY,
                theme = ThemePreference.PAPER,
                readingMode = ReadingMode.PAGED,
                dualPage = false,
            ),
            "chapter-tablet-2up.html" to ReaderSettings(
                fontSizeSp = 19f,
                lineHeightMultiplier = 1.8f,
                paragraphSpacingEm = 0.6f,
                letterSpacingEm = 0.02f,
                marginDp = 22,
                alignment = TextAlignment.JUSTIFY,
                theme = ThemePreference.PAPER,
                readingMode = ReadingMode.PAGED,
                dualPage = true,
            ),
            "chapter-scroll.html" to ReaderSettings(
                fontSizeSp = 19f,
                lineHeightMultiplier = 1.8f,
                marginDp = 22,
                theme = ThemePreference.PAPER,
                readingMode = ReadingMode.SCROLL,
            ),
        ).forEach { (name, settings) ->
            val html = ChapterHtml.build(
                content = chapter,
                settings = settings,
                palette = readerPalette(ReaderTheme.PAPER),
                fontScale = 1f,
                horizontalPaddingPx = settings.marginDp,
                verticalPaddingPx = settings.marginDp / 2 + 16,
                initialOffset = 0,
                nextChapterTitle = "第二章 下一章标题",
                previousChapterTitle = "序章 上一章标题",
            )
            File(outputDir, name).writeText(html, Charsets.UTF_8)
        }

        assertTrue(
            "预览页面已生成到 ${outputDir.absolutePath}",
            File(outputDir, "chapter-phone.html").isFile,
        )
    }

    private fun sampleText(): String {
        val paragraph = "他在旧书铺的角落里翻出这本书时，天光正从窗棂斜斜地照进来，灰尘在光里缓慢地浮沉。" +
            "书页的边缘已经发脆，翻动时会发出细碎的声响，像冬夜里踩过薄冰。"
        val builder = StringBuilder()
        builder.append("第一章 排版预览\n\n")
        repeat(24) { index ->
            builder.append(paragraph)
            if (index % 5 == 0) builder.append("这一段用来观察行距、字距与两端对齐之后的观感。")
            builder.append("\n\n")
        }
        return builder.toString()
    }
}
