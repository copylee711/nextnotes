package com.nextnotes.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var padDir: File
    private lateinit var pcDir: File
    private lateinit var server: File
    private val padState = HashMap<String, Synced>()
    private val pcState = HashMap<String, Synced>()

    private fun setUp() {
        padDir = tmp.newFolder("pad"); pcDir = tmp.newFolder("pc"); server = tmp.newFolder("server")
    }

    private fun syncPad() = Syncer(DirTree(padDir), DirTree(server), padState, "平板").run()
    private fun syncPc() = Syncer(DirTree(pcDir), DirTree(server), pcState, "电脑").run()

    private fun write(dir: File, path: String, text: String) {
        File(dir, path).apply { parentFile?.mkdirs(); writeText(text); setLastModified(System.currentTimeMillis() + text.length * 1000L) }
    }

    @Test fun twoDevicesEndUpWithTheSameNotes() {
        setUp()
        write(padDir, "数学/第一章.xnote", "pad notes")
        write(pcDir, "物理.xnote", "pc")
        assertEquals(1, syncPad().uploaded)
        val pc = syncPc()
        assertEquals(1, pc.uploaded); assertEquals(1, pc.downloaded)
        assertEquals(1, syncPad().downloaded)
        assertEquals("pad notes", File(pcDir, "数学/第一章.xnote").readText())
        assertEquals("pc", File(padDir, "物理.xnote").readText())
    }

    @Test fun aDeletionOnOneDeviceReachesTheOther() {
        setUp()
        for (i in 1..4) write(padDir, "n$i.xnote", "note $i")
        syncPad(); syncPc()
        File(padDir, "n1.xnote").delete()
        assertEquals(1, syncPad().deletedThere)
        assertEquals(1, syncPc().deletedHere)
        assertFalse(File(pcDir, "n1.xnote").exists())
    }

    @Test fun editsOnBothDevicesKeepBothVersions() {
        setUp()
        write(padDir, "a.xnote", "v1")
        syncPad(); syncPc()
        write(padDir, "a.xnote", "pad edit")
        write(pcDir, "a.xnote", "pc edit, longer")
        syncPad()
        val pc = syncPc()
        assertEquals(listOf("a.xnote"), pc.conflicts)
        assertEquals("pc edit, longer", File(pcDir, "a.xnote").readText())
        assertEquals("pad edit", File(pcDir, "a (电脑).xnote").readText())
        syncPad()
        assertEquals("pc edit, longer", File(padDir, "a.xnote").readText())
        assertTrue(File(padDir, "a (电脑).xnote").exists())
    }

    @Test(expected = SyncRefused::class) fun aSyncThatWouldEmptyADeviceIsRefused() {
        setUp()
        for (i in 1..6) write(padDir, "n$i.xnote", "note $i")
        syncPad()
        server.listFiles()!!.forEach { it.delete() } // a wrong folder, say, or a server wiped
        syncPad()
    }

    @Test fun theRecordSurvivesSaving() {
        val state = mapOf("数学/a.xnote" to Synced("1:2", "\"etag\""))
        assertEquals(state, Syncer.decodeState(Syncer.encodeState(state)))
    }
}
