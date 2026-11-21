package com.nextnotes.tools

import com.xnotes.core.tools.ToolbarEntry
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarItem.CALLIGRAPHY
import com.xnotes.core.tools.ToolbarItem.COLORS
import com.xnotes.core.tools.ToolbarItem.DASHED
import com.xnotes.core.tools.ToolbarItem.ERASER
import com.xnotes.core.tools.ToolbarItem.FIT
import com.xnotes.core.tools.ToolbarItem.FULLSCREEN
import com.xnotes.core.tools.ToolbarItem.HIGHLIGHTER
import com.xnotes.core.tools.ToolbarItem.HOME
import com.xnotes.core.tools.ToolbarItem.IMAGE
import com.xnotes.core.tools.ToolbarItem.INSERT_SPACE
import com.xnotes.core.tools.ToolbarItem.LASSO
import com.xnotes.core.tools.ToolbarItem.MARGINS
import com.xnotes.core.tools.ToolbarItem.MARKUP
import com.xnotes.core.tools.ToolbarItem.MINIMAP
import com.xnotes.core.tools.ToolbarItem.PAGE_NAV
import com.xnotes.core.tools.ToolbarItem.PAN
import com.xnotes.core.tools.ToolbarItem.PEN
import com.xnotes.core.tools.ToolbarItem.REDO
import com.xnotes.core.tools.ToolbarItem.RULER
import com.xnotes.core.tools.ToolbarItem.SCREENSHOT
import com.xnotes.core.tools.ToolbarItem.SELECT
import com.xnotes.core.tools.ToolbarItem.SHAPE
import com.xnotes.core.tools.ToolbarItem.SIDEBAR
import com.xnotes.core.tools.ToolbarItem.SPEED
import com.xnotes.core.tools.ToolbarItem.STYLES
import com.xnotes.core.tools.ToolbarItem.TAPE
import com.xnotes.core.tools.ToolbarItem.TAPER
import com.xnotes.core.tools.ToolbarItem.TEXT
import com.xnotes.core.tools.ToolbarItem.TEXT_BOX
import com.xnotes.core.tools.ToolbarItem.TITLE
import com.xnotes.core.tools.ToolbarItem.UNDO
import com.xnotes.core.tools.ToolbarItem.VIEW
import com.xnotes.core.tools.ToolbarItem.WAND
import com.xnotes.core.tools.ToolbarItem.WAYPOINTS
import com.xnotes.core.tools.ToolbarItem.ZOOM
import com.xnotes.core.tools.ToolbarItem.ZOOM_LOCK
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.core.tools.ToolbarSection

/**
 * NextNotes' default toolbars: a short pen tray with what a student reaches for every minute
 * (pen, highlighter, eraser, lasso, colours, undo) and every other tool kept but hidden, one tap
 * away in toolbar customisation. xnotes' own defaults show everything, which overflows a side rail.
 */
object NnToolbars {
    val NOTE: ToolbarLayout = of(
        listOf(HOME, TITLE, SIDEBAR),
        listOf(PEN, HIGHLIGHTER, ERASER, -DASHED, -CALLIGRAPHY, -SPEED, -TAPER),
        listOf(LASSO, -SELECT, -PAN, -INSERT_SPACE, -SCREENSHOT),
        listOf(COLORS),
        listOf(UNDO, REDO),
        listOf(TEXT, SHAPE, IMAGE, MARKUP, -TEXT_BOX, -WAND, -TAPE, -RULER),
        listOf(PAGE_NAV, VIEW, -STYLES, -MARGINS),
        listOf(-ZOOM, -FIT, -ZOOM_LOCK, -FULLSCREEN),
    )

    val CANVAS: ToolbarLayout = of(
        listOf(HOME, TITLE),
        listOf(PEN, HIGHLIGHTER, ERASER, -DASHED, -CALLIGRAPHY, -SPEED, -TAPER),
        listOf(LASSO, -SELECT, -PAN),
        listOf(COLORS),
        listOf(UNDO, REDO),
        listOf(SHAPE, IMAGE, -WAND, -TAPE),
        listOf(WAYPOINTS, MINIMAP, -STYLES),
        listOf(-ZOOM, -FIT, -ZOOM_LOCK),
    )

    /** A stored bar still on xnotes' untouched default moves to ours; one the user arranged stays. */
    fun note(stored: ToolbarLayout): ToolbarLayout = if (stored == ToolbarLayout.DEFAULT) NOTE else stored

    fun canvas(stored: ToolbarLayout): ToolbarLayout = if (stored == ToolbarLayout.CANVAS_DEFAULT) CANVAS else stored

    /** A hidden entry, written `-ITEM`. */
    private class Hidden(val item: ToolbarItem)

    private operator fun ToolbarItem.unaryMinus(): Any = Hidden(this)

    private fun of(vararg groups: List<Any>): ToolbarLayout = ToolbarLayout(
        groups.map { g ->
            ToolbarSection(g.map { if (it is Hidden) ToolbarEntry(it.item, visible = false) else ToolbarEntry(it as ToolbarItem) })
        },
    )
}
