package com.nextnotes.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nextnotes.ai.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Where to sync: a WebDAV address, the account, and the folder there that holds the notes. */
data class SyncConfig(
    val server: String,
    val user: String,
    val folder: String = "NextNotes",
    /** Sync on its own: when the app opens and each time a note is closed. */
    val auto: Boolean = true,
) {
    val folderUrl: String get() = server.trimEnd('/') + "/" + folder.trim('/')
}

/**
 * Runs syncs of the notes folder with the WebDAV server, one at a time, and keeps what Compose
 * shows about them. The password is kept encrypted with the AI keys; the rest in preferences.
 */
object SyncCenter {
    private const val PREFS = "nextnotes_sync"
    private const val VAULT_ID = "webdav"

    /** Auto syncs are at least this far apart; a sync asked for by hand always runs. */
    private const val AUTO_GAP_MS = 2 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val busy = AtomicBoolean(false)
    private var lastAuto = 0L

    var running by mutableStateOf(false)
        private set

    /** What the last sync came to, for the settings row; null before any. */
    var status by mutableStateOf<String?>(null)
        private set

    var lastSync by mutableStateOf(0L)
        private set

    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        status = p.getString("status", null)
        lastSync = p.getLong("last", 0L)
    }

    fun config(context: Context): SyncConfig? {
        val p = prefs(context)
        val server = p.getString("server", null)?.takeIf { it.isNotBlank() } ?: return null
        return SyncConfig(server, p.getString("user", "").orEmpty(), p.getString("folder", "NextNotes").orEmpty(), p.getBoolean("auto", true))
    }

    fun hasPassword(context: Context): Boolean = KeyVault(context.applicationContext).has(VAULT_ID)

    /** Saves [config]; a blank [password] keeps the one already saved. */
    fun save(context: Context, config: SyncConfig, password: String) {
        prefs(context).edit()
            .putString("server", config.server.trim()).putString("user", config.user.trim())
            .putString("folder", config.folder.trim().trim('/').ifEmpty { "NextNotes" }).putBoolean("auto", config.auto)
            .apply()
        if (password.isNotEmpty()) KeyVault(context.applicationContext).put(VAULT_ID, password)
    }

    fun forget(context: Context) {
        prefs(context).edit().clear().apply()
        KeyVault(context.applicationContext).remove(VAULT_ID)
        status = null
        lastSync = 0L
    }

    /** Tries the address and the account; a sentence saying how it went. */
    suspend fun test(context: Context, config: SyncConfig, password: String): String = withContext(Dispatchers.IO) {
        val pw = password.ifEmpty { runCatching { KeyVault(context.applicationContext).get(VAULT_ID) }.getOrDefault("") }
        try {
            DavTree(config.folderUrl, config.user, pw).check()
            "连接成功"
        } catch (e: Exception) {
            "连接失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Syncs on its own if it is set to and has not just done so. */
    fun auto(context: Context, root: String?, onChanged: () -> Unit) {
        val config = config(context) ?: return
        if (!config.auto || root == null) return
        val now = System.currentTimeMillis()
        if (now - lastAuto < AUTO_GAP_MS) return
        lastAuto = now
        sync(context, root, onChanged)
    }

    /** Syncs [root] (the notes folder's tree) with the server; [onChanged] runs when files here changed. */
    fun sync(context: Context, root: String?, onChanged: () -> Unit) {
        val app = context.applicationContext
        val config = config(app) ?: return
        if (root == null) {
            status = "没有可同步的笔记文件夹"
            return
        }
        if (!busy.compareAndSet(false, true)) return
        running = true
        scope.launch {
            val report = withContext(Dispatchers.IO) {
                runCatching {
                    val password = KeyVault(app).get(VAULT_ID)
                    val stateFile = File(app.filesDir, "sync/state-${sha1(root + "|" + config.folderUrl)}.json")
                    val state = Syncer.decodeState(stateFile.takeIf { it.exists() }?.readText())
                    val tag = "其他设备 " + SimpleDateFormat("MM-dd HH.mm", Locale.US).format(Date())
                    try {
                        Syncer(SafTree(app, root), DavTree(config.folderUrl, config.user, password), state, tag, ::ignored).run()
                    } finally {
                        // Whatever got settled is kept, even when the sync stopped partway.
                        stateFile.parentFile?.mkdirs()
                        stateFile.writeText(Syncer.encodeState(state))
                    }
                }
            }
            running = false
            busy.set(false)
            report.onSuccess { r ->
                lastSync = System.currentTimeMillis()
                status = describe(r)
                if (r.changedHere) onChanged()
            }.onFailure { e ->
                status = "同步失败：${e.message ?: e.javaClass.simpleName}"
            }
            prefs(app).edit().putString("status", status).putLong("last", lastSync).apply()
        }
    }

    /** Hidden files (each folder's ".xnote" colours, pins and trash, which every device keeps for itself) and temporary ones. */
    fun ignored(path: String): Boolean =
        path.split('/').any { it.startsWith(".") } || path.endsWith(".tmp") || path.endsWith("~")

    fun describe(r: SyncReport): String {
        val parts = buildList {
            if (r.uploaded > 0) add("上传 ${r.uploaded} 个")
            if (r.downloaded > 0) add("下载 ${r.downloaded} 个")
            if (r.deletedHere + r.deletedThere > 0) add("删除 ${r.deletedHere + r.deletedThere} 个")
            if (r.conflicts.isNotEmpty()) add("${r.conflicts.size} 个两边都改过，已保留两份")
            if (r.errors.isNotEmpty()) add("${r.errors.size} 个失败（${r.errors.first()}）")
        }
        return if (parts.isEmpty()) "已是最新" else parts.joinToString("，")
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
