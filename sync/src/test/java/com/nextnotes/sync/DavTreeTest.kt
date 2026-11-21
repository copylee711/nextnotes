package com.nextnotes.sync

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DavTreeTest {
    private val server = MockWebServer()

    @After fun tearDown() = server.shutdown()

    private fun multistatus(vararg rows: String) = MockResponse().setResponseCode(207).setBody(
        """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">${rows.joinToString("")}</d:multistatus>""",
    )

    private fun dir(href: String) =
        """<d:response><d:href>$href</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>"""

    private fun file(href: String, size: Int, etag: String) =
        """<d:response><d:href>$href</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>$size</d:getcontentlength>""" +
            """<d:getlastmodified>Sat, 21 Nov 2026 14:00:00 GMT</d:getlastmodified><d:getetag>"$etag"</d:getetag></d:prop></d:propstat></d:response>"""

    @Test fun aListingWalksTheFoldersAndDecodesNames() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/dav/NextNotes/" -> multistatus(
                    dir("/dav/NextNotes/"),
                    dir("/dav/NextNotes/%E6%95%B0%E5%AD%A6/"),
                    file("/dav/NextNotes/a%20b.xnote", 12, "e1"),
                )
                "/dav/NextNotes/%E6%95%B0%E5%AD%A6/" -> multistatus(
                    dir("/dav/NextNotes/%E6%95%B0%E5%AD%A6/"),
                    file("http://host/dav/NextNotes/%E6%95%B0%E5%AD%A6/%E7%AC%AC%E4%B8%80%E7%AB%A0.xnote", 34, "e2"),
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        val tree = DavTree(server.url("/dav/NextNotes").toString(), "u", "p")
        val files = tree.list()
        assertEquals(setOf("a b.xnote", "数学/第一章.xnote"), files.keys)
        assertEquals(34L, files.getValue("数学/第一章.xnote").size)
        assertEquals("e2", files.getValue("数学/第一章.xnote").version)
        val first = server.takeRequest()
        assertEquals("PROPFIND", first.method)
        assertEquals("1", first.getHeader("Depth"))
        assertTrue(first.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test fun aWriteMakesTheFoldersFirst() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
                "MKCOL" -> MockResponse().setResponseCode(if (request.path == "/NextNotes/") 405 else 201)
                "PUT" -> MockResponse().setResponseCode(201)
                "PROPFIND" -> multistatus(file(request.path!!, 3, "new"))
                else -> MockResponse().setResponseCode(500)
            }
        }
        val tree = DavTree(server.url("/NextNotes/").toString(), "u", "p")
        val e = tree.write("数学/a.xnote", "abc".byteInputStream(), 3)
        assertEquals("new", e.version)
        val calls = (1..server.requestCount).map { server.takeRequest().let { r -> "${r.method} ${r.path}" } }
        assertEquals(
            listOf("MKCOL /NextNotes/", "MKCOL /NextNotes/%E6%95%B0%E5%AD%A6/", "PUT /NextNotes/%E6%95%B0%E5%AD%A6/a.xnote", "PROPFIND /NextNotes/%E6%95%B0%E5%AD%A6/a.xnote"),
            calls,
        )
    }

    @Test fun aMissingFolderListsEmptyAndIsMade() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.method == "MKCOL") MockResponse().setResponseCode(201) else MockResponse().setResponseCode(404)
        }
        assertEquals(emptyMap<String, FileEntry>(), DavTree(server.url("/x/").toString(), "u", "p").list())
    }
}
