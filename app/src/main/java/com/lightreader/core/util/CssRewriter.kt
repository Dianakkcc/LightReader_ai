package com.lightreader.core.util

/**
 * Rewrites relative `url(...)` references inside stylesheets so they point at the reader asset
 * host. Needed both for EPUB stylesheets and for inline `style` attributes of book content.
 */
object CssRewriter {

    private val urlPattern = Regex("""url\(\s*(['"]?)([^'")]+)\1\s*\)""", RegexOption.IGNORE_CASE)
    private val importPattern = Regex("""@import\s+(['"])([^'"]+)\1""", RegexOption.IGNORE_CASE)

    fun rewrite(css: String, mapper: (String) -> String): String {
        var result = urlPattern.replace(css) { match ->
            val reference = match.groupValues[2]
            if (ZipPaths.isExternal(reference)) match.value else "url(\"${mapper(reference)}\")"
        }
        result = importPattern.replace(result) { match ->
            val reference = match.groupValues[2]
            if (ZipPaths.isExternal(reference)) match.value else "@import \"${mapper(reference)}\""
        }
        return result
    }
}
