package com.nextnotes.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlanTest {
    private fun e(path: String, v: String, size: Long = 10) = path to FileEntry(path, size, 0, v)
    private fun plan(local: Map<String, FileEntry>, remote: Map<String, FileEntry>, state: Map<String, Synced> = emptyMap()) =
        SyncPlan.plan(local, remote, state) { SyncPlan.copyName(it, "副本") }

    @Test fun aNewFileGoesToTheSideWithoutIt() {
        assertEquals(listOf(Step.Upload("a.xnote"), Step.Download("b.xnote")), plan(mapOf(e("a.xnote", "1")), mapOf(e("b.xnote", "x"))))
    }

    @Test fun anEditGoesOneWay() {
        val state = mapOf("a" to Synced("1", "x"), "b" to Synced("1", "x"))
        assertEquals(
            listOf(Step.Upload("a"), Step.Download("b")),
            plan(mapOf(e("a", "2"), e("b", "1")), mapOf(e("a", "x"), e("b", "y")), state),
        )
    }

    @Test fun nothingChangedMeansNothingToDo() {
        assertEquals(emptyList<Step>(), plan(mapOf(e("a", "1")), mapOf(e("a", "x")), mapOf("a" to Synced("1", "x"))))
    }

    @Test fun aDeletionFollows() {
        val state = mapOf("a" to Synced("1", "x"), "b" to Synced("1", "x"))
        assertEquals(listOf(Step.DeleteRemote("a"), Step.DeleteLocal("b")), plan(mapOf(e("b", "1")), mapOf(e("a", "x")), state))
    }

    @Test fun anEditBeatsADeletion() {
        val state = mapOf("a" to Synced("1", "x"))
        assertEquals(listOf(Step.Upload("a")), plan(mapOf(e("a", "2")), emptyMap(), state))
        assertEquals(listOf(Step.Download("a")), plan(emptyMap(), mapOf(e("a", "y")), state))
    }

    @Test fun editsOnBothSidesKeepBoth() {
        val state = mapOf("dir/a.xnote" to Synced("1", "x"))
        assertEquals(
            listOf(Step.Conflict("dir/a.xnote", "dir/a (副本).xnote")),
            plan(mapOf(e("dir/a.xnote", "2")), mapOf(e("dir/a.xnote", "y")), state),
        )
    }

    @Test fun theSameFileOnBothSidesTheFirstTimeIsTakenAsOne() {
        assertEquals(listOf(Step.Agree("a")), plan(mapOf(e("a", "1", 99)), mapOf(e("a", "x", 99))))
        assertEquals(listOf(Step.Conflict("a", "a (副本)")), plan(mapOf(e("a", "1", 99)), mapOf(e("a", "x", 98))))
    }

    @Test fun goneFromBothIsForgotten() {
        assertEquals(listOf(Step.Forget("a")), plan(emptyMap(), emptyMap(), mapOf("a" to Synced("1", "x"))))
    }
}
