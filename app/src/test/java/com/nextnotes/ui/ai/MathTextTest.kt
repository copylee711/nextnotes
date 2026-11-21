package com.nextnotes.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTextTest {
    @Test fun inlineDollarMathIsPulledOut() {
        val m = MathText.of("设 \$a_1 * b\$ 为常数")
        assertEquals(listOf("a_1 * b"), m.formulas)
        assertEquals("设 0 为常数", m.markdown)
    }

    @Test fun displayMathTakesItsOwnParagraph() {
        val m = MathText.of("结果：\$\$x^2\n+1\$\$完")
        assertEquals(listOf("x^2\n+1"), m.formulas)
        assertTrue(m.markdown.contains("\n\n0\n\n"))
    }

    @Test fun bracketDelimitersWorkToo() {
        val m = MathText.of("行内 \\(x\\) 与独立 \\[y\\]")
        assertEquals(listOf("y", "x"), m.formulas) // display is pulled first
    }

    @Test fun pricesAreNotMaths() {
        val m = MathText.of("花了 \$5 和 \$10")
        assertTrue(m.formulas.isEmpty())
    }

    @Test fun dollarsInCodeStayCode() {
        val m = MathText.of("运行 `echo \$HOME \$PATH` 和\n```\nprice = \$a\$\n```\n然后 \$z\$")
        assertEquals(listOf("z"), m.formulas)
        assertTrue(m.markdown.contains("`echo \$HOME \$PATH`"))
        assertTrue(m.markdown.contains("price = \$a\$"))
    }

    @Test fun unclosedDisplayMathIsLeftAloneWhileStreaming() {
        val m = MathText.of("推导：\$\$x = ")
        assertFalse(m.markdown.contains(""))
    }
}
