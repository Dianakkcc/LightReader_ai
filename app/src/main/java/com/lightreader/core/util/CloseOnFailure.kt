package com.lightreader.core.util

import java.io.Closeable

/** Transfers ownership on success; releases the handle if its new owner fails to open. */
internal inline fun <T : Closeable, R> T.closeIfOpeningFails(open: (T) -> R): R = try {
    open(this)
} catch (failure: Throwable) {
    try {
        close()
    } catch (closeFailure: Throwable) {
        failure.addSuppressed(closeFailure)
    }
    throw failure
}
