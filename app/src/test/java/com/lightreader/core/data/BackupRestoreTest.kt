package com.lightreader.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lightreader.core.data.db.AppDatabase
import com.lightreader.core.data.db.BookEntity
import com.lightreader.core.model.ReadingLocation
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreTest {
    private lateinit var database: AppDatabase
    private lateinit var preferences: TestPreferenceStore
    private lateinit var repository: DataTransferRepository
    private val fullReads = AtomicInteger()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = TestPreferenceStore(context)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ ->
                if (sql.trim().equals("SELECT * FROM books", ignoreCase = true)) fullReads.incrementAndGet()
            }, Executor { it.run() })
            .build()
        repository = DataTransferRepository(context, database, preferences.settings)
    }

    @After fun tearDown() { preferences.close(); database.close() }

    private suspend fun insert(uri: String, file: String): Long = database.bookDao().insert(
        BookEntity(uri = uri, fileName = file, title = file, author = null, format = "epub",
            mimeType = null, fileSize = 1, coverPath = null, addedAt = 1, lastOpenedAt = null,
            progressPercent = 0f, lastLocationJson = null),
    )

    private fun backup(uri: String, name: String) = BackupBook(uri = uri, fileName = name,
        title = name, format = "epub", progress = ReadingLocation(percentage = 0.5f))

    @Test fun `many unmatched backup entries read the library only once`() = runTest {
        insert("content://present/1", "present.epub")
        fullReads.set(0)
        val result = repository.applyBackup(BackupFile(books = (1..100).map {
            backup("content://missing/$it", "missing-$it.epub")
        }))
        assertEquals(0, result.matchedBooks)
        assertEquals(1, fullReads.get())
    }

    @Test fun `a unique filename restores a moved book`() = runTest {
        val id = insert("content://new/location", "book.epub")
        val result = repository.applyBackup(BackupFile(books = listOf(backup("content://old/location", "book.epub"))))
        assertEquals(1, result.matchedBooks)
        assertEquals(0.5f, database.progressDao().find(id)!!.percentage)
    }

    @Test fun `ambiguous filenames are skipped while exact URI still wins`() = runTest {
        val a = insert("content://books/a", "same.epub")
        val b = insert("content://books/b", "same.epub")
        val missing = repository.applyBackup(BackupFile(books = listOf(backup("content://old/unknown", "same.epub"))))
        assertEquals(0, missing.matchedBooks)
        assertNull(database.progressDao().find(a))
        assertNull(database.progressDao().find(b))
        val exact = repository.applyBackup(BackupFile(books = listOf(backup("content://books/b", "same.epub"))))
        assertEquals(1, exact.matchedBooks)
        assertNull(database.progressDao().find(a))
        assertEquals(0.5f, database.progressDao().find(b)!!.percentage)
    }
}
