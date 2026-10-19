package com.lightreader.core.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.lightreader.core.data.db.AppDatabase
import com.lightreader.core.data.db.BookmarkEntity
import com.lightreader.core.data.db.FontEntity
import com.lightreader.core.data.db.ProgressEntity
import com.lightreader.core.model.Bookmark
import com.lightreader.core.model.FontItem
import com.lightreader.core.model.ReadingLocation
import com.lightreader.core.util.FontNames
import com.lightreader.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reading state: where the user stopped, what they bookmarked, and which fonts they imported.
 *
 * Progress is a rich location (chapter + character offset / page + zoom), never just a
 * percentage, so the exact spot survives font size changes and rotation.
 */
class ReadingRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val workspace: BookWorkspace,
    private val settingsStore: SettingsStore,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeProgress(bookId: Long): Flow<ReadingLocation> =
        database.progressDao().observe(bookId).map { entity ->
            entity?.let { decodeLocation(it) } ?: ReadingLocation()
        }

    suspend fun loadProgress(bookId: Long): ReadingLocation {
        val entity = database.progressDao().find(bookId) ?: return ReadingLocation()
        return decodeLocation(entity)
    }

    suspend fun saveProgress(bookId: Long, location: ReadingLocation, snippet: String? = null) {
        database.progressDao().upsert(
            ProgressEntity(
                bookId = bookId,
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
        database.bookDao().updateProgressSummary(
            id = bookId,
            percentage = location.percentage,
            location = json.encodeToString(ReadingLocation.serializer(), location),
        )
    }

    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> =
        database.bookmarkDao().observeForBook(bookId).map { list -> list.map { it.toModel() } }

    fun observeAllBookmarks(): Flow<List<Bookmark>> =
        database.bookmarkDao().observeAll().map { list -> list.map { it.toModel() } }

    suspend fun addBookmark(
        bookId: Long,
        location: ReadingLocation,
        title: String,
        snippet: String,
        note: String? = null,
    ): Long = database.bookmarkDao().insert(
        BookmarkEntity(
            bookId = bookId,
            chapterIndex = location.chapterIndex,
            charOffset = location.charOffset,
            pageIndex = location.pageIndex,
            locationJson = json.encodeToString(ReadingLocation.serializer(), location),
            title = title,
            snippet = snippet,
            note = note,
            createdAt = System.currentTimeMillis(),
        ),
    )

    suspend fun deleteBookmark(id: Long) = database.bookmarkDao().delete(id)

    suspend fun deleteBookmarkNear(bookId: Long, location: ReadingLocation) =
        database.bookmarkDao().deleteNear(
            bookId = bookId,
            chapterIndex = location.chapterIndex,
            pageIndex = location.pageIndex,
            charOffset = location.charOffset,
        )

    suspend fun clearHistory() {
        database.progressDao().deleteAll()
        database.bookDao().clearReadingHistory()
    }

    // ---------------------------------------------------------------- fonts

    fun observeFonts(): Flow<List<FontItem>> =
        database.fontDao().observeAll().map { list -> list.map { it.toModel() } }

    suspend fun findDefaultFont(): FontItem? = database.fontDao().findDefault()?.toModel()

    suspend fun findFont(id: Long): FontItem? = database.fontDao().find(id)?.toModel()

    /** Makes sure a shipped font is available on disk before it is used. */
    suspend fun prepareFont(item: FontItem): FontItem {
        if (!item.isBuiltin) return item
        BuiltinFonts.materialize(context, item.fileName, File(item.path))
        return item
    }

    /**
     * Imports a font file into app private storage. The file is validated first: a font that
     * cannot be loaded would otherwise crash the reading surface.
     */
    suspend fun importFont(uri: Uri): FontItem? = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "font"
        val extension = name.substringAfterLast('.', "ttf").lowercase()
            .takeIf { it in setOf("ttf", "otf", "ttc", "otc") } ?: "ttf"
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null

        if (!FontNames.looksLikeFont(bytes)) {
            Logs.w("字体文件头不合法：$name")
            return@withContext null
        }
        val displayName = FontNames.familyName(bytes) ?: name.substringBeforeLast('.')
        val id = database.fontDao().insert(
            FontEntity(
                displayName = displayName,
                fileName = name,
                path = "",
                addedAt = System.currentTimeMillis(),
            ),
        )
        val target = File(workspace.fontsDir, "$id.$extension")
        target.writeBytes(bytes)
        val valid = runCatching { Typeface.createFromFile(target) != null }.getOrDefault(false)
        if (!valid) {
            target.delete()
            database.fontDao().delete(id)
            Logs.w("字体无法被系统解析：$displayName")
            return@withContext null
        }
        val stored = FontEntity(
            id = id,
            displayName = displayName,
            fileName = name,
            path = target.absolutePath,
            addedAt = System.currentTimeMillis(),
            isDefault = false,
        )
        database.fontDao().updateFont(stored)
        stored.toModel()
    }

    suspend fun setDefaultFont(fontId: Long?) {
        database.fontDao().clearDefaults()
        if (fontId != null) database.fontDao().markDefault(fontId)
    }

    suspend fun deleteFont(item: FontItem) {
        database.fontDao().delete(item.id)
        if (item.isBuiltin) settingsStore.markBuiltinRemoved(item.fileName)
        runCatching { File(item.path).delete() }
    }

    // ------------------------------------------------------------- helpers

    private fun decodeLocation(entity: ProgressEntity): ReadingLocation =
        ReadingLocation(
            chapterIndex = entity.chapterIndex,
            charOffset = entity.charOffset,
            pageIndex = entity.pageIndex,
            zoom = entity.zoom,
            scrollY = entity.scrollY,
            percentage = entity.percentage,
            pageCount = entity.pageCount,
        )

    private fun BookmarkEntity.toModel(): Bookmark = Bookmark(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        pageIndex = pageIndex,
        locationJson = locationJson,
        title = title,
        snippet = snippet,
        note = note,
        createdAt = createdAt,
    )

    private fun FontEntity.toModel(): FontItem = FontItem(
        id = id,
        displayName = displayName,
        fileName = fileName,
        path = path,
        addedAt = addedAt,
        isBuiltin = isBuiltin,
    )
}
