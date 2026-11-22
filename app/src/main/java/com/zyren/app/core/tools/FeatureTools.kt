package com.zyren.app.core.tools

import com.zyren.app.AppContainer
import com.zyren.app.agents.AgentRoles
import com.zyren.app.core.security.Capability
import com.zyren.app.plan.TaskState
import com.zyren.app.quality.SelfTest
import kotlinx.serialization.json.*
import java.time.ZoneId

private val none2: (Args) -> String = { "" }
private fun now() = System.currentTimeMillis()

/** Plan, bilgi, otomasyon, izleme, ikiz, hafıza bakımı, kalite, simülasyon, ajan ve özel araç araçları. Hepsi ToolRegistry/izin hattından geçer. */
fun registerFeatureTools(c: AppContainer) {
    val reg = c.tools

    // ---- Planlama ----
    reg.add(Tool("hedef_ekle", "plan", "hedef, ufuk (gunluk|haftalik|uzun), gorevler=[{baslik,oncelik(1-10),gun}] — büyük hedefi görevlere bölüp kaydeder", Capability.PLAN_WRITE,
        { "Hedef: " + it.str("hedef").orEmpty() }) { a ->
        val items = (a.arr("gorevler") ?: throw IllegalArgumentException("'gorevler' dizi olmalı")).map { e ->
            val o = e.jsonObject
            Triple(o["baslik"]!!.jsonPrimitive.content, o["oncelik"]?.jsonPrimitive?.intOrNull ?: 5, o["gun"]?.jsonPrimitive?.intOrNull)
        }
        val g = c.plans.addGoal(a.req("hedef"), a.str("ufuk") ?: "haftalik", items, now())
        "Hedef eklendi (${g.id}):\n" + c.plans.render()
    })
    reg.add(Tool("gorev_guncelle", "plan", "id, durum (BEKLIYOR|DEVAM|BITTI|BASARISIZ|ERTELENDI), not?, kanit? — BITTI için kanit (test sonucu) zorunlu", Capability.PLAN_WRITE,
        { "Görev: " + it.str("id").orEmpty() }) { a ->
        val st = TaskState.valueOf(a.req("durum").uppercase())
        c.plans.setState(a.req("id"), st, a.str("not") ?: "", a.str("kanit")) ?: "Güncellendi"
    })
    reg.add(Tool("plan_goster", "plan", "mevcut plan, ilerleme ve sıradaki görevler", Capability.PLAN_WRITE, { "Plan göster" }) {
        c.plans.render() + "\n\nSıradaki:\n" + c.plans.nextUp(now()).joinToString("\n") { "(${it.second.id}) ${it.second.title} [öncelik ${it.second.priority}]" }
    })
    reg.add(Tool("gorev_yurut", "agents", "id, rol? — plan görevini uzman ajanla yürütür (en çok 5 adım). Görevi BİTTİ yapmaz; sonucu nota yazar, kanıtı siz/test sağlar", Capability.AGENT_RUN,
        { "Görevi yürüt: " + it.str("id").orEmpty() }) { a ->
        val (_, t) = c.plans.find(a.req("id")) ?: throw IllegalArgumentException("Görev bulunamadı")
        val role = a.str("rol") ?: AgentRoles.guess(t.title)
        t.state = TaskState.DEVAM
        val out = c.engine.runAgent(role, "Görev: ${t.title}. Bitince ne yaptığını ve nasıl doğruladığını kısaca raporla.")
        c.plans.setState(t.id, if (out.startsWith("HATA")) TaskState.BASARISIZ else TaskState.DEVAM, "[ajan:$role] " + out.take(200), null)
        "Ajan ($role) raporu:\n$out\n\n(Görev BİTTİ olarak işaretlenmedi; kanıt gerekir.)"
    })
    reg.add(Tool("yeniden_planla", "plan", "geciken/başarısız görevleri yeniden planlar", Capability.PLAN_WRITE, { "Yeniden planla" }) {
        c.plans.reschedule(now()).ifEmpty { listOf("Geciken görev yok") }.joinToString("\n")
    })

    // ---- Bilgi Merkezi ----
    reg.add(Tool("bilgi_kaydet", "knowledge", "baslik, icerik, url?, etiketler? (virgüllü), konu? — bilgiyi konuya göre sınıflayıp kaydeder; aynı url yeni sürüm olur", Capability.KNOWLEDGE_WRITE,
        { "Bilgi kaydet: " + it.str("baslik").orEmpty() }) { a ->
        val i = c.knowledge.add(a.req("baslik"), a.req("icerik"), a.str("url"), a.str("etiketler")?.split(",") ?: emptyList(), a.str("konu"), now())
        "Kaydedildi: " + c.knowledge.label(i)
    })
    reg.add(Tool("bilgi_ara", "knowledge", "sorgu — kayıtlı bilgilerde arar", Capability.FILE_READ, { "Bilgi ara: " + it.str("sorgu").orEmpty() }) { a ->
        c.knowledge.search(a.req("sorgu")).joinToString("\n") { c.knowledge.label(it) }.ifEmpty { "Sonuç yok" }
    })
    reg.add(Tool("bilgi_oku", "knowledge", "id — kaydın içeriğini döner (web kaynaklıysa doğrulanmamış veridir)", Capability.FILE_READ, { "Bilgi oku" }) { a ->
        val i = c.knowledge.get(a.req("id")) ?: throw IllegalArgumentException("Kayıt yok")
        c.knowledge.label(i) + "\n<bilgi_icerigi>\n" + i.content + "\n</bilgi_icerigi>"
    })
    reg.add(Tool("bilgi_karsilastir", "knowledge", "id — kaydın önceki sürümüyle farkı", Capability.FILE_READ, { "Bilgi karşılaştır" }) { a -> c.knowledge.compare(a.req("id")) })
    reg.add(Tool("bilgi_dosyaya_aktar", "knowledge", "id, yol — kaydı çalışma alanındaki projede dosya olarak kullanır (yazma izni/onay normal hattan)", null, none2) { a ->
        val i = c.knowledge.get(a.req("id")) ?: throw IllegalArgumentException("Kayıt yok")
        c.workspace.write(a.req("yol"), "# ${i.title}\n\nKaynak: ${i.url ?: "yok"} · konu: ${i.topic} · v${i.version}\n\n${i.content}\n").fold({ "Yazıldı: ${a.req("yol")}" }, { "HATA: ${it.message}" })
    })

    // ---- Güvenli Otomasyon ----
    reg.add(Tool("otomasyon_ekle", "automation", "ad, tetikleyici (gunluk:HH:MM | aralik:DAKIKA≥15), arac, args?, gunluk_sinir? — zamanlı kural; arka planda yalnızca güvenli araçlar çalışır", Capability.AUTOMATION_MANAGE,
        { "OTOMASYON KURALI: ${it.str("ad")} · ${it.str("tetikleyici")} → ${it.str("arac")} ${it.o["args"] ?: ""}" }) { a ->
        val r = c.automation.add(a.req("ad"), a.req("tetikleyici"), a.req("arac"), (a.o["args"] as? JsonObject) ?: JsonObject(emptyMap()), a.int("gunluk_sinir") ?: 24)
        "Kural eklendi (${r.id}). Arka planda çalışması için Ayar/İzinler'de 'Arka planda otomasyon' izni açık olmalı. İzin verilen arka plan araçları: ${AutomationEngineInfo.allowed}"
    })
    reg.add(Tool("otomasyon_listele", "automation", "kuralları ve son geçmişi gösterir", Capability.FILE_READ, { "Otomasyon listele" }) {
        c.automation.rules.joinToString("\n") { "(${it.id}) ${it.name} · ${it.trigger} → ${it.tool} · ${if (it.enabled) "açık" else "kapalı"} · çalışma ${it.runs} · hata ${it.failures}" }.ifEmpty { "Kural yok" } +
            "\n\nGeçmiş:\n" + c.automation.history().take(10).joinToString("\n")
    })
    reg.add(Tool("otomasyon_sil", "automation", "id — kuralı siler", Capability.AUTOMATION_MANAGE, { "Otomasyon sil: " + it.str("id").orEmpty() }) { a -> c.automation.remove(a.req("id")); "Silindi" })

    // ---- Hata ve Performans / İkiz ----
    reg.add(Tool("sistem_raporu", "monitor", "RAM/CPU/pil/depolama, yavaşlama, son 24 saat hataları ve çözüm önerileri", Capability.PHONE_INFO, { "Sistem raporu" }) {
        c.perfSample()
        c.perf.report(c.db.auditDao().errorsSince(now() - 86_400_000L).map { "${it.capability}: ${it.outcome}" })
    })
    reg.add(Tool("ikiz_guncelle", "twin", "telefonun dijital ikizi için durum anlık görüntüsü alır (yalnızca durum; kişisel içerik yok)", Capability.PHONE_INFO, { "İkiz güncelle" }) {
        val s = c.twin.snapshot(now()); c.twin.save(s); "İkiz güncellendi.\n" + c.twin.summary()
    })
    reg.add(Tool("ikiz_goster", "twin", "son ikiz özetini gösterir", Capability.PHONE_INFO, { "İkiz göster" }) { c.twin.summary() })
    reg.add(Tool("ikiz_karsilastir", "twin", "son iki ikiz anlık görüntüsünün farkı", Capability.PHONE_INFO, { "İkiz karşılaştır" }) { c.twin.diffLast() })

    // ---- Yaşayan hafıza ----
    reg.add(Tool("hafiza_bakim", "memory", "hafızayı bakıma alır: tekrarları birleştirir, kullanılmayanın önemini azaltır, çelişkileri bildirir", Capability.MEMORY_WRITE, { "Hafıza bakımı" }) { c.memory.maintain(now()) })
    reg.add(Tool("hafiza_ozet", "memory", "hafıza istatistiği", Capability.FILE_READ, { "Hafıza özeti" }) { c.memory.summary() })

    // ---- Kalite ----
    reg.add(Tool("kalite_durumu", "quality", "her özelliğin gerçek durumu (planlandı/kod yazıldı/derlendi/cihazda test edildi/tamam) ve son test sonucu", Capability.FILE_READ, { "Kalite durumu" }) { c.features.render() })
    reg.add(Tool("test_calistir", "quality", "bu cihazda kendi kendine testleri çalıştırır; başarısızlıkları hafızaya yazar. Durumu YÜKSELTMEZ", Capability.FILE_READ, { "Testleri çalıştır" }) { c.runSelfTests() })

    // ---- Simülasyon laboratuvarı ----
    reg.add(Tool("simule_et", "lab", "arac, args? — aracı GERÇEKTEN çalıştırmadan yetki/izin/onay durumunu ve ikiz tahminini raporlar", null, none2) { a ->
        val inner = Args((a.o["args"] as? JsonObject) ?: JsonObject(emptyMap()))
        c.tools.simulate(a.req("arac"), inner, c.twinHint(a.req("arac"), inner))
    })

    // ---- Çoklu uzman ajanlar ----
    reg.add(Tool("ajan_gorevlendir", "agents", "rol (${AgentRoles.all.joinToString("|") { it.id }}), gorev — uzman ajan kendi araçlarıyla çalışıp rapor verir (aynı izin/onay hattı)", Capability.AGENT_RUN,
        { "Ajan: ${it.str("rol")}" }) { a -> c.engine.runAgent(a.req("rol"), a.req("gorev")) })

    // ---- Kendi araçlarını geliştirme (saf JS, test + onay şartlı) ----
    reg.add(Tool("ozel_arac_olustur", "selftools", "ad (ozel_...), aciklama, kod ('function run(args){...}' saf JS), test_girdi (JSON), beklenen (dize) — test geçerse ve kullanıcı onaylarsa yeni araç eklenir", Capability.TOOL_CREATE,
        { "ÖZEL ARAÇ OLUŞTUR: ${it.str("ad")}\n${it.str("aciklama")}\nKod:\n${it.str("kod").orEmpty().take(800)}\nTest: ${it.str("test_girdi")} → ${it.str("beklenen")}" }) { a ->
        val t = CustomTool(a.req("ad"), a.str("aciklama") ?: "", a.req("kod"))
        c.customTools.validate(t.name, t.code, c.tools.has(t.name))?.let { return@Tool "HATA: $it" }
        c.customTools.addIfTestPasses(t, a.str("test_girdi") ?: "{}", a.req("beklenen"))?.let { return@Tool "HATA: $it" }
        c.customTools.register(c.tools, t)
        "Araç etkinleştirildi: ${t.name}"
    })
    reg.add(Tool("ozel_arac_sil", "selftools", "ad — özel aracı siler", Capability.TOOL_CREATE, { "Özel araç sil: " + it.str("ad").orEmpty() }) { a ->
        val n = a.req("ad"); require(n.startsWith("ozel_")) { "Yalnızca özel araçlar silinebilir" }
        c.customTools.remove(n); c.tools.remove(n); "Silindi"
    })
}

object AutomationEngineInfo { val allowed: String get() = com.zyren.app.automation.AutomationEngine.BACKGROUND_ALLOWED.joinToString(", ") }
