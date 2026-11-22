package com.zyren.app.core.memory

import java.util.Locale

/** Hafıza bakımı: tekrarları birleştir, kullanılmayanın önemini azalt, önemsizleşeni sil, olası çelişkileri bildir. Sabitlenenlere ve hata/çözümlere dokunmaz. */
object MemoryMaintenance {
    class Plan(val deleteIds: List<Long>, val updates: List<MemoryEntity>, val conflicts: List<String>)
    private val tr = Locale("tr", "TR")
    const val DAY = 86_400_000L

    fun norm(s: String) = s.lowercase(tr).replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()

    fun plan(items: List<MemoryEntity>, now: Long, staleDays: Int = 30): Plan {
        val del = mutableSetOf<Long>()
        val upd = mutableMapOf<Long, MemoryEntity>()
        items.groupBy { norm(it.content) }.values.filter { it.size > 1 }.forEach { g ->
            val keep = g.sortedWith(compareByDescending<MemoryEntity> { it.pinned }.thenByDescending { it.importance }.thenByDescending { it.updatedAt }).first()
            g.filter { it.id != keep.id && !it.pinned }.forEach { del += it.id }
        }
        items.filter { it.id !in del && !it.pinned && it.category != "Hata/Çözüm" }.forEach { m ->
            val last = maxOf(m.lastUsedAt, m.updatedAt)
            if (now - last > staleDays * DAY) {
                if (m.importance <= 1) del += m.id else upd[m.id] = m.copy(importance = m.importance - 1, lastUsedAt = now)
            }
        }
        val conflicts = items.filter { it.id !in del }.groupBy { it.category + "|" + norm(it.content).split(' ').take(2).joinToString(" ") }
            .values.filter { g -> g.size > 1 && g.map { norm(it.content) }.toSet().size > 1 }
            .map { g -> "Olası çelişki: " + g.joinToString("  ≠  ") { "\"${it.content.take(60)}\"" } }
        return Plan(del.toList(), upd.values.toList(), conflicts)
    }
}
