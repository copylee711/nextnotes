package com.nextnotes.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The notes folder as one side of a sync, reached through the storage framework, so the app's
 * own storage and a folder the user granted are handled alike.
 */
class SafTree(private val context: Context, treeUri: String) : FileTree {
    private val tree = Uri.parse(treeUri)
    private val resolver = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    /** Document ids by path, folders included ("" is the top folder). */
    private val ids = HashMap<String, String>().apply { put("", rootId) }

    private fun doc(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private class Row(val id: String, val name: String, val dir: Boolean, val size: Long, val modified: Long)

    private fun children(id: String): List<Row> {
        val out = ArrayList<Row>()
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += Row(
                    c.getString(0), c.getString(1) ?: continue, c.getString(2) == Document.MIME_TYPE_DIR,
                    if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
        }
        return out
    }

    private fun entry(path: String, size: Long, modified: Long) = FileEntry(path, size, modified, "$size:$modified")

    override fun list(): Map<String, FileEntry> {
        val out = HashMap<String, FileEntry>()
        val queue = ArrayDeque<Pair<String, String>>().apply { add("" to rootId) }
        while (queue.isNotEmpty()) {
            val (dir, id) = queue.removeFirst()
            for (row in children(id)) {
                val path = if (dir.isEmpty()) row.name else "$dir/${row.name}"
                ids[path] = row.id
                if (row.dir) queue += path to row.id else out[path] = entry(path, row.size, row.modified)
            }
        }
        return out
    }

    override fun read(path: String): InputStream {
        val id = ids[path] ?: throw FileNotFoundException(path)
        return resolver.openInputStream(doc(id)) ?: throw FileNotFoundException(path)
    }

    override fun write(path: String, data: InputStream, size: Long): FileEntry {
        // The whole file comes off the network first, so a dropped connection never leaves a note half written.
        val temp = File.createTempFile("sync", ".part", context.cacheDir)
        try {
            temp.outputStream().use { data.copyTo(it) }
            val name = path.substringAfterLast('/')
            val parent = ensureDir(path.substringBeforeLast('/', ""))
            val id = ids[path] ?: children(parent).firstOrNull { !it.dir && it.name == name }?.id
                ?: DocumentsContract.createDocument(resolver, doc(parent), "application/octet-stream", name)
                    ?.let { DocumentsContract.getDocumentId(it) }
                ?: throw FileNotFoundException("无法创建 $path")
            ids[path] = id
            (resolver.openOutputStream(doc(id), "wt") ?: throw FileNotFoundException(path)).use { out ->
                temp.inputStream().use { it.copyTo(out) }
            }
            val row = children(parent).firstOrNull { it.id == id }
            return entry(path, row?.size ?: temp.length(), row?.modified ?: System.currentTimeMillis())
        } finally {
            temp.delete()
        }
    }

    override fun delete(path: String) {
        val id = ids.remove(path) ?: return
        DocumentsContract.deleteDocument(resolver, doc(id))
    }

    private fun ensureDir(dir: String): String {
        ids[dir]?.let { return it }
        val parent = ensureDir(dir.substringBeforeLast('/', ""))
        val name = dir.substringAfterLast('/')
        val id = children(parent).firstOrNull { it.dir && it.name == name }?.id
            ?: DocumentsContract.createDocument(resolver, doc(parent), Document.MIME_TYPE_DIR, name)?.let { DocumentsContract.getDocumentId(it) }
            ?: throw FileNotFoundException("无法创建文件夹 $dir")
        ids[dir] = id
        return id
    }
}
