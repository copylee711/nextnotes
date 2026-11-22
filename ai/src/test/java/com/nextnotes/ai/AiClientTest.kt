package com.nextnotes.ai

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Drives the client against a local server speaking each dialect. */
class AiClientTest {
    private lateinit var server: ServerSocket
    @Volatile private var lastHeaders: Map<String, String> = emptyMap()
    @Volatile private var lastBody = ""
    @Volatile private var lastPath = ""
    private var reply: (path: String) -> Triple<Int, String, String> = { Triple(200, "text/plain", "") }

    /** A one-request-at-a-time HTTP/1.1 server; android.jar has no com.sun.net.httpserver. */
    @Before fun start() {
        server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { s ->
                    val input = BufferedInputStream(s.getInputStream())
                    val requestLine = readLine(input)
                    lastPath = requestLine.split(" ").getOrElse(1) { "" }
                    val headers = HashMap<String, String>()
                    while (true) {
                        val line = readLine(input)
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                    }
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(body, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    lastHeaders = headers
                    lastBody = body.toString(Charsets.UTF_8)
                    val (code, type, text) = reply(lastPath)
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 $code X\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    s.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
                }
            }
        }
    }

    private fun readLine(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("UTF-8")
    }

    @After fun stop() = server.close()

    private val base get() = "http://127.0.0.1:${server.localPort}"

    @Test fun streamsAnOpenAiAnswerAndSendsTheSessionHeader() {
        reply = {
            assertEquals("/v1/chat/completions", it)
            Triple(
                200, "text/event-stream",
                "data: {\"choices\":[{\"delta\":{\"content\":\"39\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"1\"}}]}\n\n" +
                    "data: [DONE]\n\n",
            )
        }
        val profile = AiProfile("p", "go", Protocol.OPENAI, "$base/v1", "m", opencodeSession = true)
        val seen = mutableListOf<String>()
        val result = AiClient(userAgent = "NextNotes/test").stream(
            profile, "sk-test", "sys", listOf(ChatMessage.user("17×23?")), sessionId = "s-1",
        ) { seen += it.answer }
        assertEquals("391", result.answer)
        assertEquals(listOf("39", "391", "391"), seen)
        assertEquals("Bearer sk-test", lastHeaders["authorization"])
        assertEquals("s-1", lastHeaders["x-opencode-session"])
        assertEquals("NextNotes/test", lastHeaders["user-agent"])
        assertTrue(lastBody.contains("\"stream\":true"))
    }

    @Test fun streamsAnAnthropicAnswer() {
        reply = {
            assertEquals("/v1/messages", it)
            Triple(
                200, "text/event-stream",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"对\"}}\n\n" +
                    "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n",
            )
        }
        val profile = AiProfile("p", "c", Protocol.ANTHROPIC, base, "claude")
        val result = AiClient().stream(profile, "k", "", listOf(ChatMessage.user("?")), "s")
        assertEquals("对", result.answer)
        assertEquals("k", lastHeaders["x-api-key"])
        assertEquals(AnthropicProtocol.VERSION, lastHeaders["anthropic-version"])
    }

    @Test fun readsANonStreamedReply() {
        reply = { Triple(200, "application/json", "{\"choices\":[{\"message\":{\"content\":\"好\"}}]}") }
        val profile = AiProfile("p", "o", Protocol.OPENAI, base, "m")
        assertEquals("好", AiClient().stream(profile, "", "", listOf(ChatMessage.user("?")), "s").answer)
        assertTrue("no key, no auth header", lastHeaders["authorization"] == null)
    }

    @Test fun httpErrorsCarryTheProvidersMessage() {
        reply = { Triple(400, "application/json", "{\"error\":{\"message\":\"model not found\"}}") }
        val profile = AiProfile("p", "o", Protocol.OPENAI, base, "m")
        try {
            AiClient().stream(profile, "k", "", listOf(ChatMessage.user("?")), "s")
            fail("expected an error")
        } catch (e: AiException) {
            assertEquals(400, e.httpCode)
            assertEquals("HTTP 400：model not found", e.message)
        }
    }

    @Test fun aBareStatusGetsAHint() {
        // The JDK's connection hides a 401 body in streaming mode; Android's does not, but either way
        // the user is told what a 401 means.
        reply = { Triple(401, "application/json", "") }
        val profile = AiProfile("p", "o", Protocol.OPENAI, base, "m")
        try {
            AiClient().stream(profile, "bad", "", listOf(ChatMessage.user("?")), "s")
            fail("expected an error")
        } catch (e: AiException) {
            assertEquals(401, e.httpCode)
            assertEquals("HTTP 401：API Key 无效或没有填写", e.message)
        }
    }

    @Test fun streamsAChatGptAnswerFromTheResponsesApi() {
        reply = {
            assertEquals("/v1/responses", it)
            Triple(
                200, "text/event-stream",
                "event: response.reasoning_summary_text.delta\ndata: {\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"想一想\"}\n\n" +
                    "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"答\"}\n\n" +
                    "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"案\"}\n\n" +
                    "event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":{}}\n\n",
            )
        }
        val profile = AiProfile("p", "ChatGPT", Protocol.CHATGPT, "$base/v1", "gpt")
        val result = AiClient().stream(profile, "tok", "sys", listOf(ChatMessage.user("?", Part.Image("AAAA"))), "s")
        assertEquals("答案", result.answer)
        assertEquals("想一想", result.reasoning)
        assertEquals("Bearer tok", lastHeaders["authorization"])
        val body = org.json.JSONObject(lastBody)
        assertEquals(false, body.getBoolean("store"))
        assertEquals("sys", body.getString("instructions"))
        val content = body.getJSONArray("input").getJSONObject(0).getJSONArray("content")
        assertEquals("input_text", content.getJSONObject(0).getString("type"))
        assertEquals("data:image/png;base64,AAAA", content.getJSONObject(1).getString("image_url"))
    }

    @Test fun aRefusedReasoningSummaryIsDroppedAndAskedAgain() {
        var calls = 0
        reply = {
            calls++
            if (org.json.JSONObject(lastBody).has("reasoning")) {
                Triple(400, "application/json", "{\"error\":{\"code\":\"subscription_sharing_unsupported_capability\",\"param\":\"reasoning\"}}")
            } else {
                Triple(200, "text/event-stream", "data: {\"type\":\"response.output_text.delta\",\"delta\":\"好\"}\n\ndata: {\"type\":\"response.completed\"}\n\n")
            }
        }
        val profile = AiProfile("p", "ChatGPT", Protocol.CHATGPT, "$base/v1", "gpt")
        assertEquals("好", AiClient().stream(profile, "tok", "", listOf(ChatMessage.user("?")), "s").answer)
        assertEquals(2, calls)
    }

    @Test fun eventsAreReadEvenWhenTheContentTypeHidesThem() {
        reply = {
            Triple(200, "application/json", "event: response.created\ndata: {\"type\":\"response.created\"}\n\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"好\"}\n\ndata: {\"type\":\"response.completed\"}\n\n")
        }
        val profile = AiProfile("p", "ChatGPT", Protocol.CHATGPT, "$base/v1", "gpt")
        assertEquals("好", AiClient().stream(profile, "tok", "", listOf(ChatMessage.user("?")), "s").answer)
    }

    @Test fun aSpentPlanSaysSo() {
        reply = {
            Triple(200, "text/event-stream", "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n")
        }
        val profile = AiProfile("p", "ChatGPT", Protocol.CHATGPT, "$base/v1", "gpt")
        try {
            AiClient().stream(profile, "tok", "", listOf(ChatMessage.user("?")), "s")
            fail("expected an error")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("用量已经用完"))
        }
    }

    @Test(expected = AiException::class)
    fun aMissingModelIsCaughtBeforeSending() {
        AiClient().stream(AiProfile("p", "o", Protocol.OPENAI, base, ""), "", "", emptyList(), "s")
    }
}
