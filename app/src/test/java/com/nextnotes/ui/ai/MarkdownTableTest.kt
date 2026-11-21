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

    @Test fun columnsThatFitAreLeftAlone() {
        val natural = intArrayOf(100, 200, 50)
        assertEquals(natural.toList(), fitColumns(natural, 400, 48).toList())
    }

    @Test fun narrowColumnsKeepTheirWidthAndWideOnesShareTheRest() {
        // 600 into 400: the 60 keeps its width, the two wide ones split the 340 left.
        assertEquals(listOf(60, 170, 170), fitColumns(intArrayOf(60, 220, 320), 400, 48).toList())
    }

    @Test fun noColumnGoesUnderTheMinimum() {
        assertEquals(listOf(48, 48, 48), fitColumns(intArrayOf(200, 200, 200), 90, 48).toList())
    }

    @Test fun aColumnKeepsRoomForWhatItCannotWrap() {
        // The last column holds a formula 210 wide, so it keeps that even past the 400.
        assertEquals(listOf(60, 170, 210), fitColumns(intArrayOf(60, 220, 320), 400, 48, intArrayOf(40, 80, 210)).toList())
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
