package com.lightreader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reported from a phone: the reader's panel stopped short of the bottom of the screen and left a
 * strip of dimmed backdrop under it. The panel is drawn inside the reading window (not a
 * ModalBottomSheet, which also cost the main thread a few hundred ms to open), and this keeps it
 * flush with the bottom edge.
 */
@RunWith(AndroidJUnit4::class)
class ReaderPanelOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theReaderPanelReachesTheBottomEdge() {
        rule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderPanelOverlay(progress = { 1f }, onDismiss = {}) {
                        Text("阅读设置")
                    }
                }
            }
        }

        rule.onNodeWithText("阅读设置").assertIsDisplayed()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val sheet = rule.onNodeWithTag(ReaderPanelOverlayTag).getUnclippedBoundsInRoot()
        assertEquals(
            "面板底边必须贴住屏幕底边",
            root.bottom.value,
            sheet.bottom.value,
            1f,
        )
        assertTrue(
            "面板应当足够高（现为屏幕高度的 94%）",
            (sheet.bottom.value - sheet.top.value) > (root.bottom.value - root.top.value) * 0.8f,
        )
    }
}
