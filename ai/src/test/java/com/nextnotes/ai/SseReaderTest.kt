package com.nextnotes.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SseReaderTest {
    private fun read(vararg lines: String): List<Pair<String?, String>> {
        val out = mutableListOf<Pair<String?, String>>()
        val r = SseReader { e, d -> out += e to d }
        lines.forEach(r::line)
        r.finish()
        return out
    }

    @Test fun anEventEndsAtABlankLine() {
        assertEquals(listOf(null to "a", null to "b"), read("data: a", "", "data: b", ""))
    }

    @Test fun dataLinesJoinWithNewlines() {
        assertEquals(listOf(null to "x\ny"), read("data: x", "data: y", ""))
    }

    @Test fun theEventNameRidesAlong() {
        assertEquals(listOf("message_stop" to "{}"), read("event: message_stop", "data: {}", ""))
    }

    @Test fun commentsAndCarriageReturnsAreIgnored() {
        assertEquals(listOf(null to "ok"), read(": keep-alive", "data: ok\r", "\r"))
    }

    @Test fun aLastEventWithoutTrailingBlankStillCounts() {
        assertEquals(listOf(null to "[DONE]"), read("data: [DONE]"))
    }

    @Test fun onlyTheFirstSpaceAfterTheColonIsDropped() {
        assertEquals(listOf(null to " two"), read("data:  two", ""))
    }
}
