package com.zyren.app.quality

import android.content.Context
import com.zyren.app.AppContainer
import com.zyren.app.automation.AutomationEngine
import com.zyren.app.core.memory.MemoryEntity
import com.zyren.app.core.memory.MemoryMaintenance
import com.zyren.app.core.security.AnomalyDetector
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.PathGuard
import com.zyren.app.core.security.Risk
import com.zyren.app.core.security.defaultEnabled
import com.zyren.app.core.tools.JsSandbox
import com.zyren.app.core.tools.SafeShell
import com.zyren.app.core.workspace.ZipSafe
import com.zyren.app.knowledge.KnowledgeHub
import com.zyren.app.plan.PlanEngine
import com.zyren.app.plan.TaskState
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TestResult(val feature: String, val name: String, val passed: Boolean, val detail: String)

/** Bu CİHAZDA çalışan gerçek testler (CI'ın çalıştıramadığı Keystore/Room/Android bileşenleri dahil). Gerçek bileşenleri sınar, sahte değil. */
object SelfTest {
    suspend fun run(c: AppContainer): List<TestResult> {
        val out = mutableListOf<TestResult>()
        suspend fun case(feature: String, name: String, block: suspend () -> Unit) {
            try { block(); out += TestResult(feature, name, true, "ok") }
            catch (e: Throwable) { out += TestResult(feature, name, false, e.message ?: e.javaClass.simpleName) }
        }
        val tmp = File(c.appContext.cacheDir, "selftest-" + System.nanoTime()).apply { mkdirs() }
        try {
            case("sandbox", "yol_kacisi_engelli") {
                val root = File(tmp, "ws").apply { mkdirs() }
                val g = PathGuard(root)
                check(runCatching { g.resolve("../x") }.isFailure) { "../ geçti" }
                check(g.contains(g.resolve("/etc/passwd"))) { "mutlak yol dışarı çıktı" }
            }
            case("terminal", "guvenli_terminal") {
                val root = File(tmp, "ws2").apply { mkdirs() }
                var trashed = 0
                val sh = SafeShell(PathGuard(root), { _, _ -> true }, { f -> trashed++; f.deleteRecursively() })
                check(sh.run("echo selam").getOrNull() == "selam") { "echo" }
                check(sh.run("touch a.txt").isSuccess && sh.run("ls").getOrNull()?.contains("a.txt") == true) { "touch/ls" }
                check(sh.run("cat ../x").isFailure) { "../ engellenmedi" }
                check(sh.run("cat /etc/passwd").isFailure) { "mutlak yol" }
                check(sh.run("ls | cat").isFailure) { "boru engellenmedi" }
                check(sh.run("sh -c id").isFailure) { "sh çalıştı" }
                check(sh.run("rm a.txt").isSuccess && trashed == 1) { "rm" }
            }
            case("zip", "zip_slip_ve_bomba") {
                val z = File(tmp, "kotu.zip")
                ZipOutputStream(z.outputStream()).use { it.putNextEntry(ZipEntry("../evil.txt")); it.write("x".toByteArray()); it.closeEntry() }
                val dest = File(tmp, "d1")
                check(runCatching { ZipSafe.extract(z, dest) }.isFailure) { "zip-slip geçti" }
                check(!File(tmp, "evil.txt").exists()) { "dosya dışarı yazıldı" }
                val z2 = File(tmp, "bomba.zip")
                ZipOutputStream(z2.outputStream()).use { it.putNextEntry(ZipEntry("a.bin")); it.write(ByteArray(5000)); it.closeEntry() }
                check(runCatching { ZipSafe.extract(z2, File(tmp, "d2"), limits = ZipSafe.Limits(maxTotal = 1000)) }.isFailure) { "boyut sınırı çalışmadı" }
            }
            case("js_sandbox", "sonsuz_dongu_java_erisimi") {
                val js = JsSandbox(timeoutMs = 700)
                val t0 = System.currentTimeMillis()
                val r = js.evaluate("while(true){}")
                check(r.error?.contains("zaman") == true) { "sonsuz döngü kesilmedi: ${r.error}" }
                check(System.currentTimeMillis() - t0 < 5000) { "çok geç kesildi" }
                check(js.evaluate("1+2").value == "3") { "temel hesap" }
                check(js.evaluate("java.lang.System.exit(0)").error != null) { "Java erişimi açık!" }
                check(js.evaluate("console.log('a'); 1").output == "a") { "console.log" }
            }
            case("api_anahtar", "sifreleme_ve_baglama") {
                val prefs = c.appContext.getSharedPreferences("zyren_keys", Context.MODE_PRIVATE)
                c.secure.setApiKey("selftest", "abc123XYZ")
                try {
                    check(c.secure.apiKey("selftest") == "abc123XYZ") { "geri okuma" }
                    val raw = prefs.getString("k_selftest", null) ?: error("kayıt yok")
                    check(!raw.contains("abc123XYZ")) { "düz metin saklanıyor" }
                    prefs.edit().putString("k_selftest2", raw).apply()
                    check(c.secure.apiKey("selftest2") == null) { "şifreli metin başka sağlayıcıya taşınabildi" }
                } finally { c.secure.setApiKey("selftest", null); prefs.edit().remove("k_selftest2").apply() }
            }
            case("izin_yonetimi", "varsayilan_ve_supheli_islem") {
                check(!defaultEnabled(Capability.SMS_SEND) && !defaultEnabled(Capability.SHELL_ANY) && !defaultEnabled(Capability.ACCESSIBILITY)) { "riskli izin varsayılan açık" }
                check(defaultEnabled(Capability.FILE_READ)) { "temel izin kapalı" }
                val d = AnomalyDetector()
                var reason: String? = null
                for (i in 0..10) reason = d.record(1000L + i, critical = true, denied = false) ?: reason
                check(reason != null) { "şüpheli işlem algılanmadı" }
            }
            case("planlama", "kanitsiz_bitti_ve_yeniden_planlama") {
                val p = PlanEngine(null)
                val g = p.addGoal("test", "gunluk", listOf(Triple("a", 5, 1)), 0L)
                check(p.setState(g.tasks[0].id, TaskState.BITTI, "", null) != null) { "kanıtsız bitti kabul edildi" }
                check(p.reschedule(3 * PlanEngine.DAY).isNotEmpty()) { "geciken yeniden planlanmadı" }
            }
            case("hafiza", "ekle_tekrar_sil") {
                val tag = "__selftest__" + System.nanoTime()
                check(c.memory.add("Görev", tag)) { "ekleme" }
                check(!c.memory.add("Görev", tag)) { "tekrar engellenmedi" }
                val m = c.db.memoryDao().findByContent(tag)
                check(m != null) { "kayıt yok" }
                c.db.memoryDao().delete(m!!)
            }
            case("yasayan_hafiza", "tekrar_birlestirme") {
                val a = MemoryEntity(1, "Tercih", "Kahve sade!", false, 0, 0, 5, 0, 0)
                val b = MemoryEntity(2, "Tercih", "kahve sade", false, 0, 0, 3, 0, 0)
                check(MemoryMaintenance.plan(listOf(a, b), 1000L).deleteIds == listOf(2L)) { "tekrar silinmedi" }
            }
            case("bilgi_merkezi", "surum_karsilastirma") {
                val k = KnowledgeHub(null)
                val x = k.add("A", "bir\niki", "http://x", emptyList(), null, 1L)
                val y = k.add("A", "bir\nüç", "http://x", emptyList(), null, 2L)
                check(k.compare(y.id).contains("+ üç") && x.version == 1 && y.version == 2) { "karşılaştırma" }
            }
            case("otomasyon", "arka_planda_kritik_atlanir") {
                val a = AutomationEngine(null, null)
                a.add("r", "aralik:15", "sms_gonder", JsonObject(emptyMap()))
                var ran = 0
                a.runDue(20 * 60_000L, java.time.ZoneId.of("UTC"), false, { Risk.CRITICAL }, { _, _ -> ran++; "ok" })
                check(ran == 0) { "kritik araç arka planda çalıştı" }
            }
            case("kalite_kapisi", "testsiz_tamam_olmaz") {
                val f = FeatureRegistry(null)
                f.ensure("x", "X", FeatureStatus.KOD_YAZILDI)
                check(f.userSet("x", FeatureStatus.TAMAM, "", 0L) != null) { "kanıtsız TAMAM" }
                check(f.userSet("x", FeatureStatus.CIHAZDA_TEST_EDILDI, "denendi", 0L) != null) { "derlenmeden cihaz testi" }
            }
        } finally { tmp.deleteRecursively() }
        return out
    }
}
