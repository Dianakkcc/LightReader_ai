package com.lightreader.ui.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The comic flow is one long `LazyColumn`, and a `LazyColumn` only takes its start page on first
 * composition. Jumping from the table of contents (or the progress bar) changes the page from the
 * outside, so the flow has to follow: otherwise the page counter and percentage move while the
 * strip stays where it was - exactly what the reader reported as "点目录不切换".
 */
@RunWith(AndroidJUnit4::class)
class ComicFlowJumpTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    // Android D8 refuses class/method names containing spaces below DEX version 040, so an
    // instrumented test cannot use the backtick-with-spaces style the JVM tests use.
    fun an_external_page_change_scrolls_the_flow_to_that_page() {
        var page by mutableIntStateOf(0)
        rule.setContent {
            MaterialTheme {
                ComicFlowView(
                    pageCount = 6,
                    startPage = page,
                    chapterTitleAt = { index -> if (index == 3) "第2话" else null },
                    background = Color.White,
                    loadPage = { _, _, _ -> null },
                    pageAspect = { 0.7f },
                    onVisiblePage = {},
                    onTapCenter = {},
                    onPageStep = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // The separator belongs to page 4 of 6, which is several screens down: the flow has not
        // composed it yet, and that is the whole point of the next step.
        rule.waitForIdle()
        rule.onNodeWithText("第2话").assertDoesNotExist()

        // What the table of contents / the progress bar does: change the page from the outside.
        rule.runOnIdle { page = 3 }
        rule.waitForIdle()

        rule.onNodeWithText("第2话").assertIsDisplayed()
    }
}
