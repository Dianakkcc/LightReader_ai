package com.lightreader.core.data

import android.content.Context
import android.net.Uri
import com.lightreader.core.data.db.BookDao
import com.lightreader.core.data.db.BookEntity
import com.lightreader.core.data.db.BookmarkDao
import com.lightreader.core.data.db.ProgressDao
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.model.Book
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.ShelfKind
import com.lightreader.core.util.Logs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The shelf: what the user has imported and what happens to those entries. */
class LibraryRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val workspace: BookWorkspace,
    private val coverStore: CoverStore,
    private val importer: BookImporter,
) {

    fun observeBooks(): Flow<List<Book>> = bookDao.observeAll().map { entities ->
        entities.map { it.toBook() }
    }

    fun observeBook(id: Long): Flow<Book?> = bookDao.observe(id).map { it?.toBook() }

    suspend fun findBook(id: Long): Book? = bookDao.find(id)?.toBook()

    suspend fun findByUri(uri: String): Book? = bookDao.findByUri(uri)?.toBook()

    suspend fun import(uris: List<Uri>): BookImporter.Result = importer.import(uris)

    suspend fun markOpened(id: Long) = bookDao.markOpened(id, System.currentTimeMillis())

    suspend fun updateMetadata(id: Long, title: String, author: String?) =
        bookDao.updateMetadata(id, title, author)

    /** Moves a book between the 书籍 and 漫画 shelves (long press on the shelf). */
    suspend fun setShelfKind(bookId: Long, kind: ShelfKind) {
        bookDao.updateShelfKind(bookId, kind.id)
        Logs.d("书架归属改为 ${kind.displayName}：book=$bookId")
    }

    /** Removes the shelf entry, its progress and bookmarks. The original file is untouched. */
    suspend fun removeFromShelf(book: Book) {
        bookmarkDao.deleteForBook(book.id)
        progressDao.deleteForBook(book.id)
        bookDao.delete(book.id)
        coverStore.delete(book.id)
        workspace.dropBookCache(workspace.keyFor(book.uri))
    }

    /**
     * Deletes the file the user imported, but only when the provider allows it. Returns false
     * when the document provider refuses - the UI then tells the user to delete it elsewhere.
     */
    suspend fun deleteLocalFile(book: Book): Boolean {
        val deleted = importer.deleteLocalFile(book.uri)
        Logs.d("删除本地文件 ${book.fileName} → $deleted")
        if (deleted) removeFromShelf(book)
        return deleted
    }

    suspend fun clearReadingHistory() = bookDao.clearReadingHistory()

    fun sourceOf(book: Book): DocumentSource = DocumentSource(
        uri = book.uri,
        displayName = book.fileName,
        sizeBytes = book.fileSize,
        mimeType = book.mimeType,
    )

    private fun BookEntity.toBook(): Book = Book(
        id = id,
        uri = uri,
        fileName = fileName,
        title = title,
        author = author,
        format = BookFormat.fromId(format),
        shelfKind = ShelfKind.fromId(shelfKind),
        mimeType = mimeType,
        fileSize = fileSize,
        coverPath = coverPath,
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        progressPercent = progressPercent,
        lastLocationJson = lastLocationJson,
    )
}
