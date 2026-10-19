package com.lightreader.core.format

/**
 * All resources referenced by generated chapter HTML go through this virtual host; the WebView
 * client intercepts them and serves bytes from the book archive or from app private storage.
 * Nothing is ever fetched from the network (the app has no INTERNET permission).
 */
object ReaderAssets {
    const val HOST = "reader.local"
    const val BASE_URL = "https://$HOST/"

    const val EPUB_PREFIX = "epub/"
    const val MEDIA_PREFIX = "media/"
    const val FONT_PREFIX = "font/"

    fun url(path: String): String = BASE_URL + path.trimStart('/')

    fun epubEntry(bookKey: String, entryPath: String): String =
        url("$EPUB_PREFIX$bookKey/${entryPath.trimStart('/')}")

    fun mediaFile(bookKey: String, relativePath: String): String =
        url("$MEDIA_PREFIX$bookKey/${relativePath.trimStart('/')}")

    fun font(fontId: Long): String = url("$FONT_PREFIX$fontId")

    /** Decorative artwork shipped with the app (the Diana / 嘉然 limited theme). */
    fun brand(name: String): String = url("brand/${name.trimStart('/')}")
}
