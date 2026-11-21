package com.nextnotes.ui.ai

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.ShapeLabel
import com.xnotes.core.model.TextItem
import com.xnotes.core.model.TextStyle
import com.xnotes.core.pal.FontFace
import com.xnotes.core.pal.FontSpec
import com.xnotes.core.pal.TextMeasurer
import com.xnotes.core.shapes.ShapeKind
import kotlin.math.max

/** Places handwriting read back as text where the ink was, or just under it. */
object AiText {
    /** A size that keeps the lines about as tall as the handwriting was. */
    fun pointSize(ink: Rect, text: String): Double =
        (ink.h / text.lines().size.coerceAtLeast(1) * 0.5).coerceIn(10.0, 32.0)

    private fun width(text: String, size: Double, measurer: TextMeasurer, atLeast: Double): Double {
        val font = FontSpec(size, FontFace.SANS)
        val longest = text.lines().maxOf { measurer.advances(it, font).sum() }
        return max(atLeast, longest + size)
    }

    private fun origin(ink: Rect, inPlace: Boolean, size: Double): Pt =
        if (inPlace) Pt(ink.left, ink.top) else Pt(ink.left, ink.bottom + size * 0.6)

    /** A text box for a paged note, kept from running past the right edge of [within]. */
    fun box(ink: Rect, text: String, inPlace: Boolean, within: Rect, color: Rgba, measurer: TextMeasurer): TextItem {
        val size = pointSize(ink, text)
        val at = origin(ink, inPlace, size)
        val w = width(text, size, measurer, ink.w).coerceAtMost(max(size * 4, within.right - at.x))
        return TextItem(at, w, 0.0, text, color, size, FontFace.SANS, measurer)
    }

    /** The same words on the infinite canvas: the label of an outline-less box. */
    fun label(ink: Rect, text: String, inPlace: Boolean, color: Rgba, measurer: TextMeasurer): ShapeItem {
        val size = pointSize(ink, text)
        val at = origin(ink, inPlace, size)
        val w = width(text, size, measurer, ink.w)
        val h = text.lines().size * size * 1.6
        return ShapeItem(ShapeKind.RECTANGLE, at, Pt(at.x + w, at.y + h), Rgba(0, 0, 0, 0), 0.0).apply {
            label = ShapeLabel(text, TextStyle(color, size, FontFace.SANS), measurer)
        }
    }
}
