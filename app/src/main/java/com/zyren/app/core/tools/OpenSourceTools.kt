package com.zyren.app.core.tools

import com.zyren.app.AppContainer
import com.zyren.app.agents.AgentRoles
import com.zyren.app.core.security.Capability
import com.zyren.app.knowledge.GNode
import com.zyren.app.knowledge.KnowledgeGraph
import com.zyren.app.knowledge.OpenSourceCheck
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*

private val nameRe = Regex("^[A-Za-z0-9_.-]{1,100}$")
private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

/** Açık kaynak merkezi, bilgi grafiği ve paralel ajan araçları. Hepsi ToolRegistry/izin hattından geçer; hiçbiri dosya yazmaz veya kod indirmez. */
fun registerOpenSourceTools(c: AppContainer) {
    val reg = c.tools

    reg.add(Tool("acik_kaynak_ara", "research", "sorgu — GitHub'da depo arar (yıldız, lisans, son güncelleme)", Capability.WEB,
        { "GitHub ara: " + it.str("sorgu").orEmpty() }) { a ->
        val q = java.net.URLEncoder.encode(a.req("sorgu"), "UTF-8")
        val items = Json.parseToJsonElement(c.web.githubJson("search/repositories?q=$q&sort=stars&order=desc&per_page=5")).jsonObject["items"]?.jsonArray
        if (items == null || items.isEmpty()) "Sonuç yok." else items.joinToString("\n") { e ->
            val o = e.jsonObject
            val lic = (o["license"] as? JsonObject)?.s("spdx_id")
            "• ${o.s("full_name")} ★${o.s("stargazers_count")} · ${OpenSourceCheck.license(lic).first.substringBefore(" →")} · ${OpenSourceCheck.freshness(o.s("pushed_at"), System.currentTimeMillis())}\n  ${o.s("description").orEmpty().take(100)}"
        } + "\n(Web kaynaklı, doğrulanmamış veri. Ayrıntı için acik_kaynak_incele.)"
    })
    reg.add(Tool("acik_kaynak_incele", "research", "sahip, depo — lisans uyumu, bakım durumu, arşiv bilgisi ve entegrasyon önerisi (kod indirmez/eklemez)", Capability.WEB,
        { "GitHub incele: " + it.str("sahip").orEmpty() + "/" + it.str("depo").orEmpty() }) { a ->
        val owner = a.req("sahip"); val repo = a.req("depo")
        require(nameRe.matches(owner) && nameRe.matches(repo)) { "sahip/depo adı geçersiz" }
        val o = Json.parseToJsonElement(c.web.githubJson("repos/$owner/$repo")).jsonObject
        val (licText, licOk) = OpenSourceCheck.license((o["license"] as? JsonObject)?.s("spdx_id"))
        val now = System.currentTimeMillis()
        val archived = o.s("archived") == "true"
        "• ${o.s("full_name")} ★${o.s("stargazers_count")} · açık sorun: ${o.s("open_issues_count")} · dil: ${o.s("language")}\n" +
            "• Lisans: $licText\n• Bakım: ${OpenSourceCheck.freshness(o.s("pushed_at"), now)}${if (archived) " · ARŞİVLENMİŞ" else ""}\n" +
            "• Sonuç: ${OpenSourceCheck.verdict(licOk, archived, o.s("pushed_at"), now)}\n" +
            "Bağımlılık analizi otomatik yapılmadı; eklemeden önce depodaki bağımlılık dosyalarını ve güvenlik duyurularını elle/ajanla inceleyin. Web kaynaklı veri doğrulanmamıştır."
    })

    reg.add(Tool("bilgi_grafigi", "memory", "sorgu? — hafıza ve bilgi merkezi kayıtları arasında ilişkili olabilecek düğümleri gösterir (sözcük benzerliği; kaynak belirtilir)", Capability.FILE_READ,
        { "Bilgi grafiği: " + it.str("sorgu").orEmpty() }) { a ->
        val nodes = c.memory.all().map { GNode("m${it.id}", it.category, it.content.take(70), it.content, "hafıza") } +
            c.knowledge.latest().map { GNode("k${it.id}", it.topic, it.title.take(70), it.title, it.url ?: "bilgi merkezi") }
        KnowledgeGraph.render(nodes, a.str("sorgu").orEmpty())
    })

    reg.add(Tool("paralel_ajan", "agents", "gorevler=[{rol,gorev}] (en çok 3) — uzman ajanları aynı anda çalıştırır; hepsi aynı izin/onay hattını kullanır", Capability.AGENT_RUN,
        { "Paralel ajan: " + (it.arr("gorevler")?.size ?: 0) + " görev" }) { a ->
        val jobs = (a.arr("gorevler") ?: throw IllegalArgumentException("'gorevler' dizi olmalı")).map { e ->
            val o = e.jsonObject
            (o.s("rol") ?: "kod") to (o.s("gorev") ?: throw IllegalArgumentException("her görevde 'gorev' gerekli"))
        }
        require(jobs.size in 1..3) { "1-3 görev verilebilir" }
        jobs.forEach { require(AgentRoles.get(it.first) != null) { "bilinmeyen rol: ${it.first}" } }
        coroutineScope { jobs.map { (rol, gorev) -> async { "▶ [$rol] $gorev\n" + c.engine.runAgent(rol, gorev) } }.awaitAll() }
            .joinToString("\n\n")
    })
}
