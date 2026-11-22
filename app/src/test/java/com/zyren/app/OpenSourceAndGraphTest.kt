package com.zyren.app

import com.zyren.app.knowledge.GNode
import com.zyren.app.knowledge.KnowledgeGraph
import com.zyren.app.knowledge.OpenSourceCheck
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class OpenSourceAndGraphTest {
    private val now = Instant.parse("2026-11-22T00:00:00Z").toEpochMilli()

    @Test fun licenses() {
        assertTrue(OpenSourceCheck.license("MIT").second)
        assertTrue(OpenSourceCheck.license("apache-2.0").second)
        assertTrue(OpenSourceCheck.license("MPL-2.0").second)
        assertFalse(OpenSourceCheck.license("GPL-3.0").second)
        assertFalse(OpenSourceCheck.license("AGPL-3.0").second)
        assertFalse(OpenSourceCheck.license(null).second)
        assertFalse(OpenSourceCheck.license("NOASSERTION").second)
        assertFalse(OpenSourceCheck.license("WEIRD-1.0").second)
    }

    @Test fun freshnessAndVerdict() {
        assertTrue(OpenSourceCheck.freshness("2026-11-01T00:00:00Z", now).startsWith("aktif"))
        assertTrue(OpenSourceCheck.freshness("2026-01-01T00:00:00Z", now).startsWith("yavaş"))
        assertTrue(OpenSourceCheck.freshness("2020-01-01T00:00:00Z", now).startsWith("BAKIMSIZ"))
        assertEquals("son güncelleme bilinmiyor", OpenSourceCheck.freshness("saçma", now))
        assertTrue(OpenSourceCheck.verdict(true, false, "2026-11-01T00:00:00Z", now).startsWith("UYGUN görünüyor"))
        assertTrue(OpenSourceCheck.verdict(false, false, "2026-11-01T00:00:00Z", now).contains("lisans"))
        assertTrue(OpenSourceCheck.verdict(true, true, "2026-11-01T00:00:00Z", now).contains("arşiv"))
        assertTrue(OpenSourceCheck.verdict(true, false, "2020-01-01T00:00:00Z", now).startsWith("DİKKAT"))
        assertTrue(OpenSourceCheck.verdict(true, false, null, now).startsWith("DİKKAT"))
    }

    @Test fun graphLinksRelatedAndRendersSources() {
        val n = listOf(
            GNode("m1", "Proje", "Zyren android uygulaması derleme hatası", "Zyren android uygulaması derleme hatası", "hafıza"),
            GNode("k1", "teknik", "Android uygulaması derleme rehberi", "Android uygulaması derleme rehberi", "https://x.dev"),
            GNode("m2", "Tercih", "Kahveyi sütsüz severim", "Kahveyi sütsüz severim", "hafıza")
        )
        val e = KnowledgeGraph.edges(n)
        assertEquals(1, e.size)
        assertTrue(setOf(e[0].a, e[0].b) == setOf("m1", "k1"))
        val r = KnowledgeGraph.render(n, "android")
        assertTrue(r.contains("kaynak: https://x.dev")); assertFalse(r.contains("Kahve"))
        assertTrue(KnowledgeGraph.render(emptyList(), "x").contains("boş"))
        assertTrue(KnowledgeGraph.render(n, "bulunmayan").contains("eşleşen düğüm yok"))
    }
}
