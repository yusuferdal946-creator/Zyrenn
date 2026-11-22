package com.zyren.app

import com.zyren.app.automation.AutomationEngine
import com.zyren.app.core.memory.MemoryEntity
import com.zyren.app.core.memory.MemoryMaintenance
import com.zyren.app.core.net.NetUtil
import com.zyren.app.core.security.AnomalyDetector
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.Risk
import com.zyren.app.core.security.defaultEnabled
import com.zyren.app.core.tools.Args
import com.zyren.app.core.tools.ToolProtocol
import com.zyren.app.knowledge.KnowledgeHub
import com.zyren.app.monitor.PerfMonitor
import com.zyren.app.monitor.Sample
import com.zyren.app.plan.PlanEngine
import com.zyren.app.plan.TaskState
import com.zyren.app.quality.FeatureRegistry
import com.zyren.app.quality.FeatureStatus
import com.zyren.app.twin.TwinDiff
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class LogicTests {
    // --- izin varsayılanları ve şüpheli işlem
    @Test fun riskyPermissionsOffByDefault() {
        for (c in listOf(Capability.SMS_SEND, Capability.CALL_PHONE, Capability.ACCESSIBILITY, Capability.SHELL_ANY, Capability.NOTIF_READ, Capability.AUTOMATION_RUN, Capability.TOOL_CREATE, Capability.AUTOMATION_MANAGE))
            assertFalse(c.name, defaultEnabled(c))
        assertTrue(defaultEnabled(Capability.FILE_READ))
    }
    @Test fun criticalCapabilitiesAreCritical() {
        for (c in listOf(Capability.SMS_SEND, Capability.CALL_PHONE, Capability.FILE_DELETE, Capability.FILE_OVERWRITE, Capability.SHELL_ANY, Capability.TOOL_CREATE, Capability.AUTOMATION_MANAGE, Capability.ACCESSIBILITY))
            assertEquals(c.name, Risk.CRITICAL, c.risk)
    }
    @Test fun anomalyTriggersOnCriticalBurst() {
        val d = AnomalyDetector(); var r: String? = null
        for (i in 0..10) r = d.record(1000L + i, critical = true, denied = false) ?: r
        assertNotNull(r)
    }
    @Test fun anomalyQuietForNormalUse() {
        val d = AnomalyDetector(); for (i in 0..20) assertNull(d.record(i * 10_000L, critical = false, denied = false))
    }
    @Test fun anomalyTriggersOnDeniedBurst() {
        val d = AnomalyDetector(); var r: String? = null
        for (i in 0..15) r = d.record(1000L + i, critical = false, denied = true) ?: r
        assertNotNull(r)
    }
    @Test fun netUtil() {
        assertTrue(NetUtil.isPrivateHost("192.168.1.5")); assertTrue(NetUtil.isPrivateHost("127.0.0.1")); assertTrue(NetUtil.isPrivateHost("localhost"))
        assertFalse(NetUtil.isPrivateHost("8.8.8.8")); assertFalse(NetUtil.isPrivateHost("172.32.0.1")); assertTrue(NetUtil.isPrivateHost("172.20.0.1"))
    }
    @Test fun cleartextOnlyLoopback() {
        assertTrue(NetUtil.isCleartextAllowed("127.0.0.1")); assertTrue(NetUtil.isCleartextAllowed("localhost")); assertTrue(NetUtil.isCleartextAllowed("LOCALHOST"))
        for (h in listOf("192.168.1.5", "10.0.0.2", "8.8.8.8", "example.com", "127.0.0.2", "evil-localhost.com")) assertFalse(h, NetUtil.isCleartextAllowed(h))
    }
    // --- araç protokolü
    @Test fun toolProtocol() {
        val c = ToolProtocol.parse("tamam <arac>{\"ad\":\"pil_durumu\",\"args\":{\"x\":\"1\"}}</arac>")!!
        assertEquals("pil_durumu", c.name); assertEquals("1", c.args.str("x"))
        assertNull(ToolProtocol.parse("sadece metin")); assertEquals(ToolProtocol.INVALID, ToolProtocol.parse("<arac>{bozuk</arac>")!!.name)
        assertEquals("a", ToolProtocol.parse("<arac>```json\n{\"ad\":\"a\"}\n```</arac>")!!.name)
    }
    // --- planlama
    @Test fun planDoneNeedsEvidence() {
        val p = PlanEngine(null); val g = p.addGoal("h", "gunluk", listOf(Triple("t", 5, 1)), 0L)
        assertNotNull(p.setState(g.tasks[0].id, TaskState.BITTI, "", null)); assertNull(p.setState(g.tasks[0].id, TaskState.BITTI, "", "test geçti"))
    }
    @Test fun planReschedulesThenDefers() {
        val p = PlanEngine(null); val g = p.addGoal("h", "gunluk", listOf(Triple("t", 5, 1)), 0L); val t = g.tasks[0]
        var now = 2 * PlanEngine.DAY
        assertEquals(1, p.reschedule(now).size); assertEquals(TaskState.BEKLIYOR, t.state); assertEquals(6, t.priority)
        now += 5 * PlanEngine.DAY; p.reschedule(now); now += 5 * PlanEngine.DAY; p.reschedule(now)
        assertEquals(TaskState.ERTELENDI, t.state)
    }
    @Test fun planPrioritizes() {
        val p = PlanEngine(null); p.addGoal("h", "uzun", listOf(Triple("düşük", 2, null), Triple("yüksek", 9, null)), 0L)
        assertEquals("yüksek", p.nextUp(0L).first().second.title)
    }
    // --- bilgi merkezi
    @Test fun knowledgeVersionsAndCompare() {
        val k = KnowledgeHub(null); val a = k.add("A", "bir\niki", "http://x", emptyList(), null, 1L); val b = k.add("A", "bir\nüç", "http://x", emptyList(), null, 2L)
        assertEquals(2, b.version); assertEquals(a.id, b.supersedes); assertTrue(k.compare(b.id).contains("+ üç")); assertTrue(k.compare(b.id).contains("- iki"))
        assertEquals(1, k.latest().size)
    }
    @Test fun knowledgeClassifyAndSearch() {
        val k = KnowledgeHub(null); k.add("Kotlin ipuçları", "gradle derleme hataları", null, listOf("kod"), null, 1L)
        assertEquals("kod", k.items[0].topic); assertEquals(1, k.search("gradle").size); assertTrue(k.search("yokkelime").isEmpty())
    }
    // --- otomasyon
    @Test fun automationBackgroundSkipsCriticalAndUnlisted() = runBlocking {
        val a = AutomationEngine(null, null); a.add("k", "aralik:15", "sms_gonder", JsonObject(emptyMap())); a.add("u", "aralik:15", "dosya_sil", JsonObject(emptyMap()))
        var ran = 0
        a.runDue(30 * 60_000L, ZoneId.of("UTC"), false, { Risk.CRITICAL }, { _, _ -> ran++; "ok" })
        assertEquals(0, ran)
    }
    @Test fun automationRunsAllowedAndStopsAfterFailures() = runBlocking {
        val a = AutomationEngine(null, null); val r = a.add("p", "aralik:15", "pil_durumu", JsonObject(emptyMap()))
        var now = 20 * 60_000L; var ran = 0
        a.runDue(now, ZoneId.of("UTC"), false, { Risk.SAFE }, { _, _ -> ran++; "tamam" }); assertEquals(1, ran)
        a.runDue(now + 60_000, ZoneId.of("UTC"), false, { Risk.SAFE }, { _, _ -> ran++; "tamam" }); assertEquals(1, ran) // aralık dolmadı
        repeat(6) { now += 20 * 60_000L; a.runDue(now, ZoneId.of("UTC"), false, { Risk.SAFE }, { _, _ -> "HATA: x" }) }
        assertTrue(r.failures >= AutomationEngine.MAX_FAILURES); assertFalse(a.isDue(r, now + 99 * 60_000L, ZoneId.of("UTC")))
    }
    @Test fun automationDailyTriggerAndValidation() {
        val a = AutomationEngine(null, null); val r = a.add("g", "gunluk:08:00", "pil_durumu", JsonObject(emptyMap()))
        val z = ZoneId.of("UTC"); val day = 86_400_000L
        assertFalse(a.isDue(r, 7 * 3_600_000L, z)); assertTrue(a.isDue(r, 9 * 3_600_000L, z))
        r.lastRun = 9 * 3_600_000L; assertFalse(a.isDue(r, 10 * 3_600_000L, z)); assertTrue(a.isDue(r, day + 9 * 3_600_000L, z))
        assertNotNull(a.validateTrigger("aralik:5")); assertNotNull(a.validateTrigger("gunluk:25:00")); assertNotNull(a.validateTrigger("x")); assertNull(a.validateTrigger("aralik:15"))
    }
    @Test fun automationRespectsDailyLimit() = runBlocking {
        val a = AutomationEngine(null, null); a.add("p", "aralik:15", "pil_durumu", JsonObject(emptyMap()), maxPerDay = 2)
        var ran = 0; var now = 20 * 60_000L
        repeat(5) { a.runDue(now, ZoneId.of("UTC"), false, { Risk.SAFE }, { _, _ -> ran++; "ok" }); now += 20 * 60_000L }
        assertEquals(2, ran)
    }
    // --- yaşayan hafıza
    private fun mem(id: Long, c: String, imp: Int = 5, pinned: Boolean = false, t: Long = 0L, cat: String = "Tercih") = MemoryEntity(id, cat, c, pinned, t, t, imp, 0, t)
    @Test fun memoryDuplicatesMerged() { assertEquals(listOf(1L), MemoryMaintenance.plan(listOf(mem(1, "Kahve sade!", 3), mem(2, "kahve sade", 6)), 1000L).deleteIds) }
    @Test fun memoryDecayAndPinned() {
        val old = mem(1, "eski", 2, t = 0L); val pin = mem(2, "sabit", 1, pinned = true, t = 0L); val err = mem(3, "hata", 1, t = 0L, cat = "Hata/Çözüm")
        val p = MemoryMaintenance.plan(listOf(old, pin, err), 40 * MemoryMaintenance.DAY)
        assertEquals(1, p.updates.single().importance); assertTrue(p.deleteIds.isEmpty())
        val p2 = MemoryMaintenance.plan(listOf(old.copy(importance = 1)), 40 * MemoryMaintenance.DAY); assertEquals(listOf(1L), p2.deleteIds)
    }
    @Test fun memoryConflictsReported() { assertEquals(1, MemoryMaintenance.plan(listOf(mem(1, "kahveyi sade içerim"), mem(2, "kahveyi sade değil şekerli")), 0L).conflicts.size) }
    // --- kalite kapısı
    @Test fun featureGate() {
        val f = FeatureRegistry(null); f.ensure("x", "X", FeatureStatus.KOD_YAZILDI)
        assertNotNull(f.userSet("x", FeatureStatus.TAMAM, "", 0L)); assertNotNull(f.userSet("x", FeatureStatus.CIHAZDA_TEST_EDILDI, "denendi", 0L))
        assertNull(f.userSet("x", FeatureStatus.DERLENDI, "CI yeşil", 0L)); assertNotNull(f.userSet("x", FeatureStatus.TAMAM, "denendi", 0L))
        assertNull(f.userSet("x", FeatureStatus.CIHAZDA_TEST_EDILDI, "telefonda denendi", 0L)); assertNull(f.userSet("x", FeatureStatus.TAMAM, "tam", 0L))
    }
    @Test fun failedAutoTestDowngradesAndPassDoesNotUpgrade() {
        val f = FeatureRegistry(null); f.ensure("x", "X", FeatureStatus.KOD_YAZILDI); f.userSet("x", FeatureStatus.DERLENDI, "ok", 0L)
        f.recordAuto("x", true, "geçti", 1L); assertEquals(FeatureStatus.DERLENDI, f.get("x")!!.status)
        f.recordAuto("x", false, "bozuk", 2L); assertEquals(FeatureStatus.KOD_YAZILDI, f.get("x")!!.status)
    }
    // --- performans
    @Test fun perfIssuesAndAdvice() {
        val p = PerfMonitor(null); p.add(Sample(0, 200, 12000, 10, false, 1.0, 90))
        val i = p.issues(); assertEquals(4, i.size); assertTrue(p.report(listOf("A: x", "A: y", "B: z")).contains("A×2"))
    }
    @Test fun perfSlowdown() {
        val p = PerfMonitor(null); repeat(6) { p.recordLatency(1000) }; assertNull(p.slowdown()); repeat(5) { p.recordLatency(25_000) }; assertNotNull(p.slowdown())
    }
    // --- ikiz
    @Test fun twinDiff() {
        val a = buildJsonObject { put("pil", 50); put("uygulamalar", buildJsonArray { add("A"); add("B") }); put("izinler", buildJsonObject { put("SMS", false) }) }
        val b = buildJsonObject { put("pil", 40); put("uygulamalar", buildJsonArray { add("B"); add("C") }); put("izinler", buildJsonObject { put("SMS", true) }) }
        val d = TwinDiff.diff(a, b).joinToString("|")
        assertTrue(d.contains("pil: 50 → 40")); assertTrue(d.contains("YENİ uygulama: C")); assertTrue(d.contains("KALDIRILAN uygulama: A")); assertTrue(d.contains("izin SMS: false → true"))
    }
    @Test fun agentRoleGuess() {
        assertEquals("arastirma", com.zyren.app.agents.AgentRoles.guess("Kaynakları araştır")); assertEquals("test", com.zyren.app.agents.AgentRoles.guess("Testleri doğrula"))
        assertEquals("telefon", com.zyren.app.agents.AgentRoles.guess("Anneye mesaj at")); assertEquals("kod", com.zyren.app.agents.AgentRoles.guess("Ekranı yaz"))
        assertEquals(4, com.zyren.app.agents.AgentRoles.all.size)
    }
    @Test fun argsHelpers() {
        val a = Args(buildJsonObject { put("n", "5"); put("e", "") })
        assertEquals(5, a.int("n")); assertThrows(IllegalArgumentException::class.java) { a.req("e") }; assertNull(a.str("yok"))
    }
}
