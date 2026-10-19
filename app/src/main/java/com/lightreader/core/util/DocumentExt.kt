package com.lightreader.core.util

import com.lightreader.core.model.OpenedDocument

/** Runs [block] with the document and always closes it, even when the block throws. */
inline fun <T> OpenedDocument.use(block: (OpenedDocument) -> T): T = try {
    block(this)
} finally {
    runCatching { close() }
}
