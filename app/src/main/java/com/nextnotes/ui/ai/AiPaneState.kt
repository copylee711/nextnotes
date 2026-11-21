package com.nextnotes.ui.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nextnotes.ai.AiCall
import com.nextnotes.ai.AiCancelled
import com.nextnotes.ai.AiClient
import com.nextnotes.ai.AiException
import com.nextnotes.ai.AiProfile
import com.nextnotes.ai.ChatMessage
import com.nextnotes.ai.KeyVault
import com.nextnotes.ai.Part
import com.nextnotes.ai.ProfileStore
import com.nextnotes.ai.Prompts
import com.nextnotes.ai.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** One turn of the conversation as shown: the text, the note picture that went with it, and state. */
@Stable
class AiTurn(val role: Role, text: String, val snapshot: Bitmap? = null, val snapshotLabel: String? = null) {
    var text by mutableStateOf(text)
    var reasoning by mutableStateOf("")
    var streaming by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** Something to know about an answer that still stands, such as it being cut short. */
    var notice by mutableStateOf<String?>(null)
    /** The picture an answer was given about, which its marks are placed by. */
    var source: AiSnapshot? = null
    /** How many of the answer's marks went on the page; null until they are put there. */
    var placed by mutableStateOf<Int?>(null)
    /** [snapshot] as the JPEG kept on disk, once made. */
    @Volatile var kept: ByteArray? = null
}

/** Handwriting being turned into text: the picture of it, what came back so far, and how it went. */
@Stable
class Transcript(val shot: AiSnapshot) {
    var text by mutableStateOf("")
    var busy by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
}

/** Whether a question takes a picture of the note along. */
enum class Attach(val label: String) { AUTO("附带笔记"), NONE("不附带") }

/**
 * The assistant beside one editor pane: its conversation, the request in flight, and the settings
 * it reads. Compose observes the fields; requests run on a background thread.
 */
@Stable
class AiPaneState(context: Context) {
    private val app = context.applicationContext
    val profiles = ProfileStore(app)
    val keys = KeyVault(app)
    private val client = AiClient(userAgent = "NextNotes/${versionName(app)}")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var open by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var input by mutableStateOf("")
    var attach by mutableStateOf(Attach.AUTO)
    val turns = mutableStateListOf<AiTurn>()
    var busy by mutableStateOf(false)
        private set

    /** Bumped when profiles change, so the header re-reads the active one. */
    var profilesVersion by mutableStateOf(0)
        private set

    /** The preview of what would be sent now, refreshed when the panel opens or asks. */
    var preview by mutableStateOf<AiSnapshot?>(null)
        private set

    private var job: Job? = null
    private var call: AiCall? = null

    /** The handwriting being turned into text, while its dialog is up. */
    var transcript by mutableStateOf<Transcript?>(null)
        private set
    private var transcribeCall: AiCall? = null

    /** Read the handwriting in [shot] with the active model, filling [transcript] as it streams. */
    fun transcribe(shot: AiSnapshot?) {
        if (shot == null) return
        val profile = profiles.active()
        if (profile == null) {
            open = true
            settingsOpen = true
            return
        }
        transcribeCall?.cancel()
        val t = Transcript(shot).also { transcript = it }
        val thisCall = AiCall().also { transcribeCall = it }
        scope.launch {
            try {
                val image = withContext(Dispatchers.Default) { shot.toPart() }
                val key = keys.get(profile.id)
                val final = withContext(Dispatchers.IO) {
                    client.stream(profile, key, "", listOf(ChatMessage.user(Prompts.transcribe, image)), UUID.randomUUID().toString(), thisCall) { d ->
                        scope.launch { if (transcript === t) t.text = d.answer.trim() }
                    }
                }
                t.text = final.answer.trim()
                if (t.text.isBlank() && !thisCall.cancelled) t.error = "没有认出文字"
            } catch (_: AiCancelled) {
            } catch (e: AiException) {
                t.error = e.message
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                t.error = e.message ?: e.javaClass.simpleName
            } finally {
                t.busy = false
            }
        }
    }

    fun closeTranscript() {
        transcribeCall?.cancel()
        transcribeCall = null
        transcript = null
    }

    /** OpenCode Go and similar want one stable id per conversation. */
    private var sessionId = UUID.randomUUID().toString()

    private val sessions = AiSessions(app)

    /** The note whose conversation this is; each note keeps its own. */
    private var noteKey: String? = null

    /**
     * Switch to the conversation of the note at [key] (null: no note open). The one shown so far
     * is kept for its own note first, and the new note's comes back as it was left.
     */
    fun bindNote(key: String?) {
        if (key == noteKey) return
        stop()
        persist()
        turns.clear()
        sessionId = UUID.randomUUID().toString()
        noteKey = key
        if (key == null) return
        scope.launch {
            val saved = withContext(Dispatchers.IO) { sessions.load(key) } ?: return@launch
            if (noteKey != key || turns.isNotEmpty()) return@launch
            if (saved.sessionId.isNotEmpty()) sessionId = saved.sessionId
            turns += saved.turns.map { t ->
                AiTurn(t.role, t.text, t.image?.let { AiSessions.picture(it) }, t.label).apply {
                    reasoning = t.reasoning
                    notice = t.notice
                    kept = t.image
                }
            }
        }
    }

    /** Write the conversation down for its note, off the main thread. */
    private fun persist() {
        val key = noteKey ?: return
        val id = sessionId
        val kept = turns.filter { it.error == null && it.text.isNotBlank() }
        scope.launch(Dispatchers.IO) {
            runCatching {
                sessions.save(key, SavedSession(id, kept.map { t ->
                    val image = t.kept ?: t.snapshot?.let { AiSessions.jpeg(it) }?.also { t.kept = it }
                    SavedTurn(t.role, t.text, t.reasoning, t.snapshotLabel, image, t.notice)
                }))
            }
        }
    }

    fun activeProfile(): AiProfile? {
        @Suppress("UNUSED_EXPRESSION") profilesVersion // read so callers recompose on change
        return profiles.active()
    }

    fun profilesChanged() {
        profilesVersion++
    }

    fun refreshPreview(capture: () -> AiSnapshot?) {
        preview = if (attach == Attach.NONE) null else capture()
    }

    fun toggle(capture: () -> AiSnapshot?) {
        open = !open
        if (open) refreshPreview(capture)
    }

    fun show(capture: () -> AiSnapshot?) {
        open = true
        refreshPreview(capture)
    }

    fun newConversation() {
        stop()
        turns.clear()
        noteKey?.let { key -> scope.launch(Dispatchers.IO) { sessions.delete(key) } }
        sessionId = UUID.randomUUID().toString()
    }

    fun stop() {
        call?.cancel()
        job?.cancel()
        call = null
        job = null
        turns.lastOrNull()?.streaming = false
        busy = false
    }

    /**
     * Ask [question] (or the typed input). With [attach] on, the note is captured at this moment,
     * so the AI sees the page as it is now, selection first.
     */
    fun send(question: String? = null, capture: () -> AiSnapshot?) {
        if (busy) return
        val profile = profiles.active()
        if (profile == null) {
            settingsOpen = true
            return
        }
        val text = (question ?: input).trim().ifEmpty { Prompts.defaultQuestion }
        if (question == null) input = ""
        val snapshot = if (attach == Attach.AUTO) capture() else null
        preview = snapshot

        val userTurn = AiTurn(Role.USER, text, snapshot?.bitmap, snapshot?.kind?.label)
        val answer = AiTurn(Role.ASSISTANT, "").apply { streaming = true; source = snapshot }
        // The history goes as text; only this turn carries a picture, which keeps requests small.
        val history = turns.filter { it.error == null && it.text.isNotBlank() }.map {
            if (it.role == Role.USER) ChatMessage.user(it.text) else ChatMessage.assistant(it.text)
        }
        turns += userTurn
        turns += answer
        busy = true
        val thisCall = AiCall().also { call = it }
        job = scope.launch {
            try {
                val image = snapshot?.let { withContext(Dispatchers.Default) { it.toPart() } }
                val parts = listOf(Part.Text(text)) + listOfNotNull(image)
                val messages = history + ChatMessage(Role.USER, parts)
                val key = keys.get(profile.id)
                withContext(Dispatchers.IO) {
                    client.stream(profile, key, Prompts.system, messages, sessionId, thisCall) { delta ->
                        scope.launch {
                            answer.text = delta.answer
                            answer.reasoning = delta.reasoning
                        }
                    }
                }.let { final ->
                    answer.text = final.answer
                    answer.reasoning = final.reasoning
                    when {
                        thisCall.cancelled -> {}
                        final.answer.isBlank() && final.truncated -> answer.error =
                            "模型还没开始回答就用完了输出上限（${limitText(profile)}，思考过程也算在内）。" + raiseHint(profile)
                        final.answer.isBlank() -> answer.error = "模型没有返回内容"
                        final.truncated -> answer.notice = "回答达到输出上限（${limitText(profile)}），后面被截断了。" + raiseHint(profile)
                    }
                }
            } catch (_: AiCancelled) {
            } catch (e: AiException) {
                answer.error = e.message
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                answer.error = e.message ?: e.javaClass.simpleName
            } finally {
                answer.streaming = false
                if (call === thisCall) {
                    busy = false
                    call = null
                    persist()
                }
            }
        }
    }

    /** Send a short text-only request to check a profile's address, model and key. */
    suspend fun test(profile: AiProfile, apiKey: String): String = withContext(Dispatchers.IO) {
        try {
            val r = client.stream(profile, apiKey, "", listOf(ChatMessage.user("只回复两个字：成功")), UUID.randomUUID().toString())
            "连接成功：${r.answer.trim().take(40)}"
        } catch (e: AiException) {
            "连接失败：${e.message}"
        } catch (e: Exception) {
            "连接失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun limitText(p: AiProfile) = if (p.maxTokens > 0) "${p.maxTokens} tokens" else "模型的默认值"

    private fun raiseHint(p: AiProfile) =
        if (p.maxTokens > 0) "可以在 AI 设置里调大“最大输出 tokens”，或留空用模型的默认值。" else "可以在 AI 设置里填一个更大的“最大输出 tokens”。"

    private companion object {
        fun versionName(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
    }
}
