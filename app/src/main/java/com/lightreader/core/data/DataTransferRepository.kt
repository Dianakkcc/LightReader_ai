package com.lightreader.core.data

import android.content.Context
import android.net.Uri
import com.lightreader.core.data.db.AppDatabase
import com.lightreader.core.model.AppSettings
import com.lightreader.core.model.LibraryLayout
import com.lightreader.core.model.LibrarySort
import com.lightreader.core.model.PageFit
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.ReadingLocation
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.ShelfKind
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.model.ThemePreference
import com.lightreader.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Export / import of reading data (progress, bookmarks, settings) as a small JSON file, so a
 * user can move to a new phone without the books themselves being duplicated.
 */
class DataTransferRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    data class RestoreStats(val matchedBooks: Int, val restoredBookmarks: Int, val appliedSettings: Boolean)

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = buildBackup()
        val payload = json.encodeToString(BackupFile.serializer(), backup).toByteArray()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(payload) }
            ?: error("无法写入所选文件")
        Logs.d("导出了 ${backup.books.size} 本书的阅读数据")
        backup.books.size
    }

    suspend fun restore(uri: Uri): RestoreStats = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)
            ?: error("无法读取所选文件")
        val backup = json.decodeFromString(BackupFile.serializer(), text)
        applyBackup(backup)
    }

    private suspend fun buildBackup(): BackupFile {
        val books = database.bookDao().observeAll().first()
        val entries = ArrayList<BackupBook>(books.size)
        var bookmarkCount = 0
        for (book in books) {
            val progress = database.progressDao().find(book.id)?.let {
                ReadingLocation(
                    chapterIndex = it.chapterIndex,
                    charOffset = it.charOffset,
                    pageIndex = it.pageIndex,
                    zoom = it.zoom,
                    scrollY = it.scrollY,
                    percentage = it.percentage,
                    pageCount = it.pageCount,
                )
            }
            val bookmarks = database.bookmarkDao().observeForBook(book.id).first().map { bookmark ->
                bookmarkCount++
                BackupBookmark(
                    location = decodeLocation(bookmark.locationJson),
                    title = bookmark.title,
                    snippet = bookmark.snippet,
                    note = bookmark.note,
                    createdAt = bookmark.createdAt,
                )
            }
            entries.add(
                BackupBook(
                    uri = book.uri,
                    fileName = book.fileName,
                    title = book.title,
                    author = book.author,
                    format = book.format,
                    shelfKind = book.shelfKind,
                    progress = progress,
                    bookmarks = bookmarks,
                ),
            )
        }
        Logs.d("导出数据：${entries.size} 本书，$bookmarkCount 条书签")
        return BackupFile(
            exportedAt = System.currentTimeMillis(),
            books = entries,
            settings = settingsStore.current().toBackup(),
        )
    }

    internal suspend fun applyBackup(backup: BackupFile): RestoreStats {
        var matched = 0
        var bookmarks = 0
        // Read once: URI matching is exact; filename fallback must be unambiguous.
        val library = database.bookDao().observeAll().first()
        val byUri = library.associateBy { it.uri }
        val byFileName = library.groupBy { it.fileName }
        backup.books.forEach { entry ->
            val book = byUri[entry.uri] ?: byFileName[entry.fileName]?.singleOrNull()
            if (book != null) {
                matched++
                entry.shelfKind?.let { kind ->
                    database.bookDao().updateShelfKind(book.id, ShelfKind.fromId(kind).id)
                }
                entry.progress?.let { location ->
                    val payload = json.encodeToString(ReadingLocation.serializer(), location)
                    database.bookDao().updateProgressSummary(book.id, location.percentage, payload)
                    database.progressDao().upsert(
                        com.lightreader.core.data.db.ProgressEntity(
                            bookId = book.id,
                            chapterIndex = location.chapterIndex,
                            charOffset = location.charOffset,
                            pageIndex = location.pageIndex,
                            zoom = location.zoom,
                            scrollY = location.scrollY,
                            percentage = location.percentage,
                            pageCount = location.pageCount,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                entry.bookmarks.forEach { bookmark ->
                    database.bookmarkDao().insert(
                        com.lightreader.core.data.db.BookmarkEntity(
                            bookId = book.id,
                            chapterIndex = bookmark.location.chapterIndex,
                            charOffset = bookmark.location.charOffset,
                            pageIndex = bookmark.location.pageIndex,
                            locationJson = json.encodeToString(
                                ReadingLocation.serializer(),
                                bookmark.location,
                            ),
                            title = bookmark.title,
                            snippet = bookmark.snippet,
                            note = bookmark.note,
                            createdAt = bookmark.createdAt,
                        ),
                    )
                    bookmarks++
                }
                if (book.author == null && !entry.author.isNullOrBlank()) {
                    database.bookDao().updateMetadata(book.id, book.title, entry.author)
                }
            }
        }
        val settings = backup.settings
        if (settings != null) {
            settingsStore.replace(settings.toSettings())
        }
        Logs.d("导入数据：匹配 $matched 本书，$bookmarks 条书签")
        return RestoreStats(matched, bookmarks, settings != null)
    }

    private fun decodeLocation(payload: String): ReadingLocation = runCatching {
        json.decodeFromString(ReadingLocation.serializer(), payload)
    }.getOrDefault(ReadingLocation())

}

private fun AppSettings.toBackup(): BackupSettings = BackupSettings(
    fontSizeSp = reader.fontSizeSp,
    lineHeightMultiplier = reader.lineHeightMultiplier,
    letterSpacingEm = reader.letterSpacingEm,
    paragraphSpacingEm = reader.paragraphSpacingEm,
    marginDp = reader.marginDp,
    alignment = reader.alignment.id,
    indentMode = reader.indentMode.id,
    useSerif = reader.useSerif,
    theme = reader.theme.id,
    readingMode = reader.readingMode.id,
    pageFit = reader.pageFit.id,
    dualPage = reader.dualPage,
    libraryLayout = library.layout.id,
    librarySort = library.sort.id,
    showAuthor = library.showAuthor,
    showProgress = library.showProgress,
    gridColumns = library.gridColumns,
    comicGridColumns = library.comicGridColumns,
)

private fun BackupSettings.toSettings(): AppSettings = AppSettings(
    reader = ReaderSettings(
        fontSizeSp = fontSizeSp,
        lineHeightMultiplier = lineHeightMultiplier,
        letterSpacingEm = letterSpacingEm,
        paragraphSpacingEm = paragraphSpacingEm,
        marginDp = marginDp,
        alignment = TextAlignment.fromId(alignment),
        indentMode = com.lightreader.core.model.IndentMode.fromId(indentMode),
        useSerif = useSerif,
        theme = ThemePreference.fromId(theme),
        readingMode = ReadingMode.fromId(readingMode),
        pageFit = PageFit.fromId(pageFit),
        dualPage = dualPage,
    ),
    library = com.lightreader.core.model.LibrarySettings(
        layout = LibraryLayout.fromId(libraryLayout),
        sort = LibrarySort.fromId(librarySort),
        showAuthor = showAuthor,
        showProgress = showProgress,
        gridColumns = gridColumns.coerceIn(0, 6),
        comicGridColumns = comicGridColumns.coerceIn(0, 6),
    ),
)
