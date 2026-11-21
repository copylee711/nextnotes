package com.nextnotes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksTest {
    @Test fun takesTheBlockOutAndReadsIt() {
        val answer = "第二步错了：17×3=51。\n\n```marks\n[{\"box\":[100,200,500,300],\"kind\":\"wrong\",\"note\":\"应为 51\"}]\n```\n"
        val (shown, marks) = Marks.split(answer)
        assertEquals("第二步错了：17×3=51。", shown)
        assertEquals(1, marks.size)
        val m = marks[0]
        assertEquals(0.1, m.left, 1e-9)
        assertEquals(0.2, m.top, 1e-9)
        assertEquals(0.5, m.right, 1e-9)
        assertEquals(0.3, m.bottom, 1e-9)
        assertEquals(Mark.Kind.WRONG, m.kind)
        assertEquals("应为 51", m.note)
    }

    @Test fun hidesAnUnfinishedBlockWhileItStreams() {
        val (shown, marks) = Marks.split("对了。\n```marks\n[{\"box\":[1,2")
        assertEquals("对了。", shown)
        assertTrue(marks.isEmpty())
    }

    @Test fun leavesAnAnswerWithoutMarksAlone() {
        val answer = "没有错误。\n```python\nprint(1)\n```"
        assertEquals(answer to emptyList<Mark>(), Marks.split(answer))
    }

    @Test fun forgivesWhatModelsGetWrong() {
        val marks = Marks.parse(
            """{"marks": [
                {"box": [0.5, 0.6, 0.1, 0.2], "kind": "ok"},
                {"box": [10, 10], "kind": "wrong"},
                {"box": [300, 300, 300, 400]},
                {"box": [-50, 900, 1200, 1100], "kind": "提醒", "note": "  看单位  "},
                "nonsense"
            ]}""",
        )
        assertEquals(2, marks.size)
        // Swapped corners, given as fractions.
        assertEquals(Mark(0.1, 0.2, 0.5, 0.6, Mark.Kind.RIGHT, ""), marks[0])
        // Off the picture, clamped to it.
        assertEquals(Mark(0.0, 0.9, 1.0, 1.0, Mark.Kind.NOTE, "看单位"), marks[1])
    }

    @Test fun badJsonGivesNoMarks() {
        assertTrue(Marks.parse("[{oops").isEmpty())
    }
}
