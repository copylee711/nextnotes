package com.xnotes.canvas

import android.os.Handler
import android.view.MotionEvent
import com.xnotes.core.geometry.Pt

/**
 * NextNotes: hold the pen tip still on the screen to borrow the eraser for the rest of that contact.
 *
 * Some pens cannot report their side buttons to apps at all (the Xiaomi Smart Pen on the Pad 5
 * hands them to the system), so this is the eraser shortcut that works with any pen. It is armed
 * when a stylus starts an ink stroke and fires only if the tip stays within [SLOP_DP] dp of where
 * it landed after [HOLD_MS]; any real movement first means writing, and disarms it. That keeps it
 * clear of the shape snap, which needs a stroke drawn before the pen holds still.
 *
 * Both canvases own one and call [arm] on a stylus ink press, [onMove] with the pen's position,
 * and [end] when the contact ends or turns into something else. [onFire] receives the viewport
 * point the pen is holding at, and the canvas swaps the dot it started for an erase from there.
 */
class PenHoldEraser(
    private val handler: Handler,
    private val density: () -> Double,
    private val onFire: (Pt) -> Unit,
) {
    /** Off in the preferences: never arms. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) end()
        }

    private var anchor: Pt? = null
    private var latest = Pt.ZERO
    private var pending: Runnable? = null

    /** Arm for a stylus ink press at [viewport]. */
    fun arm(viewport: Pt) {
        end()
        if (!enabled) return
        anchor = viewport
        latest = viewport
        val r = Runnable {
            pending = null
            anchor = null
            onFire(latest)
        }
        pending = r
        handler.postDelayed(r, HOLD_MS)
    }

    /** Check batched samples too, so a stroke that loops back to its start still counts as writing. */
    fun onMove(event: MotionEvent, pointerId: Int) {
        val a = anchor ?: return
        val index = event.findPointerIndex(pointerId)
        if (index < 0) {
            end()
            return
        }
        val radius = SLOP_DP * density()
        for (i in 0 until event.historySize) {
            val point = Pt(event.getHistoricalX(index, i).toDouble(), event.getHistoricalY(index, i).toDouble())
            if (point.distanceTo(a) > radius) {
                end()
                return
            }
        }
        latest = Pt(event.getX(index).toDouble(), event.getY(index).toDouble())
        if (latest.distanceTo(a) > radius) end()
    }

    /** The contact ended or became something else. */
    fun end() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        anchor = null
    }

    companion object {
        private const val HOLD_MS = 300L

        /** Distance from the initial contact, independent of screen density and page zoom. */
        private const val SLOP_DP = 6.0
    }
}
