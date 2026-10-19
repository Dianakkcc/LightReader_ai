package com.lightreader.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lightreader.AppContainer
import com.lightreader.core.data.db.BookEntity
import com.lightreader.core.model.ReaderSettings
import com.lightreader.ui.AppNavHost
import com.lightreader.ui.theme.LightReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import com.lightreader.core.data.TestPreferenceStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Optional captures of actual production Compose screens, using synthetic local fixtures. */
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleaseScreenshotsTest {
    private var preferences: TestPreferenceStore? = null
    private var captureContainer: AppContainer? = null
    @get:Rule(order = 0) val cleanup = object : ExternalResource() {
        override fun after() {
            captureContainer?.let { container ->
                runBlocking { container.appScope.coroutineContext[Job]?.cancelAndJoin() }
                container.database.close()
            }
            preferences?.close()
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val output = File(System.getenv("LR_SCREENSHOT_DIR")!!, "$name.png")
        output.parentFile!!.mkdirs()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().buffered().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
        }
    }

    @Test fun captureLibraryAndSettings() {
        assumeTrue(System.getenv("LR_SCREENSHOT_DIR") != null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = TestPreferenceStore(context).also { preferences = it }
        val container = AppContainer(context, installBuiltinFonts = false, settingsStoreOverride = prefs.settings).also { captureContainer = it }
        runBlocking {
            container.database.bookDao().deleteAll()
            (1..12).forEach { n ->
                container.database.bookDao().insert(BookEntity(
                    uri = "file:///release-fixtures/sample$n.txt",
                    fileName = "sample$n.txt", title = "阅读示例 $n", author = "演示内容",
                    format = if (n <= 4) "txt" else "comic",
                    shelfKind = if (n <= 4) "book" else "comic", mimeType = null,
                    fileSize = 2048L, coverPath = null, addedAt = n.toLong(),
                    lastOpenedAt = null, progressPercent = 0f, lastLocationJson = null,
                ))
            }
        }
        rule.setContent { LightReaderTheme(darkTheme = false) { AppNavHost(container, emptyList(), {}) } }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("阅读示例 1").fetchSemanticsNodes().isNotEmpty() }
        capture("01-library-books")
        rule.onNodeWithContentDescription("切换到漫画书架").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("阅读示例 12").fetchSemanticsNodes().isNotEmpty() }
        capture("02-library-comics")
        rule.onNodeWithContentDescription("调节每行显示数量").performClick()
        rule.onNodeWithText("每行 4 个").performClick()
        rule.onNodeWithText("每行 4 个").assertIsDisplayed()
        capture("03-column-count")
        rule.onNodeWithContentDescription("更多").performClick()
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("应用主题").assertIsDisplayed()
        capture("04-settings")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("漫画每行数量"))
        capture("05-library-settings")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("许可与声明"))
        capture("06-about")
        rule.onNodeWithText("许可与声明").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("本项目免费分发许可").fetchSemanticsNodes().isNotEmpty() }
        capture("07-licenses")
    }

    @Test fun captureReadingSettings() {
        assumeTrue(System.getenv("LR_SCREENSHOT_DIR") != null)
        rule.setContent {
            LightReaderTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    ReaderSettingsPanel(ReaderSettings(), emptyList(), {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("阅读设置").assertIsDisplayed()
        capture("08-reader-settings")
        rule.onNodeWithTag("readerSettingsList").performScrollToNode(hasText("导入字体（.ttf / .otf / .ttc）"))
        capture("09-reader-font-settings")
    }
}
