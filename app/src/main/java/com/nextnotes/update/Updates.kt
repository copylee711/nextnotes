package com.nextnotes.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer NextNotes. Automatically at most every few hours on launch
 * (unless turned off), or when asked from About. A version the user chose to skip is not offered
 * again by the automatic check; asking by hand still shows it.
 */
object Updates {
    private const val PREFS = "nextnotes_updates"
    private const val KEY_AUTO = "auto_check"
    private const val KEY_SKIPPED = "skipped_version"
    private const val KEY_LAST = "last_check"
    private const val AUTO_EVERY_MS = 6 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** A newer release waiting for the user to act on it. */
    var offer by mutableStateOf<Release?>(null)
    var checking by mutableStateOf(false)
        private set

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCheck(ctx: Context) = prefs(ctx).getBoolean(KEY_AUTO, true)
    fun setAutoCheck(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_AUTO, on).apply()

    fun skip(ctx: Context, release: Release) {
        prefs(ctx).edit().putString(KEY_SKIPPED, release.version).apply()
        offer = null
    }

    fun currentVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** The launch check: quiet, throttled, and silent when it fails. */
    fun checkOnLaunch(ctx: Context) {
        val p = prefs(ctx)
        if (!autoCheck(ctx)) return
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_LAST, 0L) < AUTO_EVERY_MS) return
        check(ctx, manual = false)
    }

    /** Ask GitHub now; [manual] says the user asked, so they hear back either way. */
    fun check(ctx: Context, manual: Boolean) {
        if (checking) return
        val app = ctx.applicationContext
        checking = true
        scope.launch {
            val current = currentVersion(app)
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest(current) } }
            checking = false
            result.onSuccess { release ->
                prefs(app).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
                val newer = release != null && Versions.isNewer(release.version, current)
                val skipped = prefs(app).getString(KEY_SKIPPED, null)
                when {
                    newer && (manual || release!!.version != skipped) -> offer = release
                    manual -> toast(app, "已是最新版本（${Versions.clean(current)}）")
                }
            }.onFailure { e ->
                if (manual) toast(app, "检查更新失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun fetchLatest(current: String): Release? {
        val conn = URL(Releases.LATEST_API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "NextNotes/$current")
            return when (val code = conn.responseCode) {
                200 -> Releases.parse(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> null // no release published yet
                403, 429 -> error("GitHub 暂时限制了访问，请稍后再试")
                else -> error("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun download(ctx: Context, release: Release) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl ?: release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        offer = null
    }

    private fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
}
