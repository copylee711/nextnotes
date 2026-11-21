package com.nextnotes.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    @Test fun comparesVersionsNumerically() {
        assertTrue(Versions.isNewer("v0.2.0", "0.1.0"))
        assertTrue(Versions.isNewer("0.10.0", "0.9.9"))
        assertTrue(Versions.isNewer("1.0", "0.99.99"))
        assertFalse(Versions.isNewer("v0.1.0", "0.1.0-debug"))
        assertFalse(Versions.isNewer("0.1", "0.1.0"))
        assertFalse(Versions.isNewer("0.0.9", "0.1.0"))
        assertEquals("1.2.3", Versions.clean(" V1.2.3+45 "))
    }

    @Test fun readsTheLatestRelease() {
        val json = """
            {"tag_name":"v0.2.0","name":"NextNotes v0.2.0","body":"- 新功能","html_url":"https://github.com/copylee711/nextnotes/releases/tag/v0.2.0",
             "draft":false,"prerelease":false,
             "assets":[{"name":"NextNotes-v0.2.0.apk.sha256","browser_download_url":"https://x/sha"},
                       {"name":"NextNotes-v0.2.0.apk","browser_download_url":"https://x/apk"}]}
        """.trimIndent()
        val r = Releases.parse(json)!!
        assertEquals("0.2.0", r.version)
        assertEquals("NextNotes v0.2.0", r.title)
        assertEquals("- 新功能", r.notes)
        assertEquals("https://x/apk", r.apkUrl)
    }

    @Test fun skipsPreReleasesAndJunk() {
        assertNull(Releases.parse("""{"tag_name":"v9.0.0","prerelease":true}"""))
        assertNull(Releases.parse("""{"message":"Not Found"}"""))
        assertNull(Releases.parse("<html>"))
        val bare = Releases.parse("""{"tag_name":"v0.3.0"}""")!!
        assertEquals("v0.3.0", bare.title)
        assertNull(bare.apkUrl)
        assertEquals(Releases.PAGE, bare.pageUrl)
    }
}
