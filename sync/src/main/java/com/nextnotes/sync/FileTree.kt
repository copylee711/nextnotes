package com.nextnotes.sync

import java.io.File
import java.io.InputStream

/**
 * A file as one side of a sync sees it. [path] is relative to the synced folder, its parts joined
 * by "/". [version] changes whenever the file does: an ETag on the server, size and time locally.
 */
data class FileEntry(val path: String, val size: Long, val modified: Long, val version: String)

/** One side of a sync: the notes folder on this device, or the folder on the server. */
interface FileTree {
    /** Every file under the folder, folders themselves left out, by path. */
    fun list(): Map<String, FileEntry>

    fun read(path: String): InputStream

    /** Writes [data] to [path], making the folders on the way, and says what the file is now. */
    fun write(path: String, data: InputStream, size: Long): FileEntry

    fun delete(path: String)
}

/** A plain folder on disk, for tests and for anything that has a real path. */
class DirTree(private val root: File) : FileTree {
    override fun list(): Map<String, FileEntry> {
        val out = HashMap<String, FileEntry>()
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            val path = f.relativeTo(root).invariantSeparatorsPath
            out[path] = entry(path, f)
        }
        return out
    }

    private fun entry(path: String, f: File) = FileEntry(path, f.length(), f.lastModified(), "${f.length()}:${f.lastModified()}")

    override fun read(path: String): InputStream = File(root, path).inputStream()

    override fun write(path: String, data: InputStream, size: Long): FileEntry {
        val f = File(root, path)
        f.parentFile?.mkdirs()
        f.outputStream().use { data.copyTo(it) }
        return entry(path, f)
    }

    override fun delete(path: String) {
        File(root, path).delete()
    }
}
