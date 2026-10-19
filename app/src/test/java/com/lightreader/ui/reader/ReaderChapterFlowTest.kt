package com.lightreader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lightreader.TestViewModels
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import com.lightreader.AppContainer
import com.lightreader.core.data.TestPreferenceStore
import com.lightreader.core.data.db.BookEntity
import com.lightreader.core.model.TocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The chapter flow that users actually complained about: read a chapter to its end and the next one
 * has to follow on its own. This drives the real ViewModel over a real TXT file, so it covers the
 * whole chain - engine event, bridge, ViewModel, prefetch, next chapter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderChapterFlowTest {

    // Unconfined so the ViewModel's own coroutines progress eagerly while the test waits for real
    // file IO (the parsers run on Dispatchers.IO).
    private val owned = TestViewModels()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var container: AppContainer
    private lateinit var preferences: TestPreferenceStore

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** State transitions, so a timeout reports what actually happened instead of only the end state. */
    private val transitions = java.util.Collections.synchronizedList(ArrayList<String>())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = TestPreferenceStore(context)
        container = AppContainer(context, installBuiltinFonts = false, settingsStoreOverride = preferences.settings)
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
    fun `reaching the end of a chapter opens the next one`() = runTest {
        val bookId = createBook()
        val viewModel = owned.own(ReaderViewModel(container, bookId))
        watch(viewModel)

        val opened = awaitState(viewModel, "打开第一章") { it.chapter != null }
        assertEquals(0, opened.chapterIndex)

        // A TXT file starts with provisional chunks and indexes itself in the background; the
        // chapter list (and therefore the "there is a next chapter" flag) settles afterwards.
        val indexed = awaitState(viewModel, "章节索引完成") {
            !it.analysingToc && it.chapters.size >= 3
        }
        assertTrue(
            "索引完成后应当知道后面还有章节（chapters=${indexed.chapters.size}，" +
                "hasNext=${indexed.hasNextChapter}）",
            indexed.hasNextChapter,
        )

        // This is what the reading engine calls when the reader pulls past the last line.
        viewModel.onReachEnd()

        val next = awaitState(viewModel, "切到第 2 章") { it.chapterIndex == 1 && it.chapter != null }
        assertEquals(1, next.chapterIndex)
        assertTrue("第 2 章内容应当已加载", next.chapter!!.html.contains("第2章"))
    }

    @Test
    fun `a continue request made while the index is still running is not lost`() = runTest {
        val bookId = createBook()
        val viewModel = owned.own(ReaderViewModel(container, bookId))
        watch(viewModel)
        awaitState(viewModel, "打开第一章") { it.chapter != null }

        // The reader reaches the end while the TXT index is still building: the request is queued
        // and honoured as soon as the real chapter list is known.
        viewModel.onReachEnd()
        awaitState(viewModel, "索引完成前请求续读也要切到第 2 章") { it.chapterIndex == 1 }
    }

    @Test
    fun `the last chapter does not try to continue`() = runTest {
        val bookId = createBook()
        val viewModel = owned.own(ReaderViewModel(container, bookId))
        watch(viewModel)
        awaitState(viewModel, "打开第一章") { it.chapter != null }
        // Let the provisional chunk list settle into real chapters first, so every continue request
        // below maps to exactly one chapter.
        awaitState(viewModel, "章节索引完成") { !it.analysingToc && it.chapters.size >= 3 }

        viewModel.onReachEnd()
        awaitState(viewModel, "切到第 2 章") { it.chapterIndex == 1 }

        viewModel.onReachEnd()
        awaitState(viewModel, "切到第 3 章") { it.chapterIndex == 2 }

        // Third chapter is the last one: continuing must be a no-op, not an error.
        viewModel.onReachEnd()
        delay(600)
        val state = viewModel.state.value
        assertEquals(2, state.chapterIndex)
    }

    /**
     * A comic archive must read as one continuous vertical flow (webtoon style), not as a page
     * by page image viewer: the chapter folders are separators inside the flow, so scrolling from
     * the last page of one chapter into the first page of the next never stops the reader.
     */
    @Test
    fun `a comic archive reads as one continuous flow across its chapter folders`() = runTest {
        val bookId = createComicBook()
        val viewModel = owned.own(ReaderViewModel(container, bookId))
        watch(viewModel)

        val opened = awaitState(viewModel, "打开漫画") { it.isComicFlow && it.pageCount == 6 }
        assertEquals(6, opened.pageCount)
        // "第1话" is pages 1-3, "第2话" starts at page 4: the separator titles of the flow.
        assertEquals(mapOf(0 to "第1话", 3 to "第2话"), opened.comicChapterAtPage)
        assertEquals(
            "页面比例要在解码前就知道（连续流要靠它预留高度，否则滚动会跳）",
            8f / 12f,
            viewModel.pagedPageAspect(0),
            0.01f,
        )

        // The table of contents jumps to the first page of the chapter, inside the same flow.
        viewModel.goToTocEntry(TocEntry(title = "第2话", chapterIndex = 3))
        val jumped = awaitState(viewModel, "目录跳到第 2 话") { it.pageIndex == 3 }
        assertTrue("目录跳转之后仍然是连续流", jumped.isComicFlow)
    }

    /**
     * In the continuous flow a chapter is a page range, so "which chapter am I in" is derived from
     * the page - the table of contents highlights it and the top bar names it.
     */
    @Test
    fun `a comic chapter is derived from the current page`() {
        val chapters = mapOf(0 to "第1话", 3 to "第2话", 8 to "第3话")
        assertEquals(0, comicChapterStartFor(0, chapters))
        assertEquals(0, comicChapterStartFor(2, chapters))
        assertEquals(3, comicChapterStartFor(3, chapters))
        assertEquals(3, comicChapterStartFor(7, chapters))
        assertEquals(8, comicChapterStartFor(9, chapters))
        assertEquals(0, comicChapterStartFor(5, emptyMap()))
    }

    /** Waits on the real clock: the document work happens on real IO threads. */
    private fun watch(viewModel: ReaderViewModel) {
        container.appScope.launch {
            viewModel.state.collect { state ->
                transitions += "idx=${state.chapterIndex} ch=${state.chapters.size} " +
                    "hasNext=${state.hasNextChapter} toc=${state.analysingToc} " +
                    "loading=${state.loading} err=${state.errorMessage != null}"
            }
        }
    }

    private suspend fun awaitState(
        viewModel: ReaderViewModel,
        description: String,
        predicate: (ReaderViewModel.UiState) -> Boolean,
    ): ReaderViewModel.UiState = withContext(Dispatchers.Default) {
        try {
            withTimeout(30_000) { viewModel.state.first(predicate) }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            val state = viewModel.state.value
            throw AssertionError(
                "等待「$description」超时：chapterIndex=${state.chapterIndex} " +
                    "chapters=${state.chapters.size} hasNext=${state.hasNextChapter} " +
                    "analysingToc=${state.analysingToc} loading=${state.loading} " +
                    "error=${state.errorMessage} pages=${state.pageCount}\n" +
                    "状态变迁：\n" + transitions.takeLast(24).joinToString("\n") { "  $it" },
                timeout,
            )
        }
    }

    private suspend fun createBook(): Long {
        val file = File(context.cacheDir, "flow-test.txt")
        file.writeText(
            buildString {
                repeat(3) { index ->
                    append("第${index + 1}章 第${index + 1}节\n")
                    append("这是第${index + 1}章的正文。")
                    append("用于验证章末自动续读的内容填充。".repeat(40))
                    append("\n\n")
                }
            },
            Charsets.UTF_8,
        )
        return container.database.bookDao().insert(
            BookEntity(
                uri = Uri.fromFile(file).toString(),
                fileName = file.name,
                title = "续读测试",
                author = null,
                format = "txt",
                mimeType = "text/plain",
                fileSize = file.length(),
                coverPath = null,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
                progressPercent = 0f,
                lastLocationJson = null,
            ),
        )
    }

    /** Two folders, three pages each - the shape a manga/comic archive really has. */
    private suspend fun createComicBook(): Long {
        val file = File(context.cacheDir, "flow-test.cbz")
        ZipOutputStream(file.outputStream()).use { zip ->
            listOf("第1话" to 3, "第2话" to 3).forEach { (folder, pages) ->
                repeat(pages) { index ->
                    zip.putNextEntry(ZipEntry("$folder/${index + 1}.png"))
                    zip.write(png(8, 12))
                    zip.closeEntry()
                }
            }
        }
        return container.database.bookDao().insert(
            BookEntity(
                uri = Uri.fromFile(file).toString(),
                fileName = file.name,
                title = "漫画流测试",
                author = null,
                format = "cbz",
                mimeType = "application/vnd.comicbook+zip",
                fileSize = file.length(),
                coverPath = null,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
                progressPercent = 0f,
                lastLocationJson = null,
            ),
        )
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
