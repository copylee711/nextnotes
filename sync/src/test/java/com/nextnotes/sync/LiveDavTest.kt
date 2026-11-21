package com.nextnotes.sync

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Against a real server, only when NEXTNOTES_DAV_URL, NEXTNOTES_DAV_USER and NEXTNOTES_DAV_PASS
 * are set. It works in a fresh folder of its own and removes it after.
 */
class LiveDavTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun twoFoldersSyncThroughTheServer() {
        val url = System.getenv("NEXTNOTES_DAV_URL")
        val user = System.getenv("NEXTNOTES_DAV_USER")
        val pass = System.getenv("NEXTNOTES_DAV_PASS")
        assumeTrue(url != null && user != null && pass != null)
        val folder = url!!.trimEnd('/') + "/nextnotes-test-${System.currentTimeMillis()}"
        val remote = DavTree(folder, user!!, pass!!)
        val a = tmp.newFolder("a")
        val b = tmp.newFolder("b")
        File(a, "数学/第一章 草稿.xnote").apply { parentFile!!.mkdirs(); writeText("from a") }
        File(b, "物理.xnote").writeText("from b")
        val sa = HashMap<String, Synced>()
        val sb = HashMap<String, Synced>()
        try {
            remote.check()
            assertEquals(1, Syncer(DirTree(a), remote, sa, "a").run().also { println(it) }.uploaded)
            Syncer(DirTree(b), remote, sb, "b").run().also { println(it); assertEquals(1, it.downloaded); assertEquals(1, it.uploaded) }
            assertEquals("from a", File(b, "数学/第一章 草稿.xnote").readText())
            // A second pass with nothing changed does nothing: the versions read back match.
            Syncer(DirTree(b), remote, sb, "b").run().also { assertEquals(SyncReport(), it) }
            Syncer(DirTree(a), remote, sa, "a").run().also { println(it); assertEquals(1, it.downloaded) }
            File(b, "物理.xnote").delete()
            Syncer(DirTree(b), remote, sb, "b").run().also { println(it); assertEquals(1, it.deletedThere) }
            Syncer(DirTree(a), remote, sa, "a").run().also { println(it); assertEquals(1, it.deletedHere) }
            assertEquals(false, File(a, "物理.xnote").exists())
        } finally {
            for (p in remote.list().keys) remote.delete(p)
            remote.delete("数学/")
            remote.delete("")
        }
    }
}
