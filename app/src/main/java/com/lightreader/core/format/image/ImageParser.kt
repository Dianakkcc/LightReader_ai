package com.lightreader.core.format.image

import android.content.Context
import com.lightreader.core.format.DocumentParser
import com.lightreader.core.format.DocumentSource
import com.lightreader.core.format.toParseException
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.OpenedDocument

class ImageParser : DocumentParser {

    override val format: BookFormat = BookFormat.IMAGE

    override fun canHandle(source: DocumentSource): Boolean = source.format == BookFormat.IMAGE

    override suspend fun parse(context: Context, source: DocumentSource): OpenedDocument = try {
        // Verify that the image can actually be decoded: a broken file must fail here, at import
        // time, instead of showing an empty reader.
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.openInput(context.applicationContext).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw com.lightreader.core.format.ParseException(
                com.lightreader.core.format.ParseErrorReason.CORRUPT,
                "图片无法解码",
            )
        }
        ImageDocument(
            context = context.applicationContext,
            source = source,
            imageUris = listOf(source.uri),
        )
    } catch (throwable: Throwable) {
        throw throwable.toParseException("无法打开图片")
    }
}
