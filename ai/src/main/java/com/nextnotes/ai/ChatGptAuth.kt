package com.nextnotes.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * What signing in with ChatGPT left: the client OpenAI registered for this install, and the tokens
 * that spend the user's ChatGPT plan. The tokens are gone after sign-out; the client stays, so the
 * next sign-in reauthorizes it instead of registering another.
 */
data class ChatGptAccount(
    val hostId: String,
    val clientId: String = "",
    val email: String = "",
    val idToken: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    /** When the access token runs out, in epoch milliseconds. */
    val expiresAt: Long = 0,
    val scopes: List<String> = emptyList(),
) {
    val signedIn: Boolean get() = refreshToken.isNotEmpty() && clientId.isNotEmpty()

    fun encode(): String = JSONObject()
        .put("hostId", hostId).put("clientId", clientId).put("email", email)
        .put("idToken", idToken).put("accessToken", accessToken).put("refreshToken", refreshToken)
        .put("expiresAt", expiresAt).put("scopes", JSONArray(scopes))
        .toString()

    companion object {
        fun decode(raw: String?): ChatGptAccount? {
            val o = runCatching { JSONObject(raw ?: return null) }.getOrNull() ?: return null
            val scopes = o.optJSONArray("scopes")
            return ChatGptAccount(
                hostId = o.optString("hostId").ifBlank { return null },
                clientId = o.optString("clientId"),
                email = o.optString("email"),
                idToken = o.optString("idToken"),
                accessToken = o.optString("accessToken"),
                refreshToken = o.optString("refreshToken"),
                expiresAt = o.optLong("expiresAt"),
                scopes = (0 until (scopes?.length() ?: 0)).map { scopes!!.optString(it) },
            )
        }
    }
}

/** One sign-in attempt: the page to open, and what its answer is checked against. */
class ChatGptLogin internal constructor(
    val url: String,
    internal val redirectUri: String,
    internal val verifier: String,
    internal val state: String,
    internal val nonce: String,
    /** The issued client being reauthorized, or empty on a first registration. */
    internal val clientId: String,
    internal val server: ServerSocket,
) {
    fun cancel() = runCatching { server.close() }
}

/**
 * Sign in with ChatGPT, done by hand: the browser signs in and comes back to a one-shot listener on
 * 127.0.0.1, the code is traded for tokens, and the tokens are refreshed as they run out. [read]
 * and [write] keep the account wherever the app keeps secrets.
 */
class ChatGptAuth(
    private val read: () -> String?,
    private val write: (String?) -> Unit,
    private val appName: String = "NextNotes",
    private val userAgent: String = "NextNotes",
) {
    private val lock = Any()

    fun account(): ChatGptAccount? = ChatGptAccount.decode(read())

    private fun hostAccount(): ChatGptAccount =
        account() ?: ChatGptAccount(hostId = "urn:uuid:" + UUID.randomUUID()).also { write(it.encode()) }

    /**
     * Open the listener and build the authorize page. The listener is bound before the browser is
     * opened, on the documented port when it is free, else on any.
     */
    fun begin(): ChatGptLogin {
        val host = hostAccount()
        val server = runCatching { ServerSocket(PORT, 1, LOOPBACK) }.getOrElse { ServerSocket(0, 1, LOOPBACK) }
        val redirect = "http://127.0.0.1:${server.localPort}$CALLBACK_PATH"
        val verifier = random(48)
        val state = random(24)
        val nonce = random(24)
        val reauth = host.clientId.isNotEmpty()
        val params = buildList {
            add("client_id" to (if (reauth) host.clientId else REGISTRATION_CLIENT))
            if (!reauth) add("agent_name_hint" to appName)
            add("ext_agent_host_id" to host.hostId)
            if (host.idToken.isNotEmpty()) add("id_token_hint" to host.idToken)
            add("response_type" to "code")
            add("redirect_uri" to redirect)
            add("scope" to SCOPE)
            add("resource" to RESOURCE)
            add("state" to state)
            add("nonce" to nonce)
            add("code_challenge_method" to "S256")
            add("code_challenge" to challengeOf(verifier))
        }
        return ChatGptLogin(AUTHORIZE + "?" + form(params), redirect, verifier, state, nonce, host.clientId, server)
    }

    /**
     * Wait for the browser to come back, up to [timeoutMs], and sign in with what it brought.
     * [doneLink] is offered on the page the browser is left on, to return to the app. Blocking.
     */
    fun finish(login: ChatGptLogin, timeoutMs: Int, doneLink: String): ChatGptAccount {
        val query = try {
            login.server.soTimeout = timeoutMs
            awaitCallback(login.server, doneLink)
        } catch (e: SocketTimeoutException) {
            throw AiException("等待登录超时，请重试")
        } catch (e: IOException) {
            throw AiCancelled()
        } finally {
            login.cancel()
        }
        if (query["state"] != login.state) throw AiException("登录返回的状态不匹配，请重试")
        query["error"]?.let { throw AiException(if (it == "access_denied") "已取消授权" else "登录失败：$it") }
        val code = query["code"] ?: throw AiException("登录没有返回授权码")
        val issued = query["client_id"]
        val clientId = when {
            login.clientId.isNotEmpty() -> {
                if (issued != null && issued != login.clientId) throw AiException("登录返回的应用不匹配，请重试")
                login.clientId
            }
            issued.isNullOrEmpty() || issued == REGISTRATION_CLIENT -> throw AiException("ChatGPT 没有完成应用注册，请重试")
            else -> issued
        }
        val tokens = post(
            listOf(
                "grant_type" to "authorization_code",
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to login.verifier,
                "redirect_uri" to login.redirectUri,
                "resource" to RESOURCE,
            ),
        )
        val idToken = tokens.optString("id_token")
        val claims = claimsOf(idToken)
        if (claims != null && claims.has("nonce") && claims.optString("nonce") != login.nonce) {
            throw AiException("登录凭据校验失败，请重试")
        }
        val account = hostAccount().copy(
            clientId = clientId,
            email = claims?.optString("email").orEmpty(),
            idToken = idToken,
        ).withTokens(tokens)
        if (USE_SCOPE !in account.scopes) {
            write(account.copy(accessToken = "", refreshToken = "").encode())
            throw AiException("没有获得使用 ChatGPT 套餐的授权。需要 Plus 或 Pro 套餐，并在授权页同意使用额度")
        }
        write(account.encode())
        return account
    }

    /** An access token good for a while yet, refreshed first when it is close to running out. Blocking. */
    fun accessToken(): String = synchronized(lock) {
        val account = account()?.takeIf { it.signedIn } ?: throw AiException("请先在 AI 设置里登录 ChatGPT")
        if (account.accessToken.isNotEmpty() && System.currentTimeMillis() < account.expiresAt - REFRESH_AHEAD_MS) {
            return account.accessToken
        }
        val tokens = try {
            post(
                listOf(
                    "grant_type" to "refresh_token",
                    "client_id" to account.clientId,
                    "refresh_token" to account.refreshToken,
                    "resource" to RESOURCE,
                ),
            )
        } catch (e: AiException) {
            if (e.message.orEmpty().let { m -> TERMINAL.any { it in m } }) {
                write(account.copy(accessToken = "", refreshToken = "").encode())
                throw AiException("ChatGPT 登录已失效，请在 AI 设置里重新登录")
            }
            throw e
        }
        val next = account.withTokens(tokens)
        write(next.encode())
        next.accessToken
    }

    /** Let go of the tokens, here and at OpenAI. The registered client is kept. Blocking. */
    fun signOut() {
        val account = account() ?: return
        write(account.copy(accessToken = "", refreshToken = "", idToken = "", email = "", scopes = emptyList()).encode())
        if (account.refreshToken.isEmpty()) return
        runCatching {
            val discovery = request(DISCOVERY, null, null)
            val endpoint = JSONObject(discovery).optString("revocation_endpoint").ifBlank { return@runCatching }
            request(endpoint, form(listOf("token" to account.refreshToken, "token_type_hint" to "refresh_token", "client_id" to account.clientId)), null)
        }
    }

    /** The models this account may use, in the order OpenAI lists them: (id, display name). Blocking. */
    fun models(): List<Pair<String, String>> {
        val body = request("$RESOURCE/models", null, accessToken())
        val json = JSONObject(body)
        val list = json.optJSONArray("models") ?: json.optJSONArray("data") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val m = list.optJSONObject(i) ?: return@mapNotNull null
            if (m.has("visibility") && m.optString("visibility") != "list") return@mapNotNull null
            val slug = m.optString("slug").ifBlank { m.optString("id") }.ifBlank { return@mapNotNull null }
            slug to m.optString("display_name").ifBlank { slug }
        }
    }

    private fun ChatGptAccount.withTokens(tokens: JSONObject): ChatGptAccount {
        val access = tokens.optString("access_token").ifBlank { throw AiException("登录没有返回访问令牌") }
        val scope = tokens.optString("scope")
        return copy(
            accessToken = access,
            refreshToken = tokens.optString("refresh_token").ifBlank { refreshToken },
            idToken = tokens.optString("id_token").ifBlank { idToken },
            expiresAt = System.currentTimeMillis() + tokens.optLong("expires_in", 3600) * 1000,
            scopes = if (scope.isBlank()) scopes else scope.split(' ', '+').filter { it.isNotBlank() },
        )
    }

    private fun post(fields: List<Pair<String, String>>): JSONObject =
        JSONObject(request(TOKEN, form(fields), null))

    /** GET, or a form POST when [formBody] is given. Throws [AiException] with the server's words on failure. */
    private fun request(url: String, formBody: String?, bearer: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (formBody != null) {
                val bytes = formBody.toByteArray(Charsets.UTF_8)
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiException("HTTP $code：${oauthError(body)}", code)
            return body
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException("网络请求失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val AUTHORIZE = "https://auth.openai.com/api/accounts/authorize"
        const val TOKEN = "https://auth.openai.com/api/accounts/oauth/token"
        const val DISCOVERY = "https://auth.openai.com/.well-known/openid-configuration"
        const val RESOURCE = "https://api.openai.com/v1"
        const val USAGE_PAGE = "https://chatgpt.com/settings/usage"
        const val REGISTRATION_CLIENT = "dynamic_agent_client"
        const val USE_SCOPE = "chatgpt.tokens.use.direct"
        const val SCOPE = "openid profile email offline_access resource.invoke $USE_SCOPE"
        const val CALLBACK_PATH = "/auth/callback"
        const val PORT = 1455
        private const val REFRESH_AHEAD_MS = 5 * 60_000L
        private val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        private val TERMINAL = listOf(
            "invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired",
            "refresh_token_invalidated", "refresh_token_reused",
        )
        private val rng = SecureRandom()

        internal fun random(bytes: Int): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also { rng.nextBytes(it) })

        /** PKCE S256: base64url of the verifier's SHA-256, unpadded. */
        fun challengeOf(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        fun form(fields: List<Pair<String, String>>): String =
            fields.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** The query of a request line like `GET /auth/callback?code=…&state=… HTTP/1.1`. */
        fun queryOf(requestLine: String): Map<String, String>? {
            val target = requestLine.split(' ').getOrNull(1) ?: return null
            val path = target.substringBefore('?')
            if (path != CALLBACK_PATH) return null
            val query = target.substringAfter('?', "")
            return query.split('&').filter { it.isNotEmpty() }.associate { pair ->
                val k = pair.substringBefore('=')
                val v = pair.substringAfter('=', "")
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }
        }

        /** The payload of a JWT, unverified: it came straight from the token endpoint over TLS. */
        fun claimsOf(jwt: String): JSONObject? = runCatching {
            val part = jwt.split('.')[1]
            JSONObject(String(Base64.getUrlDecoder().decode(part.padEnd((part.length + 3) / 4 * 4, '=')), Charsets.UTF_8))
        }.getOrNull()

        /** An OAuth error body's code and description, or the body itself. */
        internal fun oauthError(body: String): String {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return body.take(300)
            val error = json.opt("error")
            return when (error) {
                is String -> listOf(error, json.optString("error_description")).filter { it.isNotBlank() }.joinToString("：")
                is JSONObject -> listOf(error.optString("code"), error.optString("message")).filter { it.isNotBlank() }.joinToString("：")
                else -> json.optString("detail").ifBlank { body.take(300) }
            }
        }

        /** Serve requests on [server] until the callback arrives; answer it with a page that leads back. */
        private fun awaitCallback(server: ServerSocket, doneLink: String): Map<String, String> {
            while (true) {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val line = reader.readLine().orEmpty()
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                    }
                    val query = queryOf(line)
                    val ok = query != null && query["error"] == null
                    val html = if (query == null) "" else page(ok, doneLink)
                    val status = if (query == null) "404 Not Found" else "200 OK"
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                            "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                        write(bytes)
                        flush()
                    }
                    if (query != null) return query
                }
            }
        }

        private fun page(ok: Boolean, doneLink: String): String {
            val title = if (ok) "登录完成" else "登录没有完成"
            val text = if (ok) "可以回到 NextNotes 了。" else "回到 NextNotes 再试一次。"
            return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title><style>body{font-family:sans-serif;background:#F2F3F5;color:#1C1C1E;display:flex;min-height:90vh;align-items:center;justify-content:center}
main{background:#fff;border-radius:28px;padding:40px 48px;text-align:center;box-shadow:0 8px 32px rgba(0,0,0,.08)}h1{font-size:24px;margin:0 0 8px}
p{color:#6B6F76;margin:0 0 24px}a{display:inline-block;background:#1F5FD6;color:#fff;text-decoration:none;padding:12px 28px;border-radius:999px}</style></head>
<body><main><h1>$title</h1><p>$text</p><a href="$doneLink">返回 NextNotes</a></main></body></html>"""
        }
    }
}
