package com.lightreader.ui.reader

import android.os.Looper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import com.lightreader.core.model.ReaderSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ReaderPanelPerformanceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun panelAnimationDoesNotRecomposePanelContentsEachFrame() {
        var target by mutableFloatStateOf(0f)
        val compositions = AtomicInteger()
        rule.setContent {
            MaterialTheme {
                val progress = animateFloatAsState(target, tween(220), label = "testPanel")
                Box(Modifier.fillMaxSize()) {
                    ReaderPanelOverlay(progress = { progress.value }, onDismiss = {}) {
                        SideEffect { compositions.incrementAndGet() }
                        Text("动画测试")
                    }
                }
            }
        }
        rule.waitForIdle()
        val before = compositions.get()
        rule.runOnIdle { target = 1f }
        rule.waitForIdle()
        rule.onNodeWithText("动画测试").assertIsDisplayed()
        assertTrue("动画期间内容不应每帧重组", compositions.get() - before <= 2)
    }

    @Test
    fun settingsCanScrollToFontControls() {
        rule.setContent {
            MaterialTheme {
                ReaderSettingsPanel(ReaderSettings(), emptyList(), {}, {}, {}, {})
            }
        }
        rule.onNodeWithTag("readerSettingsList")
            .performScrollToNode(hasText("导入字体（.ttf / .otf / .ttc）"))
        rule.onNodeWithText("导入字体（.ttf / .otf / .ttc）").assertIsDisplayed()
    }

    @Test
    fun comicPageHeadersAreReadOffTheMainThread() {
        val called = AtomicBoolean()
        val onMain = AtomicBoolean()
        rule.setContent {
            MaterialTheme {
                ComicFlowView(
                    pageCount = 3, startPage = 0, chapterTitleAt = { null },
                    background = Color.White,
                    loadPage = { _, _, _ -> null },
                    pageAspect = {
                        onMain.set(Looper.myLooper() == Looper.getMainLooper())
                        called.set(true)
                        0.7f
                    },
                    onVisiblePage = {}, onTapCenter = {}, onPageStep = {},
                )
            }
        }
        rule.waitUntil(5_000) { called.get() }
        assertFalse("读取图片头不能占用主线程", onMain.get())
    }
}
