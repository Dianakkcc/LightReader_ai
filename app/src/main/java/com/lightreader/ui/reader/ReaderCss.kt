package com.lightreader.ui.reader

import androidx.compose.ui.graphics.toArgb
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.model.IndentMode
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.TextAlignment
import com.lightreader.ui.theme.ReaderPalette
import java.util.Locale

/**
 * Stylesheet of the reading surface.
 *
 * The book's own stylesheet is emitted first, the reader's settings after it. Only the properties
 * the reader owns (font, size, line height, spacing, colours, alignment) are forced, so a book
 * keeps its personality (drop caps, section ornaments, inline styles) while the user stays in
 * charge of comfort.
 */
object ReaderCss {

    /** CSS pixels inside a WebView are density independent, exactly like dp. */
    private const val SANS_STACK =
        "system-ui, -apple-system, \"MiSans\", \"HarmonyOS Sans SC\", \"PingFang SC\", " +
            "\"Noto Sans CJK SC\", \"Source Han Sans SC\", \"Microsoft YaHei\", Roboto, sans-serif"

    private const val SERIF_STACK =
        "\"Noto Serif CJK SC\", \"Source Han Serif SC\", \"Songti SC\", \"STSong\", SimSun, " +
            "\"Times New Roman\", Georgia, serif"

    fun build(
        settings: ReaderSettings,
        palette: ReaderPalette,
        paged: Boolean,
        fontScale: Float,
        bookCss: String?,
        horizontalPaddingPx: Int,
    ): String {
        val background = hex(palette.background.toArgb())
        val surface = hex(palette.surface.toArgb())
        val text = hex(palette.text.toArgb())
        val secondary = hex(palette.secondaryText.toArgb())
        val accent = hex(palette.accent.toArgb())

        val scale = fontScale.coerceIn(0.7f, 1.6f)
        val fontSize = (settings.fontSizeSp * scale).coerceIn(11f, 42f)
        val lineHeight = settings.lineHeightMultiplier.coerceIn(1.1f, 3f)
        val letterSpacing = settings.letterSpacingEm.coerceIn(0f, 0.3f)
        val paragraphSpacing = settings.paragraphSpacingEm.coerceIn(0f, 3f)
        val align = when (settings.alignment) {
            TextAlignment.JUSTIFY -> "justify"
            TextAlignment.CENTER -> "center"
            TextAlignment.END -> "right"
            TextAlignment.START -> "left"
        }
        val indent = when (settings.indentMode) {
            IndentMode.ON -> true
            IndentMode.OFF -> false
            IndentMode.AUTO -> bookCss.isNullOrBlank()
        }

        val importedFontPath = settings.fontPath?.takeIf { it.isNotBlank() }
        val fontFaceCss = if (importedFontPath != null && settings.fontId != null) {
            "@font-face{font-family:'LRUserFont';src:url('${ReaderAssets.font(settings.fontId)}');" +
                "font-display:block;}"
        } else {
            ""
        }
        val family = when {
            importedFontPath != null -> "'LRUserFont', $SANS_STACK, $SERIF_STACK"
            settings.useSerif -> "$SERIF_STACK, $SANS_STACK"
            else -> "$SANS_STACK, $SERIF_STACK"
        }
        val gridFraction = hexWithAlpha(palette.secondaryText.toArgb(), 0.28f)
        val selection = hexWithAlpha(palette.accent.toArgb(), 0.26f)

        return buildString {
            append(fontFaceCss).append('\n')
            if (!bookCss.isNullOrBlank()) {
                append("/* ---- original book styles ---- */\n")
                append(bookCss).append('\n')
            }
            append("/* ---- reader styles ---- */\n")
            append(
                """
                html, body {
                    margin: 0;
                    padding: 0;
                    height: 100%;
                    overflow: hidden;
                    background: $background !important;
                    color: $text !important;
                    font-family: $family !important;
                    font-size: ${format(fontSize)}px !important;
                    line-height: ${format(lineHeight)} !important;
                    letter-spacing: ${format(letterSpacing)}em !important;
                    text-align: $align !important;
                    -webkit-text-size-adjust: none;
                    -webkit-font-smoothing: antialiased;
                    -webkit-tap-highlight-color: transparent;
                    font-kerning: normal;
                    text-rendering: optimizeLegibility;
                }
                #lr-view { position: fixed; top: 0; left: 0; right: 0; bottom: 0; overflow: hidden; }
                #lr-content {
                    box-sizing: border-box;
                    position: relative;
                    z-index: 1;
                    animation: lr-enter 220ms ease-out;
                }
                #lr-content.lr-turning { will-change: transform; }
                @keyframes lr-enter { from { opacity: 0 } to { opacity: 1 } }
                #lr-hint {
                    /* Kept only so an older page can never show a stale element. */
                    display: none !important;
                }
                /* Chapter boundary labels: plain text with a soft gradient towards the page, no
                   panel, no border, no plate - the label has to read as part of the page. They are
                   pulled in from the edge the reader drags towards and the chapter only changes
                   once the label is completely on screen. Only transform/opacity animate, so the
                   gesture stays on the compositor. */
                .lr-edge {
                    position: fixed;
                    left: 0;
                    right: 0;
                    height: 84px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 15px;
                    font-weight: 500;
                    letter-spacing: 0.14em;
                    opacity: 0;
                    pointer-events: none;
                    will-change: transform, opacity;
                    background-color: transparent !important;
                    color: $accent !important;
                }
                .lr-edge-bottom {
                    bottom: 0;
                    background-image: linear-gradient(
                        to top,
                        $background 0%,
                        $background 46%,
                        ${hexWithAlpha(palette.background.toArgb(), 0.0f)} 100%
                    ) !important;
                    transform: translate3d(0, 100%, 0);
                }
                .lr-edge-top {
                    top: 0;
                    background-image: linear-gradient(
                        to bottom,
                        $background 0%,
                        $background 46%,
                        ${hexWithAlpha(palette.background.toArgb(), 0.0f)} 100%
                    ) !important;
                    transform: translate3d(0, -100%, 0);
                }
                #lr-content > *:first-child { margin-top: 0 !important; }
                /* Diana / 嘉然 limited theme: the character is a low opacity backdrop of the whole
                   page, sitting behind the text. It only exists when that theme is selected and
                   never takes a touch. */
                #lr-brand {
                    position: fixed;
                    right: -4%;
                    bottom: -2%;
                    width: 74%;
                    max-width: 460px;
                    height: 68%;
                    background: url('${ReaderAssets.brand("jiaran.png")}') no-repeat right bottom/contain;
                    opacity: 0.11;
                    pointer-events: none;
                    z-index: 0;
                }

                /* Keep the reader in charge of type size and spacing. */
                p, div, span, li, dd, dt, td, th, section, article, aside, figcaption, blockquote,
                em, strong, i, b, u, s, a, font {
                    font-size: 1em !important;
                    line-height: ${format(lineHeight)} !important;
                    letter-spacing: ${format(letterSpacing)}em !important;
                    color: inherit !important;
                    background-color: transparent !important;
                    font-family: inherit !important;
                }
                p {
                    margin: 0 0 ${format(paragraphSpacing)}em 0 !important;
                    ${if (indent) "text-indent: 2em !important;" else "text-indent: 0 !important;"}
                    text-align: $align !important;
                }
                h1, h2, h3, h4, h5, h6 {
                    font-family: inherit !important;
                    color: $text !important;
                    line-height: 1.4 !important;
                    font-weight: 600 !important;
                    text-align: left !important;
                    text-indent: 0 !important;
                    break-after: avoid;
                }
                h1 { font-size: ${format(fontSize * 1.7f)}px !important; margin: 1.1em 0 0.6em !important; }
                h2 { font-size: ${format(fontSize * 1.45f)}px !important; margin: 1em 0 0.55em !important; }
                h3 { font-size: ${format(fontSize * 1.25f)}px !important; margin: 0.9em 0 0.5em !important; }
                h4 { font-size: ${format(fontSize * 1.12f)}px !important; margin: 0.8em 0 0.45em !important; }
                h5, h6 { font-size: ${format(fontSize)}px !important; margin: 0.7em 0 0.4em !important; }
                blockquote {
                    margin: ${format(paragraphSpacing)}em 0 !important;
                    padding: 0.35em 0 0.35em 1em !important;
                    border-left: 3px solid ${hexWithAlpha(palette.accent.toArgb(), 0.55f)} !important;
                    color: $secondary !important;
                    text-indent: 0 !important;
                }
                a { color: $accent !important; text-decoration: none !important; }
                ul, ol { margin: 0.45em 0 0.7em 0 !important; padding-left: 1.7em !important; }
                li { text-align: left !important; }
                hr {
                    border: none;
                    border-top: 1px solid $gridFraction;
                    margin: 1.4em auto !important;
                    width: 42%;
                }
                img, svg, image, video {
                    max-width: 100% !important;
                    height: auto !important;
                    /* In paged mode a picture taller than the column would break the page grid,
                       so it is capped to the page height instead. */
                    ${if (paged) "max-height: var(--lr-page-h, 90vh) !important;" else ""}
                    display: block;
                    margin: 0.7em auto !important;
                    border-radius: 4px;
                    break-inside: avoid;
                }
                figure { margin: ${format(paragraphSpacing)}em 0 !important; break-inside: avoid; }
                figcaption {
                    font-size: 0.86em !important;
                    color: $secondary !important;
                    text-align: center !important;
                    text-indent: 0 !important;
                }
                table {
                    border-collapse: collapse !important;
                    width: 100% !important;
                    margin: ${format(paragraphSpacing)}em 0 !important;
                    font-size: 0.94em !important;
                    break-inside: avoid;
                }
                td, th {
                    border: 1px solid $gridFraction !important;
                    padding: 0.4em 0.55em !important;
                    text-align: left !important;
                    text-indent: 0 !important;
                }
                th { background: $surface !important; font-weight: 600 !important; }
                pre {
                    white-space: pre-wrap !important;
                    word-break: break-word;
                    background: $surface !important;
                    padding: 0.7em 0.8em !important;
                    border-radius: 8px;
                    font-size: 0.92em !important;
                    line-height: 1.55 !important;
                    text-indent: 0 !important;
                    break-inside: avoid;
                }
                code, kbd, samp {
                    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace !important;
                    background: $surface !important;
                    border-radius: 4px;
                    padding: 0.05em 0.28em;
                }
                pre code { background: transparent !important; padding: 0 !important; }
                sup, sub { line-height: 0 !important; font-size: 0.74em !important; }
                img[src*="cover"], .cover img { margin-top: 0 !important; }
                ::selection { background: $selection; }
                mark.lr-hit {
                    background: ${hexWithAlpha(palette.accent.toArgb(), 0.38f)};
                    color: inherit;
                    border-radius: 3px;
                }
                """.trimIndent(),
            )
            append('\n')
        }
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)

    private fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

    /**
     * CSS writes 8-digit hex colours as #RRGGBBAA, while the colour int here is 0xAARRGGBB.
     * Getting that order wrong does not fail loudly: the browser happily reads the alpha byte as
     * the red channel, so "transparent paper" came out as fully opaque cyan (#00FCFCFA) and the
     * chapter banner showed a coloured band.
     */
    private fun hexWithAlpha(color: Int, alpha: Float): String {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return String.format("#%06X%02X", color and 0xFFFFFF, a)
    }
}
