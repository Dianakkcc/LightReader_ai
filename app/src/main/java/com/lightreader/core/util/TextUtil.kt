package com.lightreader.core.util

object TextUtil {

    private val whitespace = Regex("[\\t\\u000B\\f\\u00A0\\u2000-\\u200B]+")

    fun escapeHtml(raw: String): String {
        val builder = StringBuilder(raw.length + 16)
        for (char in raw) {
            when (char) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                '\'' -> builder.append("&#39;")
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    fun collapseWhitespace(raw: String): String =
        raw.replace(whitespace, " ").replace(Regex(" {2,}"), " ").trim()

    /** Text shown in bookmark / search result cards. */
    fun snippet(text: String, offset: Int, length: Int = 90): String {
        if (text.isEmpty()) return ""
        val start = (offset - length / 2).coerceIn(0, maxOf(0, text.length - 1))
        val end = (start + length).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + collapseWhitespace(text.substring(start, end)) + suffix
    }

    /** Rough word count that also works for CJK text without spaces. */
    fun charCount(text: String): Int = text.length

    fun normaliseForSearch(text: String): String = text.lowercase()
}
