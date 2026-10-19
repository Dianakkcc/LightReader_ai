package com.lightreader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation smoke test for the shelf. Requires a device or emulator:
 * `gradlew connectedDebugAndroidTest`.
 *
 * This runs at phone width: only search and the overflow menu stay in the header on narrow screens
 * (the moon and the settings icons move into that menu), so importing and settings are reached
 * through it. The test follows the layout the reader actually gets, not the tablet one.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shelfAndImportActionAreVisibleOnStart() {
        rule.onNodeWithText("书架").assertIsDisplayed()
        rule.onNodeWithContentDescription("导入书籍").assertIsDisplayed()
    }

    @Test
    fun settingsCanBeOpenedFromTheShelf() {
        rule.onNodeWithContentDescription("更多").performClick()
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("阅读").assertIsDisplayed()
    }

    /** The 书籍 / 漫画 switch at the very top: the shelf screen follows it. */
    @Test
    fun theShelfSwitchChangesShelves() {
        rule.onNodeWithContentDescription("切换到书籍书架").performClick()
        rule.onNodeWithContentDescription("切换到书籍书架").assertIsSelected()

        rule.onNodeWithContentDescription("切换到漫画书架").performClick()
        rule.onNodeWithContentDescription("切换到漫画书架").assertIsSelected()
        rule.onNodeWithText("书架").assertIsDisplayed()
    }

    @Test
    fun comicColumnCountCanBeChangedAndSurvivesRecreation() {
        rule.onNodeWithContentDescription("切换到漫画书架").performClick()
        rule.onNodeWithContentDescription("调节每行显示数量").performClick()
        rule.onNodeWithText("每行 4 个").performClick()
        rule.onNodeWithText("每行 4 个").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("每行 4 个")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("每行 4 个").assertIsDisplayed()
    }
}
