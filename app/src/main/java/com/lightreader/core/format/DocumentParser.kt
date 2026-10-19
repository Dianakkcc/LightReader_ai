package com.lightreader.core.format

import android.content.Context
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.OpenedDocument

enum class ParseErrorReason {
    CORRUPT,
    UNSUPPORTED,
    PERMISSION,
    MISSING,
    TOO_LARGE,
    UNKNOWN,
}

class ParseException(
    val reason: ParseErrorReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The single extension point of the format layer: everything a parser has to do is turn a
 * [DocumentSource] into an [OpenedDocument]. The reader UI only ever talks to that model.
 */
interface DocumentParser {
    val format: BookFormat

    fun canHandle(source: DocumentSource): Boolean

    suspend fun parse(context: Context, source: DocumentSource): OpenedDocument
}

/** Maps low level IO failures onto reasons the UI can explain to a normal user. */
fun Throwable.toParseException(fallback: String): ParseException = when (this) {
    is ParseException -> this
    is java.io.FileNotFoundException -> ParseException(
        ParseErrorReason.MISSING,
        "$fallback：文件不存在或已被移动",
        this,
    )
    is SecurityException -> ParseException(
        ParseErrorReason.PERMISSION,
        "$fallback：没有访问权限",
        this,
    )
    is java.util.zip.ZipException -> ParseException(
        ParseErrorReason.CORRUPT,
        "$fallback：压缩包结构损坏",
        this,
    )
    else -> ParseException(ParseErrorReason.UNKNOWN, fallback, this)
}
