package com.nextnotes.ui.ai

import com.nextnotes.ai.Role
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiSessionsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val session = SavedSession("sid-1", listOf(
        SavedTurn(Role.USER, "检查过程", label = "选区", image = byteArrayOf(1, 2, 3)),
        SavedTurn(Role.ASSISTANT, "第 3 步错了：\$2+3=5\$", reasoning = "想一想", notice = "被截断了"),
    ))

    @Test fun aConversationComesBackAsItWasLeft() {
        val store = AiSessions(tmp.root)
        store.save("content://notes|doc-1", session)
        val back = store.load("content://notes|doc-1")!!
        assertEquals("sid-1", back.sessionId)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), back.turns.map { it.role })
        assertEquals("选区", back.turns[0].label)
        assertArrayEquals(byteArrayOf(1, 2, 3), back.turns[0].image)
        assertEquals("第 3 步错了：\$2+3=5\$", back.turns[1].text)
        assertEquals("想一想", back.turns[1].reasoning)
        assertEquals("被截断了", back.turns[1].notice)
        assertNull(back.turns[1].image)
    }

    @Test fun eachNoteKeepsItsOwn() {
        val store = AiSessions(tmp.root)
        store.save("a", session)
        assertNull(store.load("b"))
    }

    @Test fun anEmptyConversationLeavesNothingBehind() {
        val store = AiSessions(tmp.root)
        store.save("a", session)
        store.save("a", SavedSession("sid-2", emptyList()))
        assertNull(store.load("a"))
    }
}
