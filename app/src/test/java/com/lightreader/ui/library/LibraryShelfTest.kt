package com.lightreader.ui.library

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lightreader.TestViewModels
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import com.lightreader.AppContainer
import com.lightreader.core.data.db.BookEntity
import com.lightreader.core.model.ShelfKind
import com.lightreader.core.data.TestPreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The library is two shelves behind one switch. This drives the real ViewModel and the real
 * database: what lands where on import, what the switch filters, and what the long press action
 * does to an entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryShelfTest {

    private val owned = TestViewModels()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var container: AppContainer
    private lateinit var preferences: TestPreferenceStore

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = TestPreferenceStore(context)
        container = AppContainer(
            context, installBuiltinFonts = false,
            settingsStoreOverride = preferences.settings,
        )
    }

    @After
    fun tearDown() {
        owned.close()
        runBlocking { container.appScope.coroutineContext[Job]?.cancelAndJoin() }
        container.database.close()
        preferences.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `the switch filters the shelf and the long press action moves a book`() = runTest {
        insertBook("comic.cbz", "漫画书", "comic")
        insertBook("novel.epub", "小说", "epub")
        val viewModel = owned.own(LibraryViewModel(container))

        val loaded = awaitState(viewModel) { it.books.size == 2 }
        // Text format on the book shelf, archive on the comic shelf, decided at import time.
        assertEquals(
            mapOf("comic.cbz" to ShelfKind.COMIC, "novel.epub" to ShelfKind.BOOK),
            loaded.books.associate { it.fileName to it.shelfKind },
        )
        assertEquals(listOf("小说"), loaded.filtered.map { it.title })
        assertEquals(1, loaded.countOn(ShelfKind.BOOK))
        assertEquals(1, loaded.countOn(ShelfKind.COMIC))

        viewModel.setShelf(ShelfKind.COMIC)
        val onComicShelf = awaitState(viewModel) {
            it.shelf == ShelfKind.COMIC && it.filtered.size == 1
        }
        assertEquals(listOf("漫画书"), onComicShelf.filtered.map { it.title })
        waitFor("书架模式要记住") {
            container.settingsStore.settings.first { it.library.shelf == ShelfKind.COMIC }
        }

        // Long press → 移到书籍书架: the entry leaves this shelf and shows up on the other one.
        val comic = onComicShelf.filtered.first()
        viewModel.setBookShelfKind(comic, ShelfKind.BOOK)
        val moved = awaitState(viewModel) { it.filtered.isEmpty() && it.books.size == 2 }
        assertEquals(0, moved.countOn(ShelfKind.COMIC))
        assertEquals(setOf("小说", "漫画书"), moved.books.map { it.title }.toSet())
    }

    private suspend fun insertBook(fileName: String, title: String, format: String) {
        container.database.bookDao().insert(
            BookEntity(
                uri = Uri.parse("content://test/$fileName").toString(),
                fileName = fileName,
                title = title,
                author = null,
                format = format,
                shelfKind = ShelfKind.defaultFor(
                    com.lightreader.core.model.BookFormat.fromId(format),
                ).id,
                mimeType = null,
                fileSize = 1024,
                coverPath = null,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
                progressPercent = 0f,
                lastLocationJson = null,
            ),
        )
    }

    @Test
    fun `each shelf remembers its column count across new view models`() = runTest {
        // 用不同于 UiState 默认值的列数确认首份设置已被消费，避免只等待数据库加载。
        container.settingsStore.replace(
            com.lightreader.core.model.AppSettings(
                library = com.lightreader.core.model.LibrarySettings(gridColumns = 2),
            ),
        )
        val viewModel = owned.own(LibraryViewModel(container))
        awaitState(viewModel) { !it.loading && it.gridColumns == 2 && it.shelf == ShelfKind.BOOK }
        viewModel.setShelf(ShelfKind.BOOK)
        viewModel.setColumns(5)
        waitFor("书籍列数保存") {
            container.settingsStore.settings.first { it.library.gridColumns == 5 }
        }
        viewModel.setShelf(ShelfKind.COMIC)
        viewModel.setColumns(4)
        waitFor("漫画列数保存") {
            container.settingsStore.settings.first {
                it.library.comicGridColumns == 4 && it.library.shelf == ShelfKind.COMIC
            }
        }
        val reopened = owned.own(LibraryViewModel(container))
        val restored = awaitState(reopened) {
            it.gridColumns == 5 && it.comicGridColumns == 4 && it.shelf == ShelfKind.COMIC
        }
        assertEquals(4, restored.selectedColumns)
        reopened.setShelf(ShelfKind.BOOK)
        assertEquals(5, reopened.state.value.selectedColumns)
        container.settingsStore.replace(com.lightreader.core.model.AppSettings())
    }

    /**
     * Waits on the real clock: the database and DataStore work happens on real IO threads, which
     * `runTest`'s virtual time cannot advance.
     */
    private suspend fun awaitState(
        viewModel: LibraryViewModel,
        predicate: (LibraryViewModel.UiState) -> Boolean,
    ): LibraryViewModel.UiState = waitFor("界面状态") { viewModel.state.first(predicate) }

    private suspend fun <T> waitFor(what: String, block: suspend () -> T): T =
        withContext(Dispatchers.Default) {
            try {
                withTimeout(30_000) { block() }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError("等待「$what」超时", timeout)
            }
        }
}
