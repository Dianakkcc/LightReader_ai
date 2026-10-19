package com.lightreader.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A `@JavascriptInterface` method whose body calls a name equal to the method itself calls
 * *itself*: inside the class body the member function shadows the property of the same name. That
 * is not a compile error, it is an infinite loop - and on a device it pinned the main thread at
 * 100% CPU while silently swallowing every position, ready and end-of-chapter event.
 *
 * The callbacks are therefore named differently from the bridge methods, and this test keeps it
 * that way.
 */
class ReaderBridgeShadowingTest {

    private val source = File(
        System.getProperty("user.dir"),
        "src/main/java/com/lightreader/ui/reader/WebReaderView.kt",
    )

    @Test
    fun `bridge methods never call themselves`() {
        assertTrue("找不到 ${source.path}", source.isFile)
        val text = stripComments(source.readText())
        val offenders = mutableListOf<String>()

        val pattern = Regex("""@JavascriptInterface\s+fun\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*\{""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val body = bodyOf(text, match.range.last)
            if (Regex("""\b${Regex.escape(name)}\s*\(""").containsMatchIn(body)) {
                offenders += name
            }
        }

        assertTrue(
            "这些 JS 桥接方法在方法体里调用了自己（无限递归）：$offenders",
            offenders.isEmpty(),
        )
    }

    /** Comments may talk about the trap; only code counts. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?m)//.*$"""), "")

    /** Body of a block starting at the `{` at [openBrace], found by counting braces. */
    private fun bodyOf(text: String, openBrace: Int): String {
        var depth = 0
        var index = openBrace
        while (index < text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openBrace + 1, index)
                }
            }
            index++
        }
        return text.substring(openBrace + 1)
    }
}
