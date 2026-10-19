package com.lightreader.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {
    @Test
    fun `width fit downsamples even when height is unbounded`() {
        // Old comic flow passed MAX_VALUE, disabling sampling and allocating 4096 x 6144.
        assertEquals(2, imageSampleSize(4096, 6144, 1080, 0))
        assertEquals(2, imageSampleSize(4096, 6144, 1080, Int.MAX_VALUE))
    }

    @Test
    fun `finite target keeps enough pixels for a cropped cover`() {
        assertEquals(4, imageSampleSize(2400, 3600, 300, 800))
        assertEquals(1, imageSampleSize(200, 300, 300, 450))
    }
}
