package com.lightreader.core.util

/** A zero/unbounded height means width-fit, not a demand for a full-resolution bitmap. */
internal fun imageSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    if (width <= 0 || height <= 0 || targetWidth <= 0) return 1
    val widthOnly = targetHeight <= 0 || targetHeight == Int.MAX_VALUE
    var sample = 1
    while (sample <= Int.MAX_VALUE / 2 &&
        width / (sample * 2) >= targetWidth &&
        (widthOnly || height / (sample * 2) >= targetHeight)
    ) {
        sample *= 2
    }
    return sample
}
