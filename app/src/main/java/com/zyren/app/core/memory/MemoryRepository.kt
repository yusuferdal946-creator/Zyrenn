package com.zyren.app.core.memory

import kotlinx.serialization.json.*
import java.io.File

/** Kalıcı hafıza: görüntüle / düzenle / sil. Sınırsız büyümez (MAX), sabitlenenler korunur. */
class MemoryRepository(private val dao: MemoryDao) {
    suspend fun maintain(now: Long): String {
        val items = dao.list()
        val plan = MemoryMaintenance.plan(items, now)
        if (plan.deleteIds.isNotEmpty()) dao.deleteIds(plan.deleteIds)
        plan.updates.forEach { dao.update(it) }
        return "Bakım: ${items.size} kayıt tarandı, ${plan.deleteIds.size} silindi (tekrar/eski-önemsiz), ${plan.updates.size} önemi azaltıldı." +
            (if (plan.conflicts.isEmpty()) "" else "\n" + plan.conflicts.joinToString("\n"))
    }
    suspend fun all(): List<MemoryEntity> = dao.list()
    suspend fun summary(): String { val l = dao.list(); return "${l.size} kayıt · sabitli ${l.count { it.pinned }} · kategoriler: " + l.groupingBy { it.category }.eachCount().entries.joinToString { "${it.key}×${it.value}" } }

    companion object {
        const val MAX = 200
        const val ERR = "Hata/Çözüm"
        val CATEGORIES = listOf("Tercih", "Proje", "Karar", ERR, "Görev")
    }

    val all = dao.observeAll()

    suspend fun add(category: String, content: String): Boolean {
        val c = content.trim()
        if (c.isEmpty() || dao.findByContent(c) != null) return false
        dao.insert(MemoryEntity(category = category, content = c))
        val over = dao.count() - MAX
        if (over > 0) dao.deleteOldestUnpinned(over)
        return true
    }

    suspend fun edit(m: MemoryEntity, category: String, content: String) =
        dao.update(m.copy(category = category, content = content.trim(), updatedAt = System.currentTimeMillis()))

    suspend fun togglePin(m: MemoryEntity) = dao.update(m.copy(pinned = !m.pinned))
    suspend fun delete(m: MemoryEntity) = dao.delete(m)
    suspend fun clear() = dao.clear()

    /** Modele verilecek kısa hafıza özeti (son hata/çözümler ayrıca eklenir). */
    suspend fun promptContext(limit: Int = 30): String {
        val main = dao.top(limit)
        if (main.isNotEmpty()) dao.touch(main.map { it.id }, System.currentTimeMillis())
        val errs = dao.byCategory(ERR, 8).filter { e -> main.none { it.id == e.id } }
        return (main + errs).joinToString("\n") { "- [${it.category}] ${it.content}" }
    }

    /** Proje hafırası: her proje için ayrı JSON kaydı (.zyren/projeId.json). */
    data class ProjeBilgileri(val projeId: String, val ad: String, val sonAktivite: Long, val değişiklikListesi: List<String>)
    /** Projeyi kaydet (JSON olarak .zyren klasörüne). */
    suspend fun projeKaydet(projeId: String, ad: String, değişiklikler: List<String>): Boolean {
        try {
            val dosya = File("data/zyren/${projeId}.json")
            dosya.parentFile?.mkdirs()
            dosya.writeText(buildJsonArray {
                add(buildJsonObject {
                    put("projeId", projeId)
                    put("ad", ad)
                    put("sonAktivite", System.currentTimeMillis())
                    put("değişiklikListesi", değişiklikler)
                })
            })
            return true
        } catch (e: Exception) {
            return false
        }
    }
    /** Kaydedilmiş projeyi yükle. */
    suspend fun projeYukle(projeId: String): ProjeBilgileri? {
        val dosya = File("data/zyren/${projeId}.json")
        if (!dosya.isFile) return null
        Json.parseToJsonElement(dosya.readText()).jsonArray.forEach { obj ->
            val o = obj.jsonObject
            return ProjeBilgileri(
                projeId = o["projeId"]?.jsonPrimitive?.content ?: projeId,
                ad = o["ad"]?.jsonPrimitive?.content ?: ad,
                sonAktivite = o["sonAktivite"]?.jsonPrimitive?.content?.toLong() ?: System.currentTimeMillis(),
                değişiklikListesi = o["değişiklikListesi"]?.jsonArray?.map { it.jsonPrimitive?.content ?: "" } ?: emptyList()
            )
        }
        return null
    }
    /** Proje aktivite tarihini güncelle. */
    suspend fun projeAktiviteGüncelle(projeId: String) {
        projeYukle(projeId)?.let { it.copy(sonAktivite = System.currentTimeMillis()) }
    }

    // ---- Cihazlar arası taşıma: çalışma alanındaki .zyren/memory.json ----
    suspend fun snapshotTo(file: File) {
        val arr = buildJsonArray {
            dao.top(1000).forEach { m ->
                add(buildJsonObject { put("category", m.category); put("content", m.content); put("pinned", m.pinned) })
            }
        }
        file.parentFile?.mkdirs()
        file.writeText(arr.toString())
    }

    suspend fun mergeFrom(file: File): Int {
        if (!file.isFile) return 0
        var n = 0
        Json.parseToJsonElement(file.readText()).jsonArray.forEach {
            val o = it.jsonObject
            if (add(o["category"]!!.jsonPrimitive.content, o["content"]!!.jsonPrimitive.content)) n++
        }
        return n
    }
}
