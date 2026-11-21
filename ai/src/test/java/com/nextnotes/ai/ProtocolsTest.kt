package com.nextnotes.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolsTest {
    private val openai = AiProfile("1", "t", Protocol.OPENAI, "https://api.deepseek.com", "deepseek-flash")
    private val claude = AiProfile("2", "t", Protocol.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-5-5")
    private val question = ChatMessage.user("这步对吗？", Part.Image("QUJD"))

    @Test fun openAiEndpointsAppendChatCompletionsOnce() {
        assertEquals("https://api.deepseek.com/chat/completions", OpenAiProtocol.endpoint("https://api.deepseek.com/"))
        assertEquals("https://opencode.ai/zen/go/v1/chat/completions", OpenAiProtocol.endpoint("https://opencode.ai/zen/go/v1"))
        assertEquals("https://x/v1/chat/completions", OpenAiProtocol.endpoint("https://x/v1/chat/completions"))
    }

    @Test fun anthropicEndpointsLandOnV1Messages() {
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicProtocol.endpoint("https://api.anthropic.com"))
        assertEquals("https://proxy/v1/messages", AnthropicProtocol.endpoint("https://proxy/v1/"))
    }

    @Test fun openAiBodyCarriesSystemTextAndDataUrlImage() {
        val body = OpenAiProtocol.body(openai, "sys", listOf(question), stream = true)
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val content = messages.getJSONObject(1).getJSONArray("content")
        assertEquals("这步对吗？", content.getJSONObject(0).getString("text"))
        assertEquals("data:image/png;base64,QUJD", content.getJSONObject(1).getJSONObject("image_url").getString("url"))
        assertTrue(body.getBoolean("stream"))
    }

    @Test fun openAiTextOnlyMessagesAreSentAsPlainStrings() {
        val body = OpenAiProtocol.body(openai, "", listOf(ChatMessage.assistant("好")), stream = false)
        assertEquals("好", body.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test fun anthropicBodyPutsSystemOnTopAndImagesFirst() {
        val body = AnthropicProtocol.body(claude, "sys", listOf(question), stream = true)
        assertEquals("sys", body.getString("system"))
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("QUJD", content.getJSONObject(0).getJSONObject("source").getString("data"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
    }

    @Test fun openAiStreamGathersContentAndReasoning() {
        val s = StreamState()
        OpenAiProtocol.onEvent(null, """{"choices":[{"delta":{"reasoning_content":"想"}}]}""", s)
        OpenAiProtocol.onEvent(null, """{"choices":[{"delta":{"content":"答","reasoning_content":null}}]}""", s)
        OpenAiProtocol.onEvent(null, """{"choices":[{"delta":{"content":"案"}}]}""", s)
        assertFalse(s.done)
        OpenAiProtocol.onEvent(null, "[DONE]", s)
        assertTrue(s.done)
        assertEquals(AiDelta("答案", "想"), s.snapshot())
    }

    @Test(expected = AiException::class)
    fun openAiErrorEventThrows() {
        OpenAiProtocol.onEvent(null, """{"error":{"message":"bad key"}}""", StreamState())
    }

    @Test fun anthropicStreamReadsTextAndThinkingDeltas() {
        val s = StreamState()
        AnthropicProtocol.onEvent("content_block_delta", """{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"嗯"}}""", s)
        AnthropicProtocol.onEvent("content_block_delta", """{"type":"content_block_delta","delta":{"type":"text_delta","text":"对"}}""", s)
        AnthropicProtocol.onEvent("message_stop", """{"type":"message_stop"}""", s)
        assertTrue(s.done)
        assertEquals(AiDelta("对", "嗯"), s.snapshot())
    }

    @Test fun inlineThinkTagsMoveToReasoning() {
        val s = StreamState()
        s.answer.append("<think>先算</think>\n结果是 391")
        assertEquals(AiDelta("结果是 391", "先算"), s.snapshot())
        val open = StreamState().apply { answer.append("<think>还在想") }
        assertEquals(AiDelta("", "还在想"), open.snapshot())
    }

    @Test fun fullResponsesAreReadToo() {
        val s = StreamState()
        OpenAiProtocol.onFull(JSONObject("""{"choices":[{"message":{"content":"好"}}]}"""), s)
        assertEquals("好", s.snapshot().answer)
    }

    @Test fun errorMessagesComeOutOfTheirEnvelope() {
        assertEquals("Invalid key", errorMessageOf("""{"error":{"message":"Invalid key"}}"""))
        assertEquals("MissingSessionID", errorMessageOf("""{"type":"error","error":{"type":"MissingSessionID","message":""}}"""))
        assertEquals("plain", errorMessageOf("plain"))
    }

    @Test fun profilesRoundTrip() {
        val list = listOf(openai.copy(opencodeSession = true, maxTokens = 2000), claude)
        assertEquals(list, ProfileStore.decode(ProfileStore.encode(list)))
    }

    @Test fun runningOutOfTokensIsNoticedOnBothWires() {
        val o = StreamState()
        OpenAiProtocol.onEvent(null, """{"choices":[{"delta":{"reasoning_content":"想"},"finish_reason":null}]}""", o)
        assertFalse(o.snapshot().truncated)
        OpenAiProtocol.onEvent(null, """{"choices":[{"delta":{},"finish_reason":"length"}]}""", o)
        assertTrue(o.snapshot().truncated)

        val done = StreamState()
        OpenAiProtocol.onEvent(null, """{"choices":[{"delta":{"content":"好"},"finish_reason":"stop"}]}""", done)
        assertFalse(done.snapshot().truncated)

        val a = StreamState()
        AnthropicProtocol.onEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"max_tokens"}}""", a)
        assertTrue(a.snapshot().truncated)
        val full = StreamState()
        AnthropicProtocol.onFull(JSONObject("""{"content":[{"type":"text","text":"x"}],"stop_reason":"end_turn"}"""), full)
        assertFalse(full.snapshot().truncated)
    }
}
