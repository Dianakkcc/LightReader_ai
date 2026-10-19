package com.lightreader.core.util

import java.net.URLDecoder

/** Path handling for zip based formats (EPUB, DOCX): resolution, normalisation, fragments. */
object ZipPaths {

    fun fragment(href: String): String? =
        href.substringAfterLast('#', "").takeIf { it.isNotEmpty() && '#' in href }

    fun withoutFragment(href: String): String = href.substringBefore('#')

    fun decode(href: String): String =
        runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)

    /** Resolves [href] against the directory [baseDir] (both zip-relative, '/'-separated). */
    fun resolve(baseDir: String, href: String): String {
        val decoded = decode(withoutFragment(href)).replace('\\', '/')
        if (decoded.startsWith("/")) return normalise(decoded)
        val prefix = if (baseDir.isEmpty()) "" else baseDir.trimEnd('/') + "/"
        return normalise(prefix + decoded)
    }

    fun normalise(path: String): String {
        val parts = ArrayList<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    fun parentDir(path: String): String = path.substringBeforeLast('/', "")

    fun isExternal(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://", true) ||
            trimmed.startsWith("https://", true) ||
            trimmed.startsWith("data:", true) ||
            trimmed.startsWith("mailto:", true) ||
            trimmed.startsWith("tel:", true) ||
            trimmed.startsWith("#") ||
            trimmed.startsWith("//")
    }
}
