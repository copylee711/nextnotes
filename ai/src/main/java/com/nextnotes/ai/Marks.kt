package com.nextnotes.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * One place the AI marked on the picture it was shown: a box in fractions of the picture's width
 * and height (0 to 1, from the top-left), what kind of mark, and a few words to write beside it.
 */
data class Mark(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val kind: Kind,
    val note: String,
) {
    enum class Kind { WRONG, RIGHT, NOTE }
}

/**
 * Marks ride at the end of an answer as a fenced `marks` block of JSON, boxes in 0–1000 of the
 * picture, the scale vision models are trained to point in:
 *
 *     ```marks
 *     [{"box": [120, 340, 560, 410], "kind": "wrong", "note": "17×3 应为 51"}]
 *     ```
 *
 * [split] takes the block out of the answer, so what is shown reads as prose, and parses it
 * forgivingly: a model that gets one entry wrong still has the others drawn.
 */
object Marks {
    private val block = Regex("```marks[^\\n]*\\n([\\s\\S]*?)(```|$)")

    /** How the AI is asked for marks; added to a request that wants them. */
    const val INSTRUCTION =
        "回答的最后，另起一行附上一个 marks 代码块（```marks 开头），内容是 JSON 数组，标出图中需要批注的位置，" +
            "每项形如 {\"box\": [x1, y1, x2, y2], \"kind\": \"wrong\" 或 \"right\" 或 \"note\", \"note\": \"不超过 12 个字的批语\"}。" +
            "box 是该处在图中的矩形，坐标按图片宽高归一化到 0–1000，原点在左上角，框住出错或要说明的那一步即可，不要框整页。" +
            "错误用 wrong，关键的正确步骤可用 right，其余提醒用 note；没有需要批注的地方就给空数组。正文里不要提到这个代码块。"

    /** The answer without its marks block (an unfinished one too, while it streams), and the marks it held. */
    fun split(answer: String): Pair<String, List<Mark>> {
        val m = block.find(answer) ?: return answer to emptyList()
        val shown = (answer.substring(0, m.range.first) + answer.substring(m.range.last + 1)).trimEnd()
        val complete = m.groupValues[2] == "```"
        return shown to (if (complete) parse(m.groupValues[1]) else emptyList())
    }

    fun parse(json: String): List<Mark> {
        val text = json.trim()
        val array = runCatching {
            if (text.startsWith("{")) JSONObject(text).optJSONArray("marks") ?: JSONArray() else JSONArray(text)
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<Mark>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val box = o.optJSONArray("box") ?: continue
            if (box.length() != 4) continue
            val v = DoubleArray(4) { box.optDouble(it, Double.NaN) }
            if (v.any { it.isNaN() }) continue
            // Accept 0–1 fractions too, which some models give despite being asked for 0–1000.
            val scale = if (v.all { it in 0.0..1.0 }) 1.0 else 1000.0
            val x1 = (minOf(v[0], v[2]) / scale).coerceIn(0.0, 1.0)
            val x2 = (maxOf(v[0], v[2]) / scale).coerceIn(0.0, 1.0)
            val y1 = (minOf(v[1], v[3]) / scale).coerceIn(0.0, 1.0)
            val y2 = (maxOf(v[1], v[3]) / scale).coerceIn(0.0, 1.0)
            if (x2 - x1 <= 0.0 || y2 - y1 <= 0.0) continue
            val kind = when (o.optString("kind").lowercase()) {
                "wrong", "error", "错" -> Mark.Kind.WRONG
                "right", "ok", "correct", "对" -> Mark.Kind.RIGHT
                else -> Mark.Kind.NOTE
            }
            out += Mark(x1, y1, x2, y2, kind, o.optString("note").trim().take(40))
        }
        return out
    }
}
