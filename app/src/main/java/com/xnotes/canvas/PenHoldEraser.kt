package com.xnotes.canvas

import android.os.Handler
import com.xnotes.core.geometry.Pt

/**
 * NextNotes: hold the pen tip still on the screen to borrow the eraser for the rest of that contact.
 *
 * Some pens cannot report their side buttons to apps at all (the Xiaomi Smart Pen on the Pad 5
 * hands them to the system), so this is the eraser shortcut that works with any pen. It is armed
 * when a stylus starts an ink stroke and fires only if the tip has not left [SLOP] pixels of where
 * it landed after [HOLD_MS]; any real movement first means writing, and disarms it. That keeps it
 * clear of the shape snap, which needs a stroke drawn before the pen holds still.
 *
 * Both canvases own one and call [arm] on a stylus ink press, [onMove] with the pen's position,
 * and [end] when the contact ends or turns into something else. [onFire] receives the viewport
 * point the pen is holding at, and the canvas swaps the dot it started for an erase from there.
 */
class PenHoldEraser(
    private val handler: Handler,
    private val onFire: (Pt) -> Unit,
) {
    /** Off in the preferences: never arms. */
    var enabled: Boolean = true

    private var anchor: Pt? = null
    private var pending: Runnable? = null

    /** Arm for a stylus ink press at [viewport]. */
    fun arm(viewport: Pt) {
        end()
        if (!enabled) return
        anchor = viewport
        val r = Runnable {
            pending = null
            anchor = null
            onFire(viewport)
        }
        pending = r
        handler.postDelayed(r, HOLD_MS)
    }

    /** The pen moved to [viewport]: past the slop it is writing, so stand down. */
    fun onMove(viewport: Pt) {
        val a = anchor ?: return
        if (viewport.distanceTo(a) > SLOP) end()
    }

    /** The contact ended or became something else. */
    fun end() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        anchor = null
    }

    companion object {
        /** How long the tip has to rest, matching the shape snap's dwell. */
        const val HOLD_MS = 500L

        /** Pixels the tip may drift while resting; a little above the long press's, for nib jitter. */
        const val SLOP = 8.0
    }
}
