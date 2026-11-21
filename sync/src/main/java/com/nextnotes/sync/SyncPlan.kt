package com.nextnotes.sync

/** What was true of a file the last time both sides agreed on it. */
data class Synced(val local: String, val remote: String)

/** One step of a sync. */
sealed interface Step {
    val path: String

    data class Upload(override val path: String) : Step
    data class Download(override val path: String) : Step
    data class DeleteLocal(override val path: String) : Step
    data class DeleteRemote(override val path: String) : Step

    /** Both sides changed: the local file stays, and the server's goes beside it under [copy]. */
    data class Conflict(override val path: String, val copy: String) : Step

    /** Both sides already hold the same file; only the record needs writing. */
    data class Agree(override val path: String) : Step

    /** Gone from both sides; only the record needs dropping. */
    data class Forget(override val path: String) : Step
}

/**
 * Works out a two-way sync from the files on each side now and the [state] left by the last one.
 * A side changed a file when its version differs from the one recorded. A change on one side goes
 * to the other, deletions included; a change on both keeps both files, so nothing is lost.
 */
object SyncPlan {
    fun plan(
        local: Map<String, FileEntry>,
        remote: Map<String, FileEntry>,
        state: Map<String, Synced>,
        copyName: (String) -> String,
    ): List<Step> {
        val steps = ArrayList<Step>()
        for (path in (local.keys + remote.keys + state.keys).toSortedSet()) {
            val l = local[path]
            val r = remote[path]
            val s = state[path]
            val step: Step? = when {
                l != null && r != null -> when {
                    s == null -> if (l.size == r.size) Step.Agree(path) else Step.Conflict(path, copyName(path))
                    else -> {
                        val lChanged = l.version != s.local
                        val rChanged = r.version != s.remote
                        when {
                            lChanged && rChanged -> Step.Conflict(path, copyName(path))
                            lChanged -> Step.Upload(path)
                            rChanged -> Step.Download(path)
                            else -> null
                        }
                    }
                }
                l != null -> when {
                    s == null -> Step.Upload(path)
                    // Deleted on the server: unless edited here since, it goes here too.
                    l.version != s.local -> Step.Upload(path)
                    else -> Step.DeleteLocal(path)
                }
                r != null -> when {
                    s == null -> Step.Download(path)
                    r.version != s.remote -> Step.Download(path)
                    else -> Step.DeleteRemote(path)
                }
                else -> Step.Forget(path)
            }
            step?.let(steps::add)
        }
        return steps
    }

    /** "folder/name (from another device).ext" beside [path], tagged with [tag]. */
    fun copyName(path: String, tag: String): String {
        val slash = path.lastIndexOf('/')
        val dir = path.substring(0, slash + 1)
        val name = path.substring(slash + 1)
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        return dir + name.substring(0, dot) + " ($tag)" + name.substring(dot)
    }
}
