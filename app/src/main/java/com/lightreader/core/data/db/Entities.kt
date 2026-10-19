package com.lightreader.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["lastOpenedAt"])],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val title: String,
    val author: String?,
    val format: String,
    /** Shelf the entry lives on: "book" or "comic" (see ShelfKind). */
    @ColumnInfo(defaultValue = "book")
    val shelfKind: String = "book",
    val mimeType: String?,
    val fileSize: Long,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progressPercent: Float,
    val lastLocationJson: String?,
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: Long,
    val chapterIndex: Int,
    val charOffset: Int,
    val pageIndex: Int,
    val zoom: Float,
    val scrollY: Int,
    val percentage: Float,
    val pageCount: Int,
    val updatedAt: Long,
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId"])])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(tableName = "fonts")
data class FontEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val fileName: String,
    val path: String,
    val addedAt: Long,
    val isDefault: Boolean = false,
    val isBuiltin: Boolean = false,
)
