package com.lightreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which shelf a freshly imported file lands on: text formats are books, comic archives are comics,
 * and the formats that could be either (PDF, images, HTML) start as books and are moved by hand.
 */
class ShelfKindTest {

    @Test
    fun `comic archives are comics and text formats are books`() {
        assertEquals(ShelfKind.COMIC, ShelfKind.defaultFor(BookFormat.COMIC))
        listOf(
            BookFormat.EPUB,
            BookFormat.TXT,
            BookFormat.PDF,
            BookFormat.DOCX,
            BookFormat.RTF,
            BookFormat.HTML,
            BookFormat.MARKDOWN,
            BookFormat.IMAGE,
        ).forEach { format ->
            assertEquals("$format 默认应进书籍书架", ShelfKind.BOOK, ShelfKind.defaultFor(format))
        }
    }

    @Test
    fun `an unknown stored value falls back to the book shelf`() {
        assertEquals(ShelfKind.BOOK, ShelfKind.fromId(null))
        assertEquals(ShelfKind.BOOK, ShelfKind.fromId("nonsense"))
        assertEquals(ShelfKind.COMIC, ShelfKind.fromId("comic"))
        assertEquals("book", ShelfKind.BOOK.id)
        assertEquals("comic", ShelfKind.COMIC.id)
    }
}
