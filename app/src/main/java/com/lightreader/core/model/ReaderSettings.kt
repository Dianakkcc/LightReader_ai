package com.lightreader.core.model

import com.lightreader.ui.theme.ReaderTheme

enum class TextAlignment(val id: String) {
    START("start"),
    JUSTIFY("justify"),
    CENTER("center"),
    END("end"),
    ;

    companion object {
        fun fromId(id: String?): TextAlignment = entries.firstOrNull { it.id == id } ?: START
    }
}

/** First line indentation, the single most visible difference between "text dump" and a book. */
enum class IndentMode(val id: String) {
    /** Indent documents that ship no stylesheet of their own (TXT, DOCX, RTF, Markdown). */
    AUTO("auto"),
    ON("on"),
    OFF("off"),
    ;

    companion object {
        fun fromId(id: String?): IndentMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

enum class ReadingMode(val id: String) {
    /** Continuous vertical scrolling. */
    SCROLL("scroll"),

    /** Horizontal, page by page. */
    PAGED("paged"),
    ;

    companion object {
        fun fromId(id: String?): ReadingMode = entries.firstOrNull { it.id == id } ?: SCROLL
    }
}

enum class ThemePreference(val id: String) {
    SYSTEM("system"),
    PAPER("paper"),
    OAT("oat"),
    SAGE("sage"),
    ROSE("rose"),
    MINT("mint"),
    AZURE("azure"),
    LILAC("lilac"),
    NIGHT("night"),
    DUSK("dusk"),
    FOREST("forest"),
    BLACK("black"),
    JIARAN("jiaran"),
    ;

    companion object {
        /** Also maps the ids used by 1.0/1.1 so an upgrade keeps the user's choice. */
        fun fromId(id: String?): ThemePreference = when (id) {
            "light" -> PAPER
            "sepia" -> OAT
            "dark" -> NIGHT
            null -> SYSTEM
            else -> entries.firstOrNull { it.id == id } ?: SYSTEM
        }
    }
}

enum class PageFit(val id: String) {
    WIDTH("width"),
    PAGE("page"),
    ACTUAL("actual"),
    ;

    companion object {
        fun fromId(id: String?): PageFit = entries.firstOrNull { it.id == id } ?: WIDTH
    }
}

/** Everything the reading surface needs; changed live from the in-reader settings sheet. */
data class ReaderSettings(
    val fontId: Long? = null,
    val fontFamily: String? = null,
    val fontPath: String? = null,
    val fontDisplayName: String? = null,
    val useSerif: Boolean = false,
    val fontSizeSp: Float = 19f,
    val lineHeightMultiplier: Float = 1.8f,
    val letterSpacingEm: Float = 0.02f,
    val paragraphSpacingEm: Float = 0.6f,
    val marginDp: Int = 22,
    val alignment: TextAlignment = TextAlignment.JUSTIFY,
    val indentMode: IndentMode = IndentMode.AUTO,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /** Continuous scrolling by default; page turning is one tap away in the reading settings. */
    val readingMode: ReadingMode = ReadingMode.SCROLL,
    val pageFit: PageFit = PageFit.WIDTH,
    val dualPage: Boolean = false,
)

data class LibrarySettings(
    val layout: LibraryLayout = LibraryLayout.GRID,
    val sort: LibrarySort = LibrarySort.RECENT,
    val showAuthor: Boolean = true,
    val showProgress: Boolean = true,
    /** Which shelf the library screen is showing: 书籍 or 漫画. */
    val shelf: ShelfKind = ShelfKind.BOOK,
    /** 0 follows screen width; each shelf remembers its own density. */
    val gridColumns: Int = 0,
    val comicGridColumns: Int = 3,
)
