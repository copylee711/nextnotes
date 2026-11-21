package com.nextnotes.update

import org.json.JSONObject

/** A published NextNotes release, as GitHub describes it. */
data class Release(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    /** The APK to download, when the release has one attached. */
    val apkUrl: String?,
)

object Releases {
    const val REPO = "copylee711/nextnotes"
    const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val PAGE = "https://github.com/$REPO/releases"

    /** Reads GitHub's "latest release" answer; drafts and pre-releases are never offered. */
    fun parse(json: String): Release? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.getString("tag_name")
        val assets = o.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url").ifEmpty { null }; break }
        }
        Release(
            version = Versions.clean(tag),
            title = o.optString("name").ifBlank { tag },
            notes = o.optString("body"),
            pageUrl = o.optString("html_url").ifBlank { PAGE },
            apkUrl = apk,
        )
    }.getOrNull()
}

object Versions {
    /** "v1.2.0" and "1.2.0-debug" are both 1.2.0. */
    fun clean(v: String): String = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')

    private fun parts(v: String) = clean(v).split('.').map { it.toIntOrNull() ?: 0 }

    /** Negative when [a] is older than [b], zero when the same, positive when newer. */
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(candidate: String, current: String) = compare(candidate, current) > 0
}
