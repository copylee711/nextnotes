package com.nextnotes.ai

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** A request in flight that another thread may [cancel]. */
class AiCall {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile var cancelled = false
        private set

    internal fun attach(c: HttpURLConnection) {
        connection = c
        if (cancelled) c.disconnect()
    }

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }
}

/**
 * Sends a conversation to a provider and streams the answer back. Blocking: call it off the main
 * thread. [onUpdate] gets the whole answer so far after every chunk.
 */
class AiClient(
    private val userAgent: String = "NextNotes/0.1",
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 120_000,
) {
    fun stream(
        profile: AiProfile,
        apiKey: String,
        system: String,
        messages: List<ChatMessage>,
        sessionId: String,
        call: AiCall = AiCall(),
        onUpdate: (AiDelta) -> Unit = {},
    ): AiDelta {
        if (profile.baseUrl.isBlank()) throw AiException("请先在 AI 设置里填写服务地址")
        if (profile.model.isBlank()) throw AiException("请先在 AI 设置里填写模型名称")
        val wire = WireProtocol.of(profile.protocol)
        val body = wire.body(profile, system, messages, stream = true)
        return try {
            send(profile, wire, body, apiKey, sessionId, call, onUpdate)
        } catch (e: AiException) {
            val plainer = e.httpCode?.let { wire.relaxed(body, it, e.message.orEmpty()) } ?: throw e
            send(profile, wire, plainer, apiKey, sessionId, call, onUpdate)
        }
    }

    private fun send(
        profile: AiProfile,
        wire: WireProtocol,
        body: JSONObject,
        apiKey: String,
        sessionId: String,
        call: AiCall,
        onUpdate: (AiDelta) -> Unit,
    ): AiDelta {
        val payload = body.toString().toByteArray(Charsets.UTF_8)
        val conn = try {
            URL(wire.endpoint(profile.baseUrl)).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("服务地址无效：${profile.baseUrl}")
        }
        call.attach(conn)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setFixedLengthStreamingMode(payload.size)
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "text/event-stream, application/json")
            conn.setRequestProperty("User-Agent", userAgent)
            for ((k, v) in wire.headers(apiKey)) conn.setRequestProperty(k, v)
            if (profile.opencodeSession) conn.setRequestProperty("x-opencode-session", sessionId)
            conn.outputStream.use { it.write(payload) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val body = runCatching { conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }
                    .getOrNull().orEmpty()
                val detail = when {
                    profile.protocol == Protocol.CHATGPT -> chatGptError(code, body)
                    else -> errorMessageOf(body).ifBlank { hintFor(code) }
                }
                throw AiException("HTTP $code：$detail", code)
            }
            val state = StreamState()
            val type = conn.contentType.orEmpty()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                if (type.contains("text/event-stream", ignoreCase = true)) {
                    val sse = SseReader { event, data ->
                        wire.onEvent(event, data, state)
                        onUpdate(state.snapshot())
                    }
                    while (!state.done) {
                        if (call.cancelled) break
                        val line = reader.readLine() ?: break
                        sse.line(line)
                    }
                    sse.finish()
                } else {
                    val text = reader.readText()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                        ?: throw AiException("无法解析服务返回的内容：${text.take(200)}")
                    wire.onFull(json, state)
                    onUpdate(state.snapshot())
                }
            }
            return state.snapshot()
        } catch (e: AiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiException("请求超时：模型 ${readTimeoutMs / 1000} 秒内没有响应")
        } catch (e: IOException) {
            if (call.cancelled) throw AiCancelled()
            throw AiException("网络请求失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }
}

/** What a bare status code most likely means, for providers that send no error body. */
internal fun hintFor(code: Int): String = when (code) {
    401 -> "API Key 无效或没有填写"
    403 -> "没有权限使用这个模型或服务"
    404 -> "地址或模型名称不对"
    429 -> "请求太频繁或额度用完了"
    in 500..599 -> "服务端出错，稍后再试"
    else -> "请求失败"
}

/** A refused ChatGPT-plan request, in the user's terms; the raw text stays in when it may say more. */
internal fun chatGptError(code: Int, body: String): String {
    val json = runCatching { JSONObject(body) }.getOrNull()
    val error = json?.optJSONObject("error")
    if (error != null) return ResponsesProtocol.planError(error)
    val detail = json?.optString("detail").orEmpty()
    return when (code) {
        401 -> "ChatGPT 登录已失效，请在 AI 设置里重新登录"
        403 -> "ChatGPT 拒绝了这次请求" + (if (detail.isNotBlank()) "：$detail" else "（可能是地区或权限限制）")
        429 -> "ChatGPT 套餐的用量已经用完，可以到 chatgpt.com/settings/usage 查看"
        else -> detail.ifBlank { errorMessageOf(body).ifBlank { hintFor(code) } }
    }
}

/** The user stopped the request; not an error to show. */
class AiCancelled : Exception("已停止")
