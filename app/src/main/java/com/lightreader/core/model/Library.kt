package com.lightreader.core.model

import kotlinx.serialization.Serializable

data class Book(
    val id: Long,
    val uri: String,
    val fileName: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    /** Which shelf this book lives on: 书籍 or 漫画. Movable by hand from the long press menu. */
    val shelfKind: ShelfKind = ShelfKind.BOOK,
    val mimeType: String?,
    val fileSize: Long,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progressPercent: Float,
    val lastLocationJson: String?,
) {
    val hasCover: Boolean get() = coverPath != null
}

/**
 * The two shelves the library is split into.
 *
 * Text formats are books by definition and comic archives are comics, but plenty of files could be
 * either (a PDF of scans, a folder of images, a picture-heavy EPUB), so the shelf of anything that
 * is not obvious is decided by the reader: long press the entry and move it.
 */
enum class ShelfKind(val id: String, val displayName: String) {
    BOOK("book", "书籍"),
    COMIC("comic", "漫画"),
    ;

    companion object {
        fun fromId(id: String?): ShelfKind = entries.firstOrNull { it.id == id } ?: BOOK

        /** Shelf a freshly imported file lands on. */
        fun defaultFor(format: BookFormat): ShelfKind = when (format) {
            BookFormat.COMIC -> COMIC
            else -> BOOK
        }
    }
}

enum class LibrarySort(val id: String) {
    RECENT("recent"),
    ADDED("added"),
    TITLE("title"),
    AUTHOR("author"),
    PROGRESS("progress"),
    ;

    companion object {
        fun fromId(id: String?): LibrarySort = entries.firstOrNull { it.id == id } ?: RECENT
    }
}

enum class LibraryLayout(val id: String) {
    GRID("grid"),
    LIST("list"),
    ;

    companion object {
        fun fromId(id: String?): LibraryLayout = entries.firstOrNull { it.id == id } ?: GRID
    }
}

data class Bookmark(
    val id: Long,
    val bookId: Long,
    val chapterIndex: Int,
    val charOffset: Int,
    val pageIndex: Int,
    val locationJson: String,
    val title: String,
    val snippet: String,
    val note: String?,
    val createdAt: Long,
)

/**
 * Reading position. Deliberately richer than a percentage: a percentage alone cannot restore
 * the exact spot after the font size changed.
 */
@Serializable
data class ReadingLocation(
    val chapterIndex: Int = 0,
    val charOffset: Int = 0,
    val pageIndex: Int = 0,
    val zoom: Float = 1f,
    val scrollY: Int = 0,
    val percentage: Float = 0f,
    val pageCount: Int = 0,
) {
    /** Only used as an equality key when deciding whether a save is worth a database write. */
    fun isCloseTo(other: ReadingLocation): Boolean =
        chapterIndex == other.chapterIndex &&
            kotlin.math.abs(charOffset - other.charOffset) < 24 &&
            pageIndex == other.pageIndex &&
            kotlin.math.abs(zoom - other.zoom) < 0.01f &&
            kotlin.math.abs(scrollY - other.scrollY) < 8
}

data class FontItem(
    val id: Long,
    val displayName: String,
    val fileName: String,
    val path: String,
    val addedAt: Long,
    val isBuiltin: Boolean = false,
)
