package com.nextnotes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactsTest {
    @Test fun onlyHtmlAndSvgBlocksAreArtifacts() {
        assertEquals(Artifact.Kind.HTML, Artifacts.of("html", "<p>x</p>")!!.kind)
        assertEquals(Artifact.Kind.HTML, Artifacts.of(" HTML  ", "<p>x</p>")!!.kind)
        assertEquals(Artifact.Kind.SVG, Artifacts.of("svg", "<svg/>")!!.kind)
        assertNull(Artifacts.of("kotlin", "fun x() {}"))
        assertNull(Artifacts.of("", "<p>x</p>"))
        assertNull(Artifacts.of(null, "<p>x</p>"))
        assertNull(Artifacts.of("html", "  \n"))
    }

    @Test fun titleComesFromTheTitleTagThenAHeading() {
        assertEquals("抛物线 & 焦点", Artifacts.titleOf(Artifact.Kind.HTML, "<title> 抛物线 &amp; 焦点 </title><h1>别的</h1>"))
        assertEquals("二次函数", Artifacts.titleOf(Artifact.Kind.HTML, "<div><h2 class=\"t\">二次<b>函数</b></h2></div>"))
        assertEquals("交互演示", Artifacts.titleOf(Artifact.Kind.HTML, "<canvas></canvas>"))
        assertEquals("图示", Artifacts.titleOf(Artifact.Kind.SVG, "<svg></svg>"))
    }

    @Test fun thePolicyComesBeforeAnythingTheAiWrote() {
        val doc = Artifacts.document(Artifacts.of("html", "<!DOCTYPE html><script>fetch('https://x')</script><html><head></head></html>")!!)
        assertTrue(doc.startsWith("<!doctype html><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\""))
        assertTrue(doc.indexOf("Content-Security-Policy") < doc.indexOf("<script>"))
        // The AI's own doctype is dropped, so there is only one and it comes first.
        assertEquals(1, Regex("<!doctype", RegexOption.IGNORE_CASE).findAll(doc).count())
        assertTrue(Artifacts.CSP.startsWith("default-src 'none'"))
    }

    @Test fun fragmentsAndPicturesGetABasePage() {
        val fragment = Artifacts.document(Artifacts.of("html", "<p>hi</p>")!!)
        assertTrue(fragment.contains("font-family") && fragment.endsWith("<p>hi</p>"))
        val page = Artifacts.document(Artifacts.of("html", "<html><body>x</body></html>")!!)
        assertTrue(!page.contains("font-family"))
        val svg = Artifacts.document(Artifacts.of("svg", "<svg viewBox='0 0 10 10'/>")!!)
        assertTrue(svg.contains("svg{max-width:100%"))
    }
}
