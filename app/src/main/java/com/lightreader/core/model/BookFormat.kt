package com.lightreader.core.model

/**
 * Every format the app knows about. Adding a format means adding an entry here plus a
 * [com.lightreader.core.format.DocumentParser] implementation - never touching the reader UI.
 */
enum class BookFormat(
    val id: String,
    val displayName: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
) {
    EPUB(
        id = "epub",
        displayName = "EPUB",
        extensions = listOf("epub"),
        mimeTypes = listOf("application/epub+zip"),
    ),
    PDF(
        id = "pdf",
        displayName = "PDF",
        extensions = listOf("pdf"),
        mimeTypes = listOf("application/pdf"),
    ),
    TXT(
        id = "txt",
        displayName = "TXT",
        extensions = listOf("txt", "text", "log", "md5"),
        mimeTypes = listOf("text/plain"),
    ),
    DOCX(
        id = "docx",
        displayName = "DOCX",
        extensions = listOf("docx"),
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ),
    ),
    RTF(
        id = "rtf",
        displayName = "RTF",
        extensions = listOf("rtf"),
        mimeTypes = listOf("application/rtf", "text/rtf", "text/richtext"),
    ),
    HTML(
        id = "html",
        displayName = "HTML",
        extensions = listOf("html", "htm", "xhtml"),
        mimeTypes = listOf("text/html", "application/xhtml+xml"),
    ),
    MARKDOWN(
        id = "markdown",
        displayName = "Markdown",
        extensions = listOf("md", "markdown", "mdown", "mkd"),
        mimeTypes = listOf("text/markdown", "text/x-markdown"),
    ),
    IMAGE(
        id = "image",
        displayName = "Image",
        extensions = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif"),
        mimeTypes = listOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif", "image/heic"),
    ),
    /**
     * Comic archive: a zip whose entries are page images. Images in the archive root are one
     * chapter, images inside folders are one chapter per folder (see
     * [com.lightreader.core.format.comic.ComicDocument]).
     */
    COMIC(
        id = "comic",
        displayName = "漫画",
        extensions = listOf("cbz", "zip"),
        mimeTypes = listOf(
            "application/vnd.comicbook+zip",
            "application/x-cbz",
            "application/zip",
        ),
    ),
    UNKNOWN(
        id = "unknown",
        displayName = "?",
        extensions = emptyList(),
        mimeTypes = emptyList(),
    ),
    ;

    val isReflowable: Boolean
        get() = this == EPUB || this == TXT || this == DOCX || this == RTF ||
            this == HTML || this == MARKDOWN

    val isPaged: Boolean
        get() = this == PDF || this == IMAGE || this == COMIC

    /** Formats read one image per page, so the reader shows them without reflowing text. */
    val isImageBased: Boolean
        get() = this == IMAGE || this == COMIC

    companion object {
        fun fromId(id: String?): BookFormat =
            entries.firstOrNull { it.id == id } ?: UNKNOWN

        fun fromExtension(extension: String?): BookFormat? {
            val ext = extension?.lowercase() ?: return null
            return entries.firstOrNull { ext in it.extensions }
        }

        fun fromMimeType(mimeType: String?): BookFormat? {
            val mime = mimeType?.lowercase()?.substringBefore(';')?.trim() ?: return null
            return entries.firstOrNull { mime in it.mimeTypes }
        }
    }
}

/** How a document must be laid out by the reader. */
enum class DocumentKind {
    /** Content can be re-flowed: font, size, margins and line height are user choices. */
    REFLOWABLE,

    /** Layout is authored by the file and must be preserved: page, zoom and pan only. */
    FIXED,
}
