package com.nextnotes.ui.ai

import android.graphics.Bitmap
import android.util.Base64
import com.nextnotes.ai.Part
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the AI is shown of the note: a picture of the selection or of what is on screen. [region]
 * is the content rectangle the picture covers and [source] the document it was taken from, so
 * marks the AI makes on the picture can be put back on the page.
 */
class AiSnapshot(
    val bitmap: Bitmap,
    val kind: Kind,
    val region: com.xnotes.core.geometry.Rect? = null,
    val source: Any? = null,
) {
    enum class Kind(val label: String) { SELECTION("选区"), VIEW("当前页面") }

    companion object {
        private const val MAX_SIDE = 2048
        private const val PNG_LIMIT = 1_500_000

        /** A selection is shown with a margin round it: models misread or drop what touches the edge. */
        fun around(selection: com.xnotes.core.geometry.Rect): com.xnotes.core.geometry.Rect =
            selection.outset(maxOf(selection.w, selection.h) * 0.08 + 8.0)
    }

    /**
     * The picture as an image part, its long side capped so a full-screen capture stays a
     * reasonable upload. PNG keeps thin handwriting crisp; a big capture falls back to JPEG.
     */
    fun toPart(maxSide: Int = MAX_SIDE): Part.Image {
        val long = max(bitmap.width, bitmap.height)
        val scaled = if (long <= maxSide) bitmap else {
            val f = maxSide.toFloat() / long
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1), true)
        }
        val png = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        if (png.size <= PNG_LIMIT) return Part.Image(Base64.encodeToString(png, Base64.NO_WRAP), "image/png")
        val jpeg = ByteArrayOutputStream().also { flatten(scaled).compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        return Part.Image(Base64.encodeToString(jpeg, Base64.NO_WRAP), "image/jpeg")
    }

    /** JPEG has no alpha: lay the capture over white first, or transparent areas turn black. */
    private fun flatten(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(src, 0f, 0f, null) }
        return out
    }
}
