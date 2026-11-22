package com.nextnotes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.Socket
import java.util.Base64
import kotlin.concurrent.thread

class ChatGptAuthTest {
    @Test fun theChallengeIsTheRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ChatGptAuth.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun onlyTheCallbackPathCarriesAQuery() {
        assertEquals(mapOf("code" to "a b", "state" to "s"), ChatGptAuth.queryOf("GET /auth/callback?code=a%20b&state=s HTTP/1.1"))
        assertNull(ChatGptAuth.queryOf("GET /favicon.ico HTTP/1.1"))
    }

    @Test fun theIdTokenPayloadIsRead() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"email\":\"a@b.c\"}".toByteArray())
        assertEquals("a@b.c", ChatGptAuth.claimsOf("h.$payload.s")!!.getString("email"))
    }

    @Test fun anAccountSurvivesItsEncoding() {
        val a = ChatGptAccount("urn:uuid:1", "oaiapp_x", "e", "i", "acc", "ref", 42, listOf("openid", ChatGptAuth.USE_SCOPE))
        assertEquals(a, ChatGptAccount.decode(a.encode()))
        assertTrue(a.signedIn)
    }

    @Test fun aFirstSignInRegistersAndAForgedStateIsRefused() {
        var stored: String? = null
        val auth = ChatGptAuth({ stored }, { stored = it })
        val login = auth.begin()
        assertTrue(login.url.startsWith(ChatGptAuth.AUTHORIZE + "?client_id=dynamic_agent_client&agent_name_hint=NextNotes"))
        assertTrue(login.url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A"))
        assertTrue(login.url.contains("chatgpt.tokens.use.direct"))
        val port = login.server.localPort
        var page = ""
        val browser = thread {
            Socket("127.0.0.1", port).use { s ->
                s.getOutputStream().write("GET /auth/callback?code=c&state=forged HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                page = s.getInputStream().bufferedReader().readText()
            }
        }
        try {
            auth.finish(login, 5_000, "app://done")
            fail("expected a refusal")
        } catch (e: AiException) {
            assertTrue(e.message!!.contains("状态不匹配"))
        }
        browser.join()
        assertTrue(page.startsWith("HTTP/1.1 200"))
        assertTrue(page.contains("app://done"))
        assertTrue(ChatGptAccount.decode(stored)!!.hostId.startsWith("urn:uuid:"))
    }
}
