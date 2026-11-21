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

    /** OpenCode Go and similar want one stable id per conversation. */
    private var sessionId = UUID.randomUUID().toString()

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
        val answer = AiTurn(Role.ASSISTANT, "").apply { streaming = true }
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
                    if (final.answer.isBlank() && !thisCall.cancelled) answer.error = "模型没有返回内容"
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

    private companion object {
        fun versionName(context: Context): String = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
    }
}
