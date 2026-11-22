package com.nextnotes.ai

import org.json.JSONArray
import org.json.JSONObject

/** What a stream has delivered so far. */
class StreamState {
    val answer = StringBuilder()
    val reasoning = StringBuilder()
    var done = false
    var truncated = false

    /**
     * The delta as shown: some OpenAI-compatible models put their reasoning inline as a leading
     * `<think>…</think>` block, which is moved over to the reasoning.
     */
    fun snapshot(): AiDelta {
        val (thinking, rest) = splitThinking(answer.toString())
        val reasoningText = if (thinking.isEmpty()) reasoning.toString() else reasoning.toString() + thinking
        return AiDelta(rest, reasoningText, truncated)
    }
}

internal fun splitThinking(text: String): Pair<String, String> {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("<think>")) return "" to text
    val end = trimmed.indexOf("</think>")
    return if (end < 0) {
        trimmed.removePrefix("<think>") to "" // still thinking
    } else {
        trimmed.substring("<think>".length, end) to trimmed.substring(end + "</think>".length).trimStart()
    }
}

/** One provider dialect: where to send, what to send, and how to read what comes back. */
interface WireProtocol {
    fun endpoint(baseUrl: String): String
    fun headers(apiKey: String): Map<String, String>
    fun body(profile: AiProfile, system: String, messages: List<ChatMessage>, stream: Boolean): JSONObject

    /** Fold one SSE event into [state]; throw [AiException] on an error event. */
    fun onEvent(event: String?, data: String, state: StreamState)

    /** Read a whole (non-streamed) response into [state]. */
    fun onFull(json: JSONObject, state: StreamState)

    /** A plainer body to try once when [body] was refused with [status] and [error], or null. */
    fun relaxed(body: JSONObject, status: Int, error: String): JSONObject? = null

    companion object {
        fun of(protocol: Protocol): WireProtocol = when (protocol) {
            Protocol.OPENAI -> OpenAiProtocol
            Protocol.ANTHROPIC -> AnthropicProtocol
            Protocol.CHATGPT -> ResponsesProtocol
        }
    }
}

/** The message from a provider's error body, which most shape as `{"error": {"message": …}}`. */
internal fun errorMessageOf(body: String): String {
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return body.take(600)
    val error = json.opt("error")
    val message = when (error) {
        is JSONObject -> error.optString("message").ifBlank { error.optString("type") }
        is String -> error
        else -> json.optString("message")
    }
    return message.ifBlank { body }.take(600)
}

object OpenAiProtocol : WireProtocol {
    override fun endpoint(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
    }

    override fun headers(apiKey: String): Map<String, String> =
        if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")

    override fun body(profile: AiProfile, system: String, messages: List<ChatMessage>, stream: Boolean): JSONObject {
        val list = JSONArray()
        if (system.isNotBlank()) list.put(JSONObject().put("role", "system").put("content", system))
        for (m in messages) {
            val content = JSONArray()
            for (p in m.parts) when (p) {
                is Part.Text -> content.put(JSONObject().put("type", "text").put("text", p.text))
                is Part.Image -> content.put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:${p.mimeType};base64,${p.base64}")),
                )
            }
            val role = if (m.role == Role.USER) "user" else "assistant"
            // A text-only message goes as a plain string, which every server understands.
            val single = m.parts.singleOrNull() as? Part.Text
            list.put(JSONObject().put("role", role).put("content", single?.text ?: content))
        }
        return JSONObject()
            .put("model", profile.model)
            .put("messages", list)
            .put("stream", stream)
            .apply { if (profile.maxTokens > 0) put("max_tokens", profile.maxTokens) }
    }

    override fun onEvent(event: String?, data: String, state: StreamState) {
        if (data.trim() == "[DONE]") {
            state.done = true
            return
        }
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        json.optJSONObject("error")?.let { throw AiException(it.optString("message", "流式请求出错")) }
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return
        if (choice.stringOrEmpty("finish_reason") == "length") state.truncated = true
        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: return
        state.reasoning.append(delta.stringOrEmpty("reasoning_content").ifEmpty { delta.stringOrEmpty("reasoning") })
        state.answer.append(delta.stringOrEmpty("content"))
    }

    override fun onFull(json: JSONObject, state: StreamState) {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message") ?: throw AiException("响应里没有 choices[0].message")
        if (choice.stringOrEmpty("finish_reason") == "length") state.truncated = true
        state.reasoning.append(message.stringOrEmpty("reasoning_content").ifEmpty { message.stringOrEmpty("reasoning") })
        state.answer.append(message.stringOrEmpty("content"))
        state.done = true
    }
}

object AnthropicProtocol : WireProtocol {
    const val VERSION = "2023-06-01"

    override fun endpoint(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        return when {
            clean.endsWith("/messages") -> clean
            clean.endsWith("/v1") -> "$clean/messages"
            else -> "$clean/v1/messages"
        }
    }

    override fun headers(apiKey: String): Map<String, String> =
        mapOf("x-api-key" to apiKey, "anthropic-version" to VERSION)

    override fun body(profile: AiProfile, system: String, messages: List<ChatMessage>, stream: Boolean): JSONObject {
        val list = JSONArray()
        for (m in messages) {
            val content = JSONArray()
            // Images first: Anthropic recommends putting them before the question about them.
            for (p in m.parts.filterIsInstance<Part.Image>()) {
                content.put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject().put("type", "base64").put("media_type", p.mimeType).put("data", p.base64),
                    ),
                )
            }
            for (p in m.parts.filterIsInstance<Part.Text>()) {
                if (p.text.isNotEmpty()) content.put(JSONObject().put("type", "text").put("text", p.text))
            }
            list.put(JSONObject().put("role", if (m.role == Role.USER) "user" else "assistant").put("content", content))
        }
        val body = JSONObject()
            .put("model", profile.model)
            .put("max_tokens", profile.maxTokens.takeIf { it > 0 } ?: AiProfile.ANTHROPIC_FALLBACK)
            .put("messages", list)
            .put("stream", stream)
        if (system.isNotBlank()) body.put("system", system)
        return body
    }

    override fun onEvent(event: String?, data: String, state: StreamState) {
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        when (json.optString("type", event ?: "")) {
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text_delta" -> state.answer.append(delta.stringOrEmpty("text"))
                    "thinking_delta" -> state.reasoning.append(delta.stringOrEmpty("thinking"))
                }
            }
            "message_delta" -> if (json.optJSONObject("delta")?.stringOrEmpty("stop_reason") == "max_tokens") state.truncated = true
            "message_stop" -> state.done = true
            "error" -> throw AiException(json.optJSONObject("error")?.optString("message") ?: "流式请求出错")
        }
    }

    override fun onFull(json: JSONObject, state: StreamState) {
        val content = json.optJSONArray("content") ?: throw AiException("响应里没有 content")
        if (json.stringOrEmpty("stop_reason") == "max_tokens") state.truncated = true
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> state.answer.append(block.stringOrEmpty("text"))
                "thinking" -> state.reasoning.append(block.stringOrEmpty("thinking"))
            }
        }
        state.done = true
    }
}

/**
 * OpenAI's Responses API as Sign in with ChatGPT serves it: always streamed, never stored, the
 * system prompt as `instructions`, and none of the sampling or limit fields.
 */
object ResponsesProtocol : WireProtocol {
    override fun endpoint(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/').ifEmpty { ChatGptAuth.RESOURCE }
        return if (clean.endsWith("/responses")) clean else "$clean/responses"
    }

    override fun headers(apiKey: String): Map<String, String> = mapOf("Authorization" to "Bearer $apiKey")

    override fun body(profile: AiProfile, system: String, messages: List<ChatMessage>, stream: Boolean): JSONObject {
        val input = JSONArray()
        for (m in messages) {
            if (m.role == Role.ASSISTANT) {
                input.put(JSONObject().put("role", "assistant").put("content", m.text))
                continue
            }
            val content = JSONArray()
            for (p in m.parts) when (p) {
                is Part.Text -> if (p.text.isNotEmpty()) content.put(JSONObject().put("type", "input_text").put("text", p.text))
                is Part.Image -> content.put(
                    JSONObject().put("type", "input_image").put("image_url", "data:${p.mimeType};base64,${p.base64}"),
                )
            }
            input.put(JSONObject().put("role", "user").put("content", content))
        }
        return JSONObject()
            .put("model", profile.model)
            .put("input", input)
            .put("store", false)
            .put("stream", true)
            .put("reasoning", JSONObject().put("summary", "auto"))
            .apply { if (system.isNotBlank()) put("instructions", system) }
    }

    /** A model that will not summarize its reasoning still answers without being asked to. */
    override fun relaxed(body: JSONObject, status: Int, error: String): JSONObject? =
        if (status == 400 && body.has("reasoning") && error.contains("reasoning", ignoreCase = true)) {
            JSONObject(body.toString()).apply { remove("reasoning") }
        } else null

    override fun onEvent(event: String?, data: String, state: StreamState) {
        if (data.trim() == "[DONE]") {
            state.done = true
            return
        }
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        when (json.optString("type", event ?: "")) {
            "response.output_text.delta" -> state.answer.append(json.stringOrEmpty("delta"))
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> state.reasoning.append(json.stringOrEmpty("delta"))
            "response.reasoning_summary_part.done" -> if (state.reasoning.isNotEmpty()) state.reasoning.append("\n\n")
            "response.completed" -> state.done = true
            "response.incomplete" -> {
                state.truncated = true
                state.done = true
            }
            "response.failed" -> throw AiException(planError(json.optJSONObject("response")?.optJSONObject("error")))
            "error" -> throw AiException(planError(json.optJSONObject("error") ?: json))
        }
    }

    override fun onFull(json: JSONObject, state: StreamState) {
        json.optJSONObject("error")?.let { throw AiException(planError(it)) }
        val output = json.optJSONArray("output") ?: throw AiException("响应里没有 output")
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.optJSONObject(j) ?: continue
                if (c.optString("type") == "output_text") state.answer.append(c.stringOrEmpty("text"))
            }
        }
        state.done = true
    }

    /** What a plan-usage error means for the user, by its code. */
    fun planError(error: JSONObject?): String {
        val code = error?.optString("code").orEmpty()
        val message = error?.optString("message").orEmpty()
        return when (code) {
            "subscription_sharing_usage_limit_exceeded" -> "ChatGPT 套餐的用量已经用完，可以到 chatgpt.com/settings/usage 查看"
            "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" -> "ChatGPT 暂时不可用，稍后再试"
            "subscription_sharing_user_not_eligible" -> "这个 ChatGPT 账号不能用套餐额度（需要 Plus 或 Pro）"
            "subscription_sharing_invalid_user" -> "ChatGPT 登录已失效，请在 AI 设置里重新登录"
            "subscription_sharing_unsupported_capability" -> "这个模型不支持本次请求的内容" + (error?.optString("param")?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")
            else -> listOf(code, message).filter { it.isNotBlank() }.joinToString("：").ifBlank { "流式请求出错" }
        }
    }
}

/** A string field, or "" when it is missing or JSON null (optString would give "null"). */
private fun JSONObject.stringOrEmpty(key: String): String = (opt(key) as? String).orEmpty()
