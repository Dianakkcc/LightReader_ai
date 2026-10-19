package com.lightreader.core.data

import com.lightreader.core.model.LibraryLayout
import com.lightreader.core.model.LibrarySort
import com.lightreader.core.model.IndentMode
import com.lightreader.core.model.PageFit
import com.lightreader.core.model.ReadingLocation
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.model.ThemePreference
import kotlinx.serialization.Serializable

/**
 * Versioned, human readable backup format. Books themselves are never copied - only the reading
 * data, so the export stays a few kilobytes even for a huge library.
 */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val books: List<BackupBook> = emptyList(),
    val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupBook(
    val uri: String,
    val fileName: String,
    val title: String,
    val author: String? = null,
    val format: String,
    /** Shelf of the entry ("book" / "comic"); absent in backups written before v2.6. */
    val shelfKind: String? = null,
    val progress: ReadingLocation? = null,
    val bookmarks: List<BackupBookmark> = emptyList(),
)

@Serializable
data class BackupBookmark(
    val location: ReadingLocation,
    val title: String,
    val snippet: String,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupSettings(
    val fontSizeSp: Float,
    val lineHeightMultiplier: Float,
    val letterSpacingEm: Float,
    val paragraphSpacingEm: Float,
    val marginDp: Int,
    val alignment: String,
    val indentMode: String = IndentMode.AUTO.id,
    val useSerif: Boolean = false,
    val theme: String,
    val readingMode: String,
    val pageFit: String,
    val dualPage: Boolean,
    val libraryLayout: String,
    val librarySort: String,
    val showAuthor: Boolean,
    val showProgress: Boolean,
    val gridColumns: Int = 0,
    val comicGridColumns: Int = 3,
)

@Serializable
internal data class BackupError(val message: String)

internal fun TextAlignment.serialize() = id
internal fun IndentMode.serialize() = id
internal fun ThemePreference.serialize() = id
internal fun ReadingMode.serialize() = id
internal fun PageFit.serialize() = id
internal fun LibraryLayout.serialize() = id
internal fun LibrarySort.serialize() = id
