package com.zyren.app

import com.zyren.app.atelier.Atelier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtelierTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun page(t: String) = "```html\n<!doctype html><html><body><p>$t</p><script>var a=1;</script></body></html>\n```"

    @Test fun createReviseRollbackDelete() = runBlocking {
        var n = 0
        val at = Atelier(tmp.newFolder("m")) { _, _ -> Result.success(page("v${++n}")) }
        val a = at.create("Hesap Defteri", "gelir gider", 1L).getOrThrow()
        assertEquals("hesap-defteri", a.id); assertEquals(1, a.current)
        val b = at.revise(a.id, "koyu tema", 2L).getOrThrow()
        assertEquals(2, b.current); assertEquals(listOf(1, 2), b.versions)
        assertTrue(at.html(a.id)!!.contains("v2"))
        val c = at.rollback(a.id, 1, 3L).getOrThrow()
        assertEquals(1, c.current); assertTrue(at.html(a.id)!!.contains("v1"))
        assertTrue(at.rollback(a.id, 9, 4L).isFailure)
        assertTrue(at.delete(a.id, 5L)); assertNull(at.get(a.id)); assertTrue(at.list().isEmpty())
    }

    @Test fun duplicateNamesGetUniqueIds() = runBlocking {
        val at = Atelier(tmp.newFolder("m2")) { _, _ -> Result.success(page("x")) }
        val a = at.create("Not", "d", 1L).getOrThrow(); val b = at.create("Not", "d", 2L).getOrThrow()
        assertNotEquals(a.id, b.id)
    }

    @Test fun invalidOutputIsRetriedWithFeedbackThenAccepted() = runBlocking {
        val prompts = mutableListOf<String>()
        val at = Atelier(tmp.newFolder("m3")) { _, u -> prompts += u
            Result.success(if (prompts.size == 1) "```html\n<html><body><script>fetch('https://x')</script></body></html>\n```" else page("ok")) }
        val a = at.create("Ağ", "d", 1L).getOrThrow()
        assertEquals(2, prompts.size)
        assertTrue(prompts[1].contains("REDDEDİLDİ")); assertTrue(prompts[1].contains("fetch"))
        assertNotNull(at.html(a.id))
    }

    @Test fun neverSavesUnsafeAfterMaxTries() = runBlocking {
        val at = Atelier(tmp.newFolder("m4")) { _, _ -> Result.success("```html\n<html><body><iframe></iframe></body></html>\n```") }
        assertTrue(at.create("K", "d", 1L).isFailure)
        assertTrue(at.list().isEmpty())
    }

    @Test fun modelFailureIsPropagated() = runBlocking {
        val at = Atelier(tmp.newFolder("m5")) { _, _ -> Result.failure(java.io.IOException("ağ yok")) }
        assertTrue(at.create("K", "d", 1L).exceptionOrNull() is java.io.IOException)
    }

    @Test fun oldVersionsArePruned() = runBlocking {
        val at = Atelier(tmp.newFolder("m6")) { _, _ -> Result.success(page("x")) }
        val a = at.create("P", "d", 1L).getOrThrow()
        repeat(Atelier.MAX_VERSIONS + 3) { at.revise(a.id, "r", 2L + it).getOrThrow() }
        assertTrue(at.get(a.id)!!.versions.size <= Atelier.MAX_VERSIONS)
    }
}
