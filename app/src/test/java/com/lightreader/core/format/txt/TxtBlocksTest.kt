package com.lightreader.core.format.txt

import com.lightreader.core.format.Block
import com.lightreader.core.format.BlockHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtBlocksTest {

    @Test
    fun `short lines become separate paragraphs`() {
        val blocks = TxtBlocks.parse("第一段\n第二段\n第三段")
        assertEquals(3, blocks.size)
    }

    @Test
    fun `blank line separates joined paragraphs`() {
        val long1 = "这是很长的一行文字，用来验证硬换行会被合并成同一个段落，".repeat(2)
        val long2 = "紧接着的下一行同样很长，应该和上一行属于同一段。".repeat(2)
        val blocks = TxtBlocks.parse("$long1\n$long2\n\n新的段落")
        assertEquals(2, blocks.size)
    }

    @Test
    fun `windows line endings are handled`() {
        val blocks = TxtBlocks.parse("你好\r\n世界\r\n")
        assertEquals(2, blocks.size)
    }

    @Test
    fun `html rendering escapes markup`() {
        val blocks = TxtBlocks.parse("<script>alert(1)</script>")
        val html = BlockHtml.render(blocks)
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `empty text produces no blocks`() {
        assertEquals(emptyList<Block>(), TxtBlocks.parse("   \n\n  "))
    }
}
