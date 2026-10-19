package com.lightreader.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun find(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun markOpened(id: Long, timestamp: Long)

    @Query("UPDATE books SET progressPercent = :percentage, lastLocationJson = :location WHERE id = :id")
    suspend fun updateProgressSummary(id: Long, percentage: Float, location: String?)

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, author: String?)

    @Query("UPDATE books SET coverPath = :path WHERE id = :id")
    suspend fun updateCover(id: Long, path: String?)

    @Query("UPDATE books SET shelfKind = :kind WHERE id = :id")
    suspend fun updateShelfKind(id: Long, kind: String)

    @Query("UPDATE books SET lastOpenedAt = NULL, progressPercent = 0, lastLocationJson = NULL")
    suspend fun clearReadingHistory()

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}

@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    fun observe(bookId: Long): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun find(bookId: Long): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM progress")
    suspend fun deleteAll()
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex, charOffset, pageIndex")
    fun observeForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND pageIndex = :pageIndex AND ABS(charOffset - :charOffset) < 32")
    suspend fun deleteNear(bookId: Long, chapterIndex: Int, pageIndex: Int, charOffset: Int)
}

@Dao
interface FontDao {

    @Query("SELECT * FROM fonts ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FontEntity>>

    @Query("SELECT * FROM fonts ORDER BY addedAt DESC")
    suspend fun findAll(): List<FontEntity>

    @Query("SELECT * FROM fonts WHERE isDefault = 1 LIMIT 1")
    suspend fun findDefault(): FontEntity?

    @Query("SELECT * FROM fonts WHERE id = :id")
    suspend fun find(id: Long): FontEntity?

    @Insert
    suspend fun insert(font: FontEntity): Long

    @Update
    suspend fun updateFont(font: FontEntity)

    @Query("DELETE FROM fonts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE fonts SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE fonts SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: Long)
}
