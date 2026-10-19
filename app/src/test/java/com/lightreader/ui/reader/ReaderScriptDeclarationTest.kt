package com.lightreader.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading engine runs in strict mode, where assigning to an identifier that was never
 * declared throws a `ReferenceError` and kills the whole engine - exactly what happened once and
 * left continuous scrolling completely dead on device (Rhino tolerated it, a real Blink did not).
 *
 * This test scans the generated JavaScript for statement level assignments and fails if the name
 * is not declared anywhere.
 */
class ReaderScriptDeclarationTest {

    private val script = ReaderScript.build(
        chapterIndex = 0,
        paged = true,
        initialOffset = 0,
        horizontalPaddingPx = 20,
        verticalPaddingPx = 24,
        nextChapterTitle = "下一章",
        previousChapterTitle = "上一章",
        twoUp = true,
    )

    @Test
    fun `every assigned identifier is declared`() {
        val declared = HashSet<String>()

        Regex("""\bvar\s+([A-Za-z_$][\w$]*)""").findAll(script).forEach {
            declared += it.groupValues[1]
        }
        Regex("""\bfunction\s+([A-Za-z_$][\w$]*)""").findAll(script).forEach {
            declared += it.groupValues[1]
        }
        Regex("""function\s*[A-Za-z_$\w]*\s*\(([^)]*)\)""").findAll(script).forEach { match ->
            match.groupValues[1].split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { declared += it }
        }

        val assigned = Regex("""(?m)^\s*([A-Za-z_$][\w$]*)\s*=(?!=)""")
            .findAll(script)
            .map { it.groupValues[1] }
            .toSet()

        val undeclared = assigned - declared
        assertTrue(
            "这些标识符被赋值但没有声明（严格模式会抛 ReferenceError）：$undeclared",
            undeclared.isEmpty(),
        )
    }

    @Test
    fun `engine declares the variables used by the scroll mode`() {
        listOf("shortChapter", "pullThreshold", "pull", "pulling", "ready", "hasNext").forEach { name ->
            assertTrue("缺少声明：$name", script.contains("var $name"))
        }
    }
}
