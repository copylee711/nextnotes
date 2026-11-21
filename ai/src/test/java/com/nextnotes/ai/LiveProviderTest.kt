package com.nextnotes.ai

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Base64
import java.util.UUID

/**
 * Talks to a real provider with the same client the app uses. Skipped unless asked for, since it
 * spends tokens and needs a key:
 *
 *     NEXTNOTES_LIVE_TEST=1 OPENCODE_GO_API_KEY=… gradlew :ai:testDebugUnitTest --tests '*LiveProviderTest*'
 *
 * The key is read from the environment only; it is never written anywhere.
 */
class LiveProviderTest {
    private val key = System.getenv("OPENCODE_GO_API_KEY").orEmpty()
    private val model = System.getenv("NEXTNOTES_LIVE_MODEL") ?: "deepseek-v4.1-flash"

    private val profile = AiProfile(
        id = "live", name = "OpenCode Go", protocol = Protocol.OPENAI,
        baseUrl = "https://opencode.ai/zen/go/v1", model = model, opencodeSession = true, maxTokens = 1024,
    )

    private fun requireLive() {
        assumeTrue("set NEXTNOTES_LIVE_TEST=1 to run", System.getenv("NEXTNOTES_LIVE_TEST") == "1")
        assumeTrue("OPENCODE_GO_API_KEY is not set", key.isNotBlank())
    }

    @Test fun checksHandwrittenWorkingFromAnImage() {
        requireLive()
        val png = javaClass.classLoader!!.getResourceAsStream("hand_17x23.png")!!.readBytes()
        val image = Part.Image(Base64.getEncoder().encodeToString(png), "image/png")
        val check = Prompts.quickActions.first { it.label == "检查过程" }.prompt
        var updates = 0
        val result = AiClient(userAgent = "NextNotes/live-test").stream(
            profile, key, Prompts.system, listOf(ChatMessage.user(check, image)), UUID.randomUUID().toString(),
        ) { updates++ }
        println("updates=$updates reasoning=${result.reasoning.length} chars\n--- answer ---\n${result.answer}")
        assertTrue("streamed in several chunks", updates > 3)
        // The working has 17×3 written as 41; a model that read the picture says the result is 391.
        assertTrue("finds the right result", result.answer.contains("391"))
        assertTrue("points at the wrong step", result.answer.contains("51"))
    }

    @Test fun makesAnOfflineArtifactWhenAPictureHelps() {
        requireLive()
        val ask = "用一个可交互的演示说明 y = a·sin(x) 中 a 对图像的影响。"
        val result = AiClient(userAgent = "NextNotes/live-test").stream(
            profile.copy(maxTokens = 32000), key, Prompts.system, listOf(ChatMessage.user(ask)), UUID.randomUUID().toString(),
        ) {}
        val block = Regex("```(\\w+)[^\\n]*\\n([\\s\\S]*?)```").findAll(result.answer)
            .mapNotNull { Artifacts.of(it.groupValues[1], it.groupValues[2]) }.firstOrNull()
        println("truncated=${result.truncated} reasoning=${result.reasoning.length} answer=${result.answer.length} chars")
        println("--- answer ---\n${result.answer}")
        assertTrue("answers with an artifact", block != null)
        println("title=${block!!.title}")
        // It runs offline, so it must not lean on a CDN.
        assertTrue("loads nothing from outside", !Regex("(src|href)\\s*=\\s*[\"']https?:").containsMatchIn(block.source))
    }
}
