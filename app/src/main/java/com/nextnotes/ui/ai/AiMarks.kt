package com.nextnotes.ui.ai

import com.nextnotes.ai.Mark
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.ShapeLabel
import com.xnotes.core.model.TextStyle
import com.xnotes.core.model.TextItem
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.TextMeasurer
import com.xnotes.core.shapes.ShapeKind
import kotlin.math.max

/**
 * Turns the AI's marks into ink a teacher would leave: a loop round the step and a few words
 * beside it, red for a mistake, green for a step done right, blue for a remark. They are ordinary
 * shapes and text, so they undo, move and erase like anything else on the page.
 */
object AiMarks {
    val WRONG = Rgba(220, 38, 38, 255)
    val RIGHT = Rgba(22, 163, 74, 255)
    val NOTE = Rgba(37, 99, 235, 255)

    fun color(kind: Mark.Kind): Rgba = when (kind) {
        Mark.Kind.WRONG -> WRONG
        Mark.Kind.RIGHT -> RIGHT
        Mark.Kind.NOTE -> NOTE
    }

    /** Where [mark] falls in the content [region] the picture was taken of. */
    fun contentBox(region: Rect, mark: Mark): Rect = Rect(
        region.left + mark.left * region.w,
        region.top + mark.top * region.h,
        (mark.right - mark.left) * region.w,
        (mark.bottom - mark.top) * region.h,
    )

    /**
     * The loop and the note for [mark] round [box], in [box]'s own coordinates. The note goes to
     * the right of the loop, or under it when that would run past the right edge of [within]. On a
     * paged note it is a text box; the infinite canvas has none, so there [textBoxes] is false and
     * the words are the label of an outline-less box instead.
     */
    fun items(box: Rect, mark: Mark, within: Rect?, measurer: TextMeasurer, textBoxes: Boolean = true): List<CanvasItem> {
        val color = color(mark.kind)
        val pad = max(box.w, box.h) * 0.06 + 3.0
        val loop = Rect(box.left - pad, box.top - pad, box.w + 2 * pad, box.h + 2 * pad)
        val stroke = (box.h * 0.05).coerceIn(1.5, 5.0)
        val ring = ShapeItem(ShapeKind.ELLIPSE, Pt(loop.left, loop.top), Pt(loop.right, loop.bottom), color, stroke)
        if (mark.note.isBlank()) return listOf(ring)
        val size = (box.h * 0.45).coerceIn(9.0, 28.0)
        // Wide enough for the words on one line, measured in the hand they are set in.
        val width = measurer.advances(mark.note, com.xnotes.core.pal.FontSpec(size, FontFace.HAND)).sum() + size
        var at = Pt(loop.right + size * 0.4, loop.top + (loop.h - size * 1.5) / 2)
        if (within != null && at.x + width > within.right) at = Pt(loop.left, loop.bottom + size * 0.3)
        if (textBoxes) return listOf(ring, TextItem(at, width, 0.0, mark.note, color, size, FontFace.HAND, measurer))
        val holder = ShapeItem(ShapeKind.RECTANGLE, at, Pt(at.x + width, at.y + size * 1.8), Rgba(0, 0, 0, 0), 0.0)
        holder.label = ShapeLabel(mark.note, TextStyle(color, size, FontFace.HAND), measurer)
        return listOf(ring, holder)
    }
}
