package com.nextnotes.ai

/** The wire protocol a provider speaks. */
enum class Protocol(val id: String) {
    /** `POST {base}/chat/completions`, the de facto standard most providers and local servers speak. */
    OPENAI("openai"),

    /** Anthropic's Messages API, `POST {base}/v1/messages`. */
    ANTHROPIC("anthropic"),

    /** OpenAI's Responses API on the user's ChatGPT plan, signed in with ChatGPT instead of a key. */
    CHATGPT("chatgpt");

    companion object {
        fun fromId(id: String?): Protocol = entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

/**
 * One user-configured AI service. Everything is the user's choice; [AiPresets] only pre-fills the
 * fields. The API key is not part of this: it lives encrypted in [KeyVault] under [id].
 */
data class AiProfile(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val baseUrl: String,
    val model: String,
    /** Send a stable `x-opencode-session` header per conversation, which OpenCode Go requires. */
    val opencodeSession: Boolean = false,
    /** The most an answer may run to; 0 leaves it to the model's own limit. */
    val maxTokens: Int = 0,
) {
    companion object {
        /** Anthropic's API will not go without a limit; this one is under every current model's ceiling. */
        const val ANTHROPIC_FALLBACK = 32000
    }
}

/** Starting points for the profile editor. */
data class AiPreset(
    val name: String,
    val protocol: Protocol,
    val baseUrl: String,
    val model: String,
    val opencodeSession: Boolean = false,
)

object AiPresets {
    val all = listOf(
        AiPreset("OpenAI", Protocol.OPENAI, "https://api.openai.com/v1", ""),
        AiPreset("DeepSeek", Protocol.OPENAI, "https://api.deepseek.com", "deepseek-flash"),
        AiPreset("OpenCode Go", Protocol.OPENAI, "https://opencode.ai/zen/go/v1", "deepseek-v4.1-flash", opencodeSession = true),
        AiPreset("Claude", Protocol.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-5-5"),
        AiPreset("通义千问（百炼）", Protocol.OPENAI, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3-vl-plus"),
        // On a tablet, localhost is the tablet itself: replace it with the computer's LAN address.
        AiPreset("Ollama（局域网）", Protocol.OPENAI, "http://localhost:11434/v1", ""),
        AiPreset("自定义", Protocol.OPENAI, "", ""),
    )
}

enum class Role { USER, ASSISTANT }

sealed interface Part {
    data class Text(val text: String) : Part

    /** An image as base64 without the data-URL prefix. */
    data class Image(val base64: String, val mimeType: String = "image/png") : Part
}

data class ChatMessage(val role: Role, val parts: List<Part>) {
    val text: String get() = parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text }

    companion object {
        fun user(text: String, vararg images: Part.Image) = ChatMessage(Role.USER, listOf(Part.Text(text)) + images)
        fun assistant(text: String) = ChatMessage(Role.ASSISTANT, listOf(Part.Text(text)))
    }
}

/** What streaming delivered so far: the answer, and the model's visible reasoning when it sends any. */
data class AiDelta(
    val answer: String,
    val reasoning: String,
    /** The model stopped because it reached max_tokens, not because it was done. */
    val truncated: Boolean = false,
)

class AiException(message: String, val httpCode: Int? = null) : Exception(message)
