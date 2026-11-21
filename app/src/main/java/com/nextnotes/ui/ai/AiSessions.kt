package com.nextnotes.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

import com.nextnotes.ai.Role
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** A conversation as kept on disk: its session id and its turns, pictures as JPEG bytes. */
class SavedSession(val sessionId: String, val turns: List<SavedTurn>)

class SavedTurn(
    val role: Role,
    val text: String,
    val reasoning: String = "",
    val label: String? = null,
    val image: ByteArray? = null,
    val notice: String? = null,
)

/**
 * Each note's conversation with the assistant, kept in the app's own files under a hash of the
 * note's address, so it is there again when the note is reopened. Nothing leaves the device.
 */
class AiSessions(private val dir: File) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "ai_sessions"))

    private fun fileFor(key: String) = File(dir, sha1(key) + ".json")

    fun load(key: String): SavedSession? = runCatching {
        val f = fileFor(key)
        if (!f.exists()) null else decode(JSONObject(f.readText()))
    }.getOrNull()

    fun save(key: String, session: SavedSession) {
        if (session.turns.isEmpty()) return delete(key)
        dir.mkdirs()
        val f = fileFor(key)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(encode(session).toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    fun delete(key: String) {
        fileFor(key).delete()
    }

    companion object {
        /** The longest side a kept picture is shrunk to: enough to recognise the question by. */
        const val PICTURE_SIDE = 800

        fun encode(s: SavedSession): JSONObject = JSONObject()
            .put("version", 1)
            .put("session", s.sessionId)
            .put("turns", JSONArray().apply {
                for (t in s.turns) put(JSONObject().apply {
                    put("role", if (t.role == Role.USER) "user" else "assistant")
                    put("text", t.text)
                    if (t.reasoning.isNotEmpty()) put("reasoning", t.reasoning)
                    t.label?.let { put("label", it) }
                    t.notice?.let { put("notice", it) }
                    t.image?.let { put("image", java.util.Base64.getEncoder().encodeToString(it)) }
                })
            })

        fun decode(o: JSONObject): SavedSession {
            val arr = o.optJSONArray("turns") ?: JSONArray()
            val turns = (0 until arr.length()).mapNotNull { i ->
                val t = arr.optJSONObject(i) ?: return@mapNotNull null
                SavedTurn(
                    role = if (t.optString("role") == "user") Role.USER else Role.ASSISTANT,
                    text = t.optString("text"),
                    reasoning = t.optString("reasoning", ""),
                    label = t.optString("label").ifEmpty { null },
                    image = t.optString("image").ifEmpty { null }?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() },
                    notice = t.optString("notice").ifEmpty { null },
                )
            }
            return SavedSession(o.optString("session"), turns)
        }

        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        fun jpeg(bitmap: Bitmap): ByteArray {
            val scale = PICTURE_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            } else bitmap
            // JPEG has no alpha: lay the picture on white, as the note's paper is.
            val flat = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(flat).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(small, 0f, 0f, null) }
            return ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        }

        fun picture(bytes: ByteArray): Bitmap? = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
}
