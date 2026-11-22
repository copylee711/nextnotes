package com.nextnotes.input

import android.view.MotionEvent

/**
 * Keeps a resting hand from moving the page while the pen writes.
 *
 * - A touch that begins while the pen is hovering, touching, or was there a moment ago is a palm:
 *   none of it reaches the canvas, so it cannot pan, zoom or tap.
 * - So is a contact far wider than a fingertip.
 * - A touch already under way when the pen arrives is taken back: the canvas gets a cancel, which
 *   puts back any view the hand moved, and the rest of it is dropped.
 * - When the pen lands among other contacts, the canvas sees the pen alone, as if nothing else
 *   were touching.
 *
 * One per canvas view: [filter] every touch event and [hover] every hover event through it.
 */
class PalmGuard(private val palmPx: Float) {
    var enabled = true

    /** The pen is in hover range now. */
    private var hovering = false
    /** When the pen was last hovering or touching. */
    private var penSeenAt = Long.MIN_VALUE / 2

    /** This gesture is a hand: drop it, apart from the pen's own pointer. */
    private var palm = false
    /** The pen pointer the canvas is being shown on its own, or -1. */
    private var penId = -1
    private var penDownTime = 0L
    /** The canvas has seen this gesture's down and not yet its end. */
    private var delivering = false
    private var sink: ((MotionEvent) -> Boolean)? = null

    fun hover(e: MotionEvent) {
        if (!isPen(e, 0)) return
        hovering = e.actionMasked != MotionEvent.ACTION_HOVER_EXIT
        penSeenAt = e.eventTime
        if (hovering) takeBack(e)
    }

    fun filter(e: MotionEvent, deliver: (MotionEvent) -> Boolean): Boolean {
        sink = deliver
        if (!enabled) return deliver(e)
        val action = e.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            palm = false
            penId = -1
            delivering = false
            if (!isPen(e, 0) && (penNear(e.eventTime) || wide(e, 0))) {
                palm = true
                return true
            }
        }
        if (anyPen(e)) penSeenAt = e.eventTime

        // The pen, or a contact wider than a finger, joining a hand's touch makes it all a palm. A
        // gesture the pen leads is never taken back: the hand is left out of it below instead.
        if (!palm && action == MotionEvent.ACTION_POINTER_DOWN && !isPen(e, 0)) {
            val i = e.actionIndex
            if (isPen(e, i) || wide(e, i)) takeBack(e)
        }
        // The pen with other contacts down: show the canvas the pen alone.
        if (!palm && isPen(e, 0) && e.pointerCount > 1 && penId < 0) {
            palm = true
            penId = e.getPointerId(0)
            penDownTime = e.downTime
        }
        if (!palm) {
            delivering = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
            return deliver(e)
        }
        return penOnly(e, deliver)
    }

    /** Cancel what the canvas has of this gesture, and treat the rest of it as a palm. */
    private fun takeBack(e: MotionEvent) {
        if (palm) return
        palm = true
        if (!delivering) return
        delivering = false
        val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
        try {
            sink?.invoke(cancel)
        } finally {
            cancel.recycle()
        }
    }

    /** Within a palm gesture, pass on the pen pointer only, as a gesture of its own. */
    private fun penOnly(e: MotionEvent, deliver: (MotionEvent) -> Boolean): Boolean {
        val action = e.actionMasked
        val actionId = e.getPointerId(e.actionIndex)
        if (penId < 0) {
            val starts = (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && isPen(e, e.actionIndex)
            if (!starts) return true
            penId = actionId
            penDownTime = e.eventTime
            return send(e, MotionEvent.ACTION_DOWN, deliver)
        }
        val index = e.findPointerIndex(penId)
        if (index < 0) {
            penId = -1
            return true
        }
        return when {
            action == MotionEvent.ACTION_CANCEL -> send(e, MotionEvent.ACTION_CANCEL, deliver).also { penId = -1 }
            (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && actionId == penId ->
                send(e, MotionEvent.ACTION_UP, deliver).also { penId = -1 }
            action == MotionEvent.ACTION_MOVE -> send(e, MotionEvent.ACTION_MOVE, deliver)
            else -> true // another contact came or went: nothing for the pen
        }
    }

    /** [e] as a one-pointer event of the pen, its in-between samples kept for smooth ink. */
    private fun send(e: MotionEvent, action: Int, deliver: (MotionEvent) -> Boolean): Boolean {
        val i = e.findPointerIndex(penId)
        if (i < 0) return true
        delivering = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
        val props = arrayOf(MotionEvent.PointerProperties().also { e.getPointerProperties(i, it) })
        val history = if (action == MotionEvent.ACTION_MOVE) e.historySize else 0
        val coords = MotionEvent.PointerCoords()
        if (history > 0) e.getHistoricalPointerCoords(i, 0, coords) else e.getPointerCoords(i, coords)
        val first = if (history > 0) e.getHistoricalEventTime(0) else e.eventTime
        val out = MotionEvent.obtain(
            penDownTime, first, action, 1, props, arrayOf(coords),
            e.metaState, e.buttonState, e.xPrecision, e.yPrecision, e.deviceId, e.edgeFlags, e.source, e.flags,
        )
        try {
            for (h in 1 until history) {
                val c = MotionEvent.PointerCoords().also { e.getHistoricalPointerCoords(i, h, it) }
                out.addBatch(e.getHistoricalEventTime(h), arrayOf(c), e.metaState)
            }
            if (history > 0) {
                val c = MotionEvent.PointerCoords().also { e.getPointerCoords(i, it) }
                out.addBatch(e.eventTime, arrayOf(c), e.metaState)
            }
            return deliver(out)
        } finally {
            out.recycle()
        }
    }

    private fun penNear(now: Long) = hovering || now - penSeenAt < GRACE_MS

    private fun wide(e: MotionEvent, i: Int) = palmPx > 0 && e.getTouchMajor(i) > palmPx

    private fun anyPen(e: MotionEvent) = (0 until e.pointerCount).any { isPen(e, it) }

    private fun isPen(e: MotionEvent, i: Int): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    companion object {
        /** How long after the pen leaves a touch still counts as the hand that held it. */
        const val GRACE_MS = 700L

        /** A guard for a view on a screen of [xdpi]: contacts wider than about 2 cm are palms. */
        fun forDpi(xdpi: Float) = PalmGuard(xdpi * 20f / 25.4f)
    }
}
