package com.lightreader.core.format

import com.lightreader.core.model.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMathTest {

    private val chapters = listOf(
        ChapterRef(0, "一", 0, 100),
        ChapterRef(1, "二", 0, 300),
        ChapterRef(2, "三", 0, 100),
    )

    @Test
    fun `start of book is zero percent`() {
        assertEquals(0f, ProgressMath.percent(chapters, 0, 0f), 0.0001f)
    }

    @Test
    fun `position accounts for chapter weight`() {
        // 100 of 500 characters done, then halfway through the 300 character chapter.
        val value = ProgressMath.percent(chapters, 1, 0.5f)
        assertEquals((100 + 150) / 500f, value, 0.0001f)
    }

    @Test
    fun `end of book is one hundred percent`() {
        assertEquals(1f, ProgressMath.percent(chapters, 2, 1f), 0.0001f)
    }

    @Test
    fun `chapter lookup round trips`() {
        val (index, _) = ProgressMath.chapterAt(chapters, 0.5f)
        assertEquals(1, index)
        val (first, _) = ProgressMath.chapterAt(chapters, 0f)
        assertEquals(0, first)
        val (last, _) = ProgressMath.chapterAt(chapters, 1f)
        assertEquals(2, last)
    }

    @Test
    fun `empty chapter list is safe`() {
        assertTrue(ProgressMath.percent(emptyList(), 0, 0.5f) == 0f)
        assertEquals(0 to 0, ProgressMath.chapterAt(emptyList(), 0.5f))
    }
}
