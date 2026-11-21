package com.nextnotes.sync

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.w3c.dom.Element
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** A WebDAV request the server turned down, with what it said. */
class DavException(val code: Int, message: String) : IOException(message)

/**
 * The folder at [folderUrl] on a WebDAV server, as one side of a sync. Only plain WebDAV is used
 * (PROPFIND one level at a time, GET, PUT, DELETE, MKCOL), which every server speaks.
 */
class DavTree(
    folderUrl: String,
    user: String,
    password: String,
    private val client: OkHttpClient = defaultClient,
) : FileTree {
    private val base: HttpUrl = folderUrl.trimEnd('/').plus("/").toHttpUrl()
    private val auth = Credentials.basic(user, password, Charsets.UTF_8)
    private val basePath = decode(base.encodedPath)

    /** Folders known to exist, so a write makes each at most once. */
    private val made = HashSet<String>()

    private fun url(path: String): HttpUrl {
        val b = base.newBuilder()
        for (seg in path.split('/')) if (seg.isNotEmpty()) b.addPathSegment(seg)
        return b.build()
    }

    private fun dirUrl(path: String): HttpUrl = url(path).newBuilder().addPathSegment("").build()

    private fun call(method: String, url: HttpUrl, body: RequestBody? = null, depth: String? = null): Response {
        val req = Request.Builder().url(url).header("Authorization", auth).method(method, body)
        depth?.let { req.header("Depth", it) }
        return client.newCall(req.build()).execute()
    }

    private fun fail(r: Response, what: String): Nothing {
        val code = r.code
        r.close()
        throw DavException(code, when (code) {
            401 -> "用户名或密码不对"
            403 -> "服务器拒绝访问这个文件夹"
            404 -> "找不到$what"
            507 -> "服务器空间不足"
            else -> "$what 失败（HTTP $code）"
        })
    }

    /** Checks the address and the account, making the folder if it is not there yet. */
    fun check() {
        propfind("", "0") ?: ensureDir("")
    }

    override fun list(): Map<String, FileEntry> {
        val out = HashMap<String, FileEntry>()
        val folders = ArrayDeque<String>()
        if (propfind("", "1") == null) {
            ensureDir("")
            return out
        }
        folders += ""
        while (folders.isNotEmpty()) {
            val dir = folders.removeFirst()
            made += dir
            for (e in propfind(dir, "1").orEmpty()) {
                if (e.path == dir) continue
                if (e.folder) folders += e.path else out[e.path] = e.entry
            }
        }
        return out
    }

    override fun read(path: String): InputStream {
        val r = call("GET", url(path))
        if (!r.isSuccessful) fail(r, path)
        return r.body!!.byteStream()
    }

    override fun write(path: String, data: InputStream, size: Long): FileEntry {
        ensureDir(path.substringBeforeLast('/', ""))
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                sink.writeAll(data.source())
            }
        }
        val r = call("PUT", url(path), body)
        if (!r.isSuccessful) fail(r, "上传 $path")
        val etag = r.header("ETag")
        r.close()
        // Its version is what a listing will say next time, so read it back the same way.
        return propfind(path, "0")?.firstOrNull()?.entry
            ?: FileEntry(path, size, System.currentTimeMillis(), etag.orEmpty())
    }

    override fun delete(path: String) {
        val r = call("DELETE", url(path))
        if (!r.isSuccessful && r.code != 404) fail(r, "删除 $path")
        r.close()
    }

    private fun ensureDir(dir: String) {
        if (dir in made) return
        if (dir.isNotEmpty()) ensureDir(dir.substringBeforeLast('/', ""))
        val r = call("MKCOL", dirUrl(dir))
        // 405: it is already there.
        if (!r.isSuccessful && r.code != 405) fail(r, "创建文件夹 ${dir.ifEmpty { "/" }}")
        r.close()
        made += dir
    }

    private class Found(val path: String, val folder: Boolean, val entry: FileEntry)

    /** The entries at [path] ([depth] "0") or in it ("1"); null when it does not exist. */
    private fun propfind(path: String, depth: String): List<Found>? {
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val target = if (depth == "1" || path.isEmpty()) dirUrl(path) else url(path)
        val r = call("PROPFIND", target, body, depth)
        if (r.code == 404) {
            r.close()
            return null
        }
        if (r.code != 207 && !r.isSuccessful) fail(r, path.ifEmpty { "服务器文件夹" })
        val xml = r.body!!.byteStream().use { parse(it) }
        r.close()
        return xml
    }

    private fun parse(stream: InputStream): List<Found> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(stream)
        val responses = doc.getElementsByTagNameNS(DAV, "response")
        val out = ArrayList<Found>()
        for (i in 0 until responses.length) {
            val resp = responses.item(i) as Element
            val href = text(resp, "href") ?: continue
            val full = decode(if (href.startsWith("http")) href.toHttpUrl().encodedPath else href)
            if (!full.startsWith(basePath) && full.trimEnd('/') + "/" != basePath) continue
            val rel = full.removePrefix(basePath).trim('/')
            val folder = resp.getElementsByTagNameNS(DAV, "collection").length > 0
            val size = text(resp, "getcontentlength")?.toLongOrNull() ?: 0L
            val modified = text(resp, "getlastmodified")?.let { parseDate(it) } ?: 0L
            val etag = text(resp, "getetag")?.trim('"', ' ', '\n').orEmpty()
            out += Found(rel, folder, FileEntry(rel, size, modified, etag.ifEmpty { "$size:$modified" }))
        }
        return out
    }

    private fun text(e: Element, name: String): String? =
        e.getElementsByTagNameNS(DAV, name).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private const val DAV = "DAV:"
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getetag/></d:prop></d:propfind>"""

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

        private fun parseDate(s: String): Long? = runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(s)?.time
        }.getOrNull()
    }
}
