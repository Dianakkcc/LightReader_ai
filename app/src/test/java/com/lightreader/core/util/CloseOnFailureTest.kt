package com.lightreader.core.util

import java.io.Closeable
import org.junit.Assert.*
import org.junit.Test

class CloseOnFailureTest {
    private class Handle(private val closeFailure: Throwable? = null) : Closeable {
        var closes = 0
        override fun close() { closes++; closeFailure?.let { throw it } }
    }

    @Test fun `failed renderer creation releases the descriptor and keeps the cause`() {
        val handle = Handle()
        val cause = IllegalArgumentException("invalid PDF")
        val failure = runCatching { handle.closeIfOpeningFails { throw cause } }.exceptionOrNull()
        assertSame(cause, failure)
        assertEquals(1, handle.closes)
    }

    @Test fun `successful owner retains the descriptor`() {
        val handle = Handle()
        assertEquals("renderer", handle.closeIfOpeningFails { "renderer" })
        assertEquals(0, handle.closes)
    }

    @Test fun `close failure does not hide the original open failure`() {
        val closeFailure = IllegalStateException("close")
        val handle = Handle(closeFailure)
        val cause = IllegalArgumentException("open")
        val failure = runCatching { handle.closeIfOpeningFails { throw cause } }.exceptionOrNull()
        assertSame(cause, failure)
        assertEquals(listOf(closeFailure), failure!!.suppressed.toList())
    }
}
