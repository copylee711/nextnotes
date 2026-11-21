package com.nextnotes.ui.ai

import org.commonmark.ext.gfm.tables.TableBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkdownTableTest {
    private fun text(n: org.commonmark.node.Node): String {
        val sb = StringBuilder()
        fun walk(x: org.commonmark.node.Node) {
            if (x is org.commonmark.node.Text) sb.append(x.literal)
            var c = x.firstChild
            while (c != null) { walk(c); c = c.next }
        }
        walk(n)
        return sb.toString()
    }

    @Test fun anAnswersTableIsReadAsRows() {
        // The shape the model gave when checking 2+3=6, with a formula in a cell.
        val answer = "| 步骤 | 内容 | 判断 |\n|---|---|---|\n| 1 | 抄写算式 \$2+3\$ | ✅ |\n| 3 | 写出 \$=6\$ | ❌ 错误 |\n"
        val math = MathText.of(answer)
        val table = parser.parse(math.markdown).firstChild
        assertNotNull("parsed as a table", table as? TableBlock)
        val rows = tableRows(table)
        assertEquals(3, rows.size)
        assertEquals(listOf(true, false, false), rows.map { it.first })
        assertEquals(listOf("步骤", "内容", "判断"), rows[0].second.map { text(it) })
        assertEquals("❌ 错误", text(rows[2].second[2]))
        // The formula stays a token the cell sets as maths.
        assertEquals(listOf("2+3", "=6"), math.formulas)
    }
}
