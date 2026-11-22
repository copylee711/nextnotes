package com.nextnotes.tools

import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NnToolbarsTest {
    private fun ToolbarLayout.items() = sections.flatMap { it.entries }.map { it.item }
    private fun ToolbarLayout.raw() = sections.map { s -> s.entries.map { it.item.id to it.visible } }

    @Test fun everyToolIsKeptOnce() {
        assertEquals(ToolbarLayout.NOTE_ITEMS, NnToolbars.NOTE.items().toSet())
        assertEquals(ToolbarLayout.NOTE_ITEMS.size, NnToolbars.NOTE.items().size)
        assertEquals(ToolbarLayout.CANVAS_ITEMS, NnToolbars.CANVAS.items().toSet())
        assertEquals(ToolbarLayout.CANVAS_ITEMS.size, NnToolbars.CANVAS.items().size)
    }

    @Test fun survivesStorage() {
        assertEquals(NnToolbars.NOTE, ToolbarLayout.fromRaw(NnToolbars.NOTE.raw()))
        assertEquals(NnToolbars.CANVAS, ToolbarLayout.fromRaw(NnToolbars.CANVAS.raw(), ToolbarLayout.CANVAS_ITEMS, ToolbarLayout.CANVAS_DEFAULT))
    }

    @Test fun showsTheEverydayToolsOnly() {
        val shown = NnToolbars.NOTE.sections.flatMap { it.visibleEntries }.map { it.item }
        assertTrue(listOf(ToolbarItem.PEN, ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER, ToolbarItem.LASSO, ToolbarItem.COLORS, ToolbarItem.UNDO).all { it in shown })
        assertTrue(ToolbarItem.CALLIGRAPHY !in shown && ToolbarItem.FULLSCREEN !in shown)
    }

    @Test fun movesOnlyUntouchedDefaults() {
        assertSame(NnToolbars.NOTE, NnToolbars.note(ToolbarLayout.DEFAULT))
        assertSame(NnToolbars.CANVAS, NnToolbars.canvas(ToolbarLayout.CANVAS_DEFAULT))
        val arranged = ToolbarLayout.DEFAULT.toggleVisible(2, 1)
        assertSame(arranged, NnToolbars.note(arranged))
    }

    @Test fun panHeadsThePenTray() {
        val tray = NnToolbars.NOTE.sections[1].visibleEntries.map { it.item }
        assertEquals(listOf(ToolbarItem.PAN, ToolbarItem.PEN, ToolbarItem.HIGHLIGHTER, ToolbarItem.ERASER), tray)
        assertEquals(ToolbarItem.PAN, NnToolbars.CANVAS.sections[1].visibleEntries.first().item)
    }
}
