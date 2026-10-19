package com.lightreader.core.format.txt

/**
 * Heuristics for finding chapter headings in plain text. Deliberately conservative: a false
 * positive only creates a slightly odd table of contents entry, but a too aggressive pattern
 * would chop real paragraphs in half, so matches must look like a standalone heading line.
 */
object ChapterPatterns {

    private const val MAX_HEADING_LENGTH = 40

    private const val TITLE_SUFFIX = "(?:[\\s:：、.．\\-—－].{0,28})?"

    private val chinesePattern = Regex(
        "^(?:" +
            "第\\s*[0-9零一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]+\\s*[章节節回卷篇部话話集]" +
            "|卷\\s*[0-9零一二三四五六七八九十百千万两]+" +
            "|序章|序言|序幕|前言|引子|楔子|尾声|尾聲|后记|後記|附录|附錄|番外[0-9一二三四五六七八九十]*" +
            ")$TITLE_SUFFIX",
    )

    private val englishPattern = Regex(
        "^(?:(chapter|part|book|section)\\s+([0-9]{1,4}|[IVXLC]{1,8})" +
            "|(prologue|epilogue|preface|foreword|appendix|introduction|conclusion)" +
            ")$TITLE_SUFFIX",
        RegexOption.IGNORE_CASE,
    )

    private val numberedPattern = Regex("^[0-9]{1,4}\\s*[.、]\\s*\\S.{0,30}$")

    /**
     * @return heading level (1 for volumes/parts, 2 for chapters) or null when the line is not a
     * heading.
     */
    fun headingLevel(line: String): Int? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_HEADING_LENGTH) return null
        if (chinesePattern.matches(trimmed)) {
            return if (trimmed.startsWith("卷") || trimmed.startsWith("第") && trimmed.contains("卷")) 1 else 2
        }
        if (englishPattern.matches(trimmed)) return 2
        if (numberedPattern.matches(trimmed)) return 2
        return null
    }
}
