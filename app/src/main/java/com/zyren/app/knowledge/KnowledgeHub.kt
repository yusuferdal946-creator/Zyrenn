package com.zyren.app.knowledge

import com.zyren.app.store.JsonStore
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.UUID

class KnowledgeItem(
    val id: String, val title: String, val topic: String, val tags: List<String>, val url: String?,
    val content: String, val version: Int, val createdAt: Long, val supersedes: String?
) { val webSourced: Boolean get() = url != null }

/** Araştırma/belge arşivi: konu sınıflama, kaynak+bağlantı, sürüm (eski/yeni karşılaştırma), arama. Web kaynaklıları "doğrulanmamış" etiketlidir. */
class KnowledgeHub(private val store: JsonStore? = null, private val idGen: () -> String = { UUID.randomUUID().toString().take(8) }) {
    companion object { const val MAX_ITEMS = 500; const val MAX_CONTENT = 20_000 }
    private val tr = Locale("tr", "TR")
    val items = mutableListOf<KnowledgeItem>()

    private val topics = mapOf(
        "kod" to listOf("kotlin", "java", "kod", "fonksiyon", "api", "derleme", "gradle", "python", "javascript", "algoritma"),
        "telefon" to listOf("android", "telefon", "sms", "bildirim", "batarya", "uygulama"),
        "güvenlik" to listOf("güvenlik", "şifre", "izin", "saldırı", "zafiyet", "şifreleme", "gizlilik"),
        "yapay zekâ" to listOf("model", "llm", "yapay zeka", "yapay zekâ", "gemma", "ajan", "prompt"),
        "finans" to listOf("fiyat", "borsa", "kur", "ücret", "yatırım"),
        "sağlık" to listOf("sağlık", "ilaç", "hastalık", "tedavi")
    )

    init { reload() }

    fun classify(text: String): String {
        val t = text.lowercase(tr)
        return topics.map { (k, ws) -> k to ws.count { t.contains(it) } }.filter { it.second > 0 }.maxByOrNull { it.second }?.first ?: "genel"
    }

    /** Aynı url varsa yeni SÜRÜM olarak eklenir (eskisi saklanır, karşılaştırılabilir). */
    fun add(title: String, content: String, url: String?, tags: List<String>, topic: String?, now: Long): KnowledgeItem {
        require(title.isNotBlank() && content.isNotBlank()) { "Başlık ve içerik gerekli" }
        val prev = url?.let { u -> items.filter { it.url == u }.maxByOrNull { it.version } }
        val it = KnowledgeItem(idGen(), title.take(200), topic ?: classify(title + " " + content), tags.map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.take(10),
            url, content.take(MAX_CONTENT), (prev?.version ?: 0) + 1, now, prev?.id)
        items += it
        while (items.size > MAX_ITEMS) { val old = items.filter { x -> items.any { y -> y.supersedes == x.id } }.minByOrNull { x -> x.createdAt } ?: items.minByOrNull { x -> x.createdAt }!!; items.remove(old) }
        save(); return it
    }

    fun latest(): List<KnowledgeItem> = items.filter { x -> items.none { it.supersedes == x.id } }

    fun get(id: String) = items.firstOrNull { it.id == id }

    fun search(q: String): List<KnowledgeItem> {
        val w = q.lowercase(tr).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (w.isEmpty()) return emptyList()
        return latest().map { i ->
            val hay = (i.title + " " + i.tags.joinToString(" ") + " " + i.topic).lowercase(tr)
            val body = i.content.lowercase(tr)
            i to w.sumOf { x -> (if (hay.contains(x)) 3 else 0) + (if (body.contains(x)) 1 else 0) }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }.take(10)
    }

    fun compare(id: String): String {
        val n = get(id) ?: return "Kayıt bulunamadı"
        val o = n.supersedes?.let { get(it) } ?: return "Bu kaydın önceki sürümü yok"
        val a = o.content.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val b = n.content.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val diff = (b - a).map { "+ $it" } + (a - b).map { "- $it" }
        return "v${o.version} → v${n.version}: ${diff.size} satır farkı\n" + diff.take(40).joinToString("\n").ifEmpty { "(fark yok)" }
    }

    fun topicCounts(): Map<String, Int> = latest().groupingBy { it.topic }.eachCount()

    fun remove(id: String) { items.removeAll { it.id == id }; save() }

    fun label(i: KnowledgeItem) = "(${i.id}) ${i.title} · ${i.topic} · v${i.version}" + (i.url?.let { " · $it [web kaynaklı, doğrulanmamış]" } ?: "")

    private fun save() {
        store?.write(buildJsonArray { items.forEach { i -> add(buildJsonObject {
            put("id", i.id); put("title", i.title); put("topic", i.topic); put("tags", buildJsonArray { i.tags.forEach { add(it) } })
            put("url", i.url ?: ""); put("content", i.content); put("version", i.version); put("createdAt", i.createdAt); put("supersedes", i.supersedes ?: "") }) } })
    }

    fun reload() {
        items.clear()
        store?.read()?.forEach { e -> runCatching {
            val o = e.jsonObject
            items += KnowledgeItem(o["id"]!!.jsonPrimitive.content, o["title"]!!.jsonPrimitive.content, o["topic"]!!.jsonPrimitive.content,
                o["tags"]!!.jsonArray.map { it.jsonPrimitive.content }, o["url"]!!.jsonPrimitive.content.ifEmpty { null },
                o["content"]!!.jsonPrimitive.content, o["version"]!!.jsonPrimitive.int, o["createdAt"]!!.jsonPrimitive.long,
                o["supersedes"]!!.jsonPrimitive.content.ifEmpty { null })
        } }
    }
}
