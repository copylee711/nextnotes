package com.nextnotes.ai

/**
 * Something the AI made to be looked at or played with rather than read: a self-contained web page
 * or a picture, sent as an ```html or ```svg block in its answer.
 */
data class Artifact(val kind: Kind, val source: String, val title: String) {
    enum class Kind { HTML, SVG }
}

object Artifacts {
    /**
     * The page runs offline: it may not load or send anything, so the note it was asked about never
     * leaves the device through it. The WebView also blocks network loads; this is the second lock.
     */
    const val CSP = "default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; " +
        "style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:"

    /** The artifact a fenced code block holds, if its language says it is one. */
    fun of(info: String?, source: String): Artifact? {
        val kind = when (info?.trim()?.substringBefore(' ')?.lowercase()) {
            "html", "htm" -> Artifact.Kind.HTML
            "svg" -> Artifact.Kind.SVG
            else -> return null
        }
        if (source.isBlank()) return null
        return Artifact(kind, source, titleOf(kind, source))
    }

    private val titleTag = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val heading = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tag = Regex("<[^>]+>")

    fun titleOf(kind: Artifact.Kind, source: String): String {
        val found = (titleTag.find(source) ?: heading.find(source))?.groupValues?.get(1)
            ?.replace(tag, "")?.let(::unescape)?.replace(Regex("\\s+"), " ")?.trim()
        return found?.takeIf { it.isNotEmpty() }?.take(40)
            ?: if (kind == Artifact.Kind.SVG) "图示" else "交互演示"
    }

    private fun unescape(s: String) =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    private val doctype = Regex("^\\s*<!doctype[^>]*>", RegexOption.IGNORE_CASE)
    private val wholePage = Regex("<(html|body)[\\s>]", RegexOption.IGNORE_CASE)

    /**
     * The page the sandbox loads. The policy goes first, ahead of anything the AI wrote, so no
     * script of its can run before the policy holds.
     */
    fun document(artifact: Artifact): String {
        val head = "<!doctype html><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"$CSP\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        val body = artifact.source.replaceFirst(doctype, "")
        return when {
            artifact.kind == Artifact.Kind.SVG -> head +
                "<style>html,body{margin:0;background:#fff}body{display:flex;justify-content:center;padding:8px}" +
                "svg{max-width:100%;height:auto}</style>" + body
            wholePage.containsMatchIn(body) -> head + body
            else -> head +
                "<style>body{margin:12px;font-family:system-ui,sans-serif;color:#222;background:#fff}</style>" + body
        }
    }
}
