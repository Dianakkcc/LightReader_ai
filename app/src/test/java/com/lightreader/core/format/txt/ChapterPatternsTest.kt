package com.lightreader.core.format.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterPatternsTest {

    @Test
    fun `matches chinese chapters`() {
        assertEquals(2, ChapterPatterns.headingLevel("第一章 初见"))
        assertEquals(2, ChapterPatterns.headingLevel("第 12 节"))
        assertEquals(2, ChapterPatterns.headingLevel("序章"))
        assertEquals(2, ChapterPatterns.headingLevel("番外一"))
    }

    @Test
    fun `matches english chapters`() {
        assertEquals(2, ChapterPatterns.headingLevel("Chapter 3"))
        assertEquals(2, ChapterPatterns.headingLevel("CHAPTER IV - The Sea"))
        assertEquals(2, ChapterPatterns.headingLevel("Prologue"))
    }

    @Test
    fun `does not match normal prose`() {
        assertNull(ChapterPatterns.headingLevel("他走进房间，发现桌上放着一本书。"))
        assertNull(ChapterPatterns.headingLevel("第二章讲述的是主人公的成长历程，这一章非常长并且包含了很多细节描写。"))
        assertNull(ChapterPatterns.headingLevel(""))
    }
}
