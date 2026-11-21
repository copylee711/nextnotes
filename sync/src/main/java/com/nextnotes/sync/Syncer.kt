package com.nextnotes.sync

import org.json.JSONObject

/** How a sync went. */
data class SyncReport(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val deletedHere: Int = 0,
    val deletedThere: Int = 0,
    val conflicts: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val changedHere: Boolean get() = downloaded > 0 || deletedHere > 0 || conflicts.isNotEmpty()
}

/** A sync stopped before touching anything, because what it would do looks like a mistake. */
class SyncRefused(message: String) : Exception(message)

/**
 * Runs a [SyncPlan] between [local] and [remote], keeping [state] in step with each file it
 * settles, so a sync cut off halfway picks up where it stopped. One file failing does not stop
 * the rest; it is reported and tried again next time.
 */
class Syncer(
    private val local: FileTree,
    private val remote: FileTree,
    private val state: MutableMap<String, Synced>,
    /** What a conflicting file's copy is tagged with, such as "其他设备 2026-11-21 22.30". */
    private val conflictTag: String,
    /** Paths left out of syncing altogether, such as half-written temporary files. */
    private val ignore: (String) -> Boolean = { false },
) {
    fun run(): SyncReport {
        val here = local.list().filterKeys { !ignore(it) }
        val there = remote.list().filterKeys { !ignore(it) }
        val steps = SyncPlan.plan(here, there, state) { SyncPlan.copyName(it, conflictTag) }
        guard(steps.count { it is Step.DeleteLocal }, here.size, "这台设备")
        guard(steps.count { it is Step.DeleteRemote }, there.size, "服务器")

        var report = SyncReport()
        val conflicts = ArrayList<String>()
        val errors = ArrayList<String>()
        for (step in steps) {
            val path = step.path
            try {
                when (step) {
                    is Step.Upload -> {
                        state[path] = Synced(here.getValue(path).version, push(path, here.getValue(path).size).version)
                        report = report.copy(uploaded = report.uploaded + 1)
                    }
                    is Step.Download -> {
                        state[path] = Synced(pull(path, path, there.getValue(path).size).version, there.getValue(path).version)
                        report = report.copy(downloaded = report.downloaded + 1)
                    }
                    is Step.Conflict -> {
                        // The server's file comes down beside ours and goes back up under that name;
                        // then ours takes the original name on both sides.
                        val copy = pull(path, step.copy, there.getValue(path).size)
                        state[step.copy] = Synced(copy.version, push(step.copy, copy.size).version)
                        state[path] = Synced(here.getValue(path).version, push(path, here.getValue(path).size).version)
                        conflicts += path
                    }
                    is Step.DeleteLocal -> {
                        local.delete(path)
                        state.remove(path)
                        report = report.copy(deletedHere = report.deletedHere + 1)
                    }
                    is Step.DeleteRemote -> {
                        remote.delete(path)
                        state.remove(path)
                        report = report.copy(deletedThere = report.deletedThere + 1)
                    }
                    is Step.Agree -> state[path] = Synced(here.getValue(path).version, there.getValue(path).version)
                    is Step.Forget -> state.remove(path)
                }
            } catch (e: Exception) {
                errors += "$path：${e.message ?: e.javaClass.simpleName}"
            }
        }
        return report.copy(conflicts = conflicts, errors = errors)
    }

    private fun push(path: String, size: Long): FileEntry = local.read(path).use { remote.write(path, it, size) }

    private fun pull(from: String, to: String, size: Long): FileEntry = remote.read(from).use { local.write(to, it, size) }

    /** Refuses a sync that would delete most of a side's files: far likelier a fault than a wish. */
    private fun guard(deletions: Int, total: Int, side: String) {
        if (deletions >= 3 && deletions * 2 > total) {
            throw SyncRefused("这次同步会删除${side}上 $total 个文件中的 $deletions 个，看起来不对，已停止。请检查服务器文件夹是否填对。")
        }
    }

    companion object {
        fun encodeState(state: Map<String, Synced>): String = JSONObject().apply {
            put("version", 1)
            put("files", JSONObject().apply {
                for ((path, s) in state) put(path, JSONObject().put("l", s.local).put("r", s.remote))
            })
        }.toString()

        fun decodeState(raw: String?): MutableMap<String, Synced> {
            val out = HashMap<String, Synced>()
            val files = runCatching { JSONObject(raw ?: return out).optJSONObject("files") }.getOrNull() ?: return out
            for (path in files.keys()) {
                val o = files.optJSONObject(path) ?: continue
                out[path] = Synced(o.optString("l"), o.optString("r"))
            }
            return out
        }
    }
}
