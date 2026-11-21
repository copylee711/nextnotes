package com.nextnotes.ui.ai

import com.nextnotes.ai.Mark
import com.xnotes.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class AiMarksTest {
    @Test fun aMarkLandsWhereItWasOnThePicture() {
        // The picture showed content from (100, 200), 400 wide and 300 tall.
        val region = Rect(100.0, 200.0, 400.0, 300.0)
        val box = AiMarks.contentBox(region, Mark(0.25, 0.5, 0.75, 1.0, Mark.Kind.WRONG, ""))
        assertEquals(Rect(200.0, 350.0, 200.0, 150.0), box)
    }

    @Test fun eachKindHasItsColour() {
        assertEquals(AiMarks.WRONG, AiMarks.color(Mark.Kind.WRONG))
        assertEquals(AiMarks.RIGHT, AiMarks.color(Mark.Kind.RIGHT))
        assertEquals(AiMarks.NOTE, AiMarks.color(Mark.Kind.NOTE))
    }
}
