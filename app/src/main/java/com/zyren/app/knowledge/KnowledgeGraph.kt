package com.zyren.app.knowledge

import java.util.Locale

/**
 * Kişisel bilgi grafiği (saf mantık): hafıza ve bilgi merkezi kayıtlarını düğüm yapar, ortak anahtar kelimelere göre kenar kurar.
 * Anlamsal değil sözcüksel benzerliktir; bu yüzden yalnızca "ilişkili olabilir" önerisi verir. Kaynak ve tarih düğümde saklanır.
 */
class GNode(val id: String, val kind: String, val label: String, val text: String, val source: String)
class GEdge(val a: String, val b: String, val shared: List<String>)

object KnowledgeGraph {
    private val tr = Locale("tr", "TR")
    private val stop = setOf("için", "gibi", "daha", "olan", "ama", "veya", "ile", "çok", "bir", "bunu", "şunu", "kadar", "sonra", "önce", "ben", "sen", "var", "yok", "değil", "olarak")

    fun tokens(s: String): Set<String> = s.lowercase(tr).split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 4 && it !in stop }.map { if (it.length > 6) it.take(6) else it }.toSet() // kaba kök: ek farklarını azaltır

    fun edges(nodes: List<GNode>, minShared: Int = 2, minJaccard: Double = 0.2): List<GEdge> {
        val tk = nodes.associate { it.id to tokens(it.label + " " + it.text) }
        val out = mutableListOf<GEdge>()
        for (i in nodes.indices) for (j in i + 1 until nodes.size) {
            val a = tk.getValue(nodes[i].id); val b = tk.getValue(nodes[j].id)
            val inter = a.intersect(b)
            if (inter.size >= minShared && inter.size.toDouble() / a.union(b).size >= minJaccard)
                out += GEdge(nodes[i].id, nodes[j].id, inter.sorted().take(5))
        }
        return out
    }

    /** Sorguyla eşleşen düğümler ve komşuları. Sorgu boşsa en bağlantılı düğümler. */
    fun render(nodes: List<GNode>, query: String, limit: Int = 8): String {
        if (nodes.isEmpty()) return "Grafik boş (hafıza/bilgi kaydı yok)."
        val es = edges(nodes)
        val byId = nodes.associateBy { it.id }
        val q = tokens(query)
        val hits = if (q.isEmpty()) nodes.sortedByDescending { n -> es.count { it.a == n.id || it.b == n.id } }.take(limit)
        else nodes.filter { n -> tokens(n.label + " " + n.text).intersect(q).isNotEmpty() }.take(limit)
        if (hits.isEmpty()) return "Bu sorguyla eşleşen düğüm yok."
        return hits.joinToString("\n\n") { n ->
            val rel = es.filter { it.a == n.id || it.b == n.id }.take(5).joinToString("\n") { e ->
                val o = byId.getValue(if (e.a == n.id) e.b else e.a)
                "   ↔ [${o.kind}] ${o.label} (ortak: ${e.shared.joinToString()}) · kaynak: ${o.source}"
            }
            "• [${n.kind}] ${n.label} · kaynak: ${n.source}" + (if (rel.isNotBlank()) "\n$rel" else "\n   (bağlantı yok)")
        } + "\n\nNot: ilişkiler ortak sözcüklere dayanır; kesin bağlantı değil, öneridir."
    }
}
