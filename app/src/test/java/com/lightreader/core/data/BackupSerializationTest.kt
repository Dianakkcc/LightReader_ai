package com.lightreader.core.data

import com.lightreader.core.model.ReadingLocation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `backup survives a round trip`() {
        val backup = BackupFile(
            version = BackupFile.CURRENT_VERSION,
            exportedAt = 1_700_000_000_000L,
            books = listOf(
                BackupBook(
                    uri = "content://book/1",
                    fileName = "book.epub",
                    title = "测试书",
                    author = "作者",
                    format = "epub",
                    progress = ReadingLocation(chapterIndex = 3, charOffset = 128, percentage = 0.25f),
                    bookmarks = listOf(
                        BackupBookmark(
                            location = ReadingLocation(chapterIndex = 1, charOffset = 42),
                            title = "第一章",
                            snippet = "……内容……",
                            note = "备注",
                            createdAt = 1_700_000_000_001L,
                        ),
                    ),
                ),
            ),
            settings = BackupSettings(
                fontSizeSp = 20f,
                lineHeightMultiplier = 1.7f,
                letterSpacingEm = 0f,
                paragraphSpacingEm = 0.6f,
                marginDp = 24,
                alignment = "start",
                theme = "sepia",
                readingMode = "paged",
                pageFit = "width",
                dualPage = false,
                libraryLayout = "grid",
                librarySort = "recent",
                showAuthor = true,
                showProgress = true,
            ),
        )

        val encoded = json.encodeToString(BackupFile.serializer(), backup)
        assertTrue(encoded.contains("\"version\":1"))
        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)
        assertEquals(backup, decoded)
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val payload = """{"version":2,"exportedAt":1,"books":[],"future":"value"}"""
        val decoded = json.decodeFromString(BackupFile.serializer(), payload)
        assertEquals(2, decoded.version)
        assertTrue(decoded.books.isEmpty())
    }
}
