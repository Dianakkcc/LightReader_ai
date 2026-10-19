package com.lightreader.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.data.db.AppDatabase
import com.lightreader.core.model.ReadingLocation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ReadingRepository
    private lateinit var preferences: TestPreferenceStore
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        preferences = TestPreferenceStore(context)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReadingRepository(
            context = context,
            database = database,
            workspace = BookWorkspace(context),
            settingsStore = preferences.settings,
        )
    }

    @After
    fun tearDown() {
        preferences.close()
        database.close()
    }

    @Test
    fun `progress keeps chapter offset and percentage`() = runTest {
        repository.saveProgress(
            bookId = 1L,
            location = ReadingLocation(
                chapterIndex = 4,
                charOffset = 2048,
                percentage = 0.37f,
            ),
        )

        val loaded = repository.loadProgress(1L)
        assertEquals(4, loaded.chapterIndex)
        assertEquals(2048, loaded.charOffset)
        assertEquals(0.37f, loaded.percentage, 0.0001f)

        val summary = database.bookDao().find(1L)
        assertEquals(null, summary)
    }

    @Test
    fun `progress for a paged document keeps page and zoom`() = runTest {
        repository.saveProgress(
            bookId = 2L,
            location = ReadingLocation(pageIndex = 12, zoom = 1.5f, percentage = 0.5f, pageCount = 24),
        )
        val loaded = repository.loadProgress(2L)
        assertEquals(12, loaded.pageIndex)
        assertEquals(1.5f, loaded.zoom, 0.0001f)
        assertEquals(24, loaded.pageCount)
        assertTrue(loaded.percentage > 0.4f)
    }

    @Test
    fun `bookmarks can be added listed and removed`() = runTest {
        val id = repository.addBookmark(
            bookId = 5L,
            location = ReadingLocation(chapterIndex = 2, charOffset = 100, percentage = 0.2f),
            title = "第二章",
            snippet = "……片段……",
            note = "备注",
        )
        var bookmarks = repository.observeBookmarks(5L).first()
        assertEquals(1, bookmarks.size)
        assertEquals("第二章", bookmarks.first().title)

        repository.deleteBookmark(id)
        bookmarks = repository.observeBookmarks(5L).first()
        assertTrue(bookmarks.isEmpty())
    }

    @Test
    fun `clearing history removes progress`() = runTest {
        repository.saveProgress(9L, ReadingLocation(chapterIndex = 1, percentage = 0.1f))
        repository.clearHistory()
        val loaded = repository.loadProgress(9L)
        assertEquals(0, loaded.chapterIndex)
        assertEquals(0f, loaded.percentage, 0.0001f)
    }
}
