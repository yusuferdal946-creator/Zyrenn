package com.zyren.app.core.memory

import kotlinx.serialization.json.*
import java.io.File

/** Kalıcı hafıza: görüntüle / düzenle / sil. */
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

    suspend fun summary(): String {
        val l = dao.list()
        return "${l.size} kayıt · sabitli ${l.count { it.pinned }} · kategoriler: " +
            l.groupingBy { it.category }.eachCount().entries.joinToString { "${it.key}×${it.value}" }
    }

    companion object {
        const val MAX = 200
        const val ERR = "Hata/Çözüm"
        val CATEGORIES = listOf("Tercih", "Proje", "Karar", ERR, "Görev")
    }

    val all = dao.observeAll()

    /** Yeni hafıza kaydı ekler. Boş ve tekrar eden kayıtları engeller. */
    suspend fun add(category: String, content: String): Boolean {
        val cleanContent = content.trim()
        val cleanCategory = category.trim().ifBlank { "Tercih" }

        if (cleanContent.isBlank()) return false
        if (dao.findByContent(cleanContent) != null) return false

        if (dao.count() >= MAX) {
            dao.deleteOldestUnpinned(1)
            if (dao.count() >= MAX) return false
        }

        dao.insert(
            MemoryEntity(
                category = cleanCategory,
                content = cleanContent
            )
        )
        return true
    }

    suspend fun edit(m: MemoryEntity, category: String, content: String) =
        dao.update(
            m.copy(
                category = category,
                content = content.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )

    suspend fun togglePin(m: MemoryEntity) =
        dao.update(m.copy(pinned = !m.pinned))

    suspend fun delete(m: MemoryEntity) = dao.delete(m)

    suspend fun clear() = dao.clear()

    /** Modele verilecek kısa hafıza özeti. */
    suspend fun promptContext(limit: Int = 30): String {
        val main = dao.top(limit)

        if (main.isNotEmpty()) {
            dao.touch(main.map { it.id }, System.currentTimeMillis())
        }

        val errs = dao.byCategory(ERR, 8).filter { e ->
            main.none { it.id == e.id }
        }

        return (main + errs).joinToString("\n") {
            "- [${it.category}] ${it.content}"
        }
    }

    /** Hafızayı JSON dosyasına yedekler. */
    suspend fun snapshotTo(file: File) {
        val items = dao.list()

        val json = buildJsonArray {
            items.forEach { m ->
                add(
                    buildJsonObject {
                        put("category", m.category)
                        put("content", m.content)
                        put("pinned", m.pinned)
                        put("createdAt", m.createdAt)
                        put("updatedAt", m.updatedAt)
                        put("importance", m.importance)
                        put("useCount", m.useCount)
                        put("lastUsedAt", m.lastUsedAt)
                    }
                )
            }
        }

        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }

    /** JSON yedeğindeki hafızayı mevcut kayıtlarla birleştirir. */
    suspend fun mergeFrom(file: File) {
        if (!file.isFile) return

        val entries = try {
            Json.parseToJsonElement(file.readText()).jsonArray
        } catch (_: Exception) {
            return
        }

        for (entry in entries) {
            try {
                val obj = entry.jsonObject
                val content = obj["content"]?.jsonPrimitive?.content ?: continue
                val category = obj["category"]?.jsonPrimitive?.content ?: "Tercih"
                val cleanContent = content.trim()

                if (cleanContent.isBlank()) continue
                if (dao.findByContent(cleanContent) != null) continue

                if (dao.count() >= MAX) {
                    dao.deleteOldestUnpinned(1)
                    if (dao.count() >= MAX) break
                }

                dao.insert(
                    MemoryEntity(
                        category = category,
                        content = cleanContent,
                        pinned = obj["pinned"]?.jsonPrimitive?.booleanOrNull ?: false,
                        createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull
                            ?: System.currentTimeMillis(),
                        updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull
                            ?: System.currentTimeMillis(),
                        importance = obj["importance"]?.jsonPrimitive?.intOrNull ?: 5,
                        useCount = obj["useCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        lastUsedAt = obj["lastUsedAt"]?.jsonPrimitive?.longOrNull
                            ?: System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
                continue
            }
        }
    }

    /** Proje hafızası. */
    data class ProjeBilgileri(
        val projeId: String,
        val ad: String,
        val sonAktivite: Long,
        val değişiklikListesi: List<String>
    )

    /** Projeyi JSON olarak kaydet. */
    suspend fun projeKaydet(
        projeId: String,
        ad: String,
        değişiklikler: List<String>
    ): Boolean {
        return try {
            val dosya = File("data/zyren/${projeId}.json")
            dosya.parentFile?.mkdirs()

            val veri = buildJsonObject {
                put("projeId", projeId)
                put("ad", ad)
                put("sonAktivite", System.currentTimeMillis())
                put(
                    "değişiklikListesi",
                    JsonArray(değişiklikler.map { JsonPrimitive(it) })
                )
            }

            dosya.writeText(veri.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Kaydedilmiş projeyi yükle. */
    suspend fun projeYukle(projeId: String): ProjeBilgileri? {
        return try {
            val dosya = File("data/zyren/${projeId}.json")
            if (!dosya.isFile) return null

            val o = Json.parseToJsonElement(dosya.readText()).jsonObject

            ProjeBilgileri(
                projeId = o["projeId"]?.jsonPrimitive?.content ?: projeId,
                ad = o["ad"]?.jsonPrimitive?.content ?: projeId,
                sonAktivite = o["sonAktivite"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: System.currentTimeMillis(),
                değişiklikListesi = o["değişiklikListesi"]?.jsonArray
                    ?.map { it.jsonPrimitive.content } ?: emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Proje aktivite tarihini güncelle. */
    suspend fun projeAktiviteGüncelle(projeId: String) {
        projeYukle(projeId)?.let {
            it.copy(sonAktivite = System.currentTimeMillis())
        }
    }
}
