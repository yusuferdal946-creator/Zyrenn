package com.zyren.app.quality

import com.zyren.app.store.JsonStore
import kotlinx.serialization.json.*

enum class FeatureStatus(val label: String) {
    PLANLANDI("Planlandı"), KOD_YAZILDI("Kod yazıldı"), DERLENDI("Derlendi"), CIHAZDA_TEST_EDILDI("Cihazda test edildi"), TAMAM("Tamam")
}

class Feature(val id: String, val name: String, var status: FeatureStatus, var lastResult: String, var lastTestAt: Long, var note: String)

/**
 * "Test edilmemiş özellik tamamlanmış gösterilmez" kuralını KODLA zorlar:
 * - Otomatik test sonucu durumu YÜKSELTMEZ; başarısız test durumu KOD_YAZILDI'ya düşürür.
 * - CİHAZDA TEST EDİLDİ / TAMAM yalnızca kullanıcı onayıyla (arayüz) ve kanıt notuyla verilir; ajanlar bunu çağıramaz.
 */
class FeatureRegistry(private val store: JsonStore? = null) {
    val features = mutableListOf<Feature>()

    init { reload() }

    fun reload() {
        features.clear()
        store?.read()?.forEach { e -> runCatching { val o = e.jsonObject
            features += Feature(o["id"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content, FeatureStatus.valueOf(o["status"]!!.jsonPrimitive.content),
                o["result"]!!.jsonPrimitive.content, o["at"]!!.jsonPrimitive.long, o["note"]!!.jsonPrimitive.content) } }
    }

    fun ensure(id: String, name: String, status: FeatureStatus) { if (features.none { it.id == id }) { features += Feature(id, name, status, "", 0L, ""); save() } }
    fun get(id: String) = features.firstOrNull { it.id == id }

    fun recordAuto(id: String, passed: Boolean, detail: String, now: Long) {
        val f = get(id) ?: return
        f.lastResult = (if (passed) "GEÇTİ: " else "BAŞARISIZ: ") + detail.take(200); f.lastTestAt = now
        if (!passed && f.status.ordinal > FeatureStatus.KOD_YAZILDI.ordinal) f.status = FeatureStatus.KOD_YAZILDI
        save()
    }

    /** Yalnızca kullanıcı arayüzünden çağrılır. Hata mesajı veya null. */
    fun userSet(id: String, to: FeatureStatus, note: String, now: Long): String? {
        val f = get(id) ?: return "Özellik yok"
        if (to.ordinal > FeatureStatus.KOD_YAZILDI.ordinal && note.isBlank()) return "Bu durum için kanıt notu gerekir (ne denendi, sonuç)"
        if (to == FeatureStatus.TAMAM && f.status != FeatureStatus.CIHAZDA_TEST_EDILDI) return "TAMAM için önce 'Cihazda test edildi' olmalı"
        if (to == FeatureStatus.CIHAZDA_TEST_EDILDI && f.status.ordinal < FeatureStatus.DERLENDI.ordinal) return "Önce 'Derlendi' olmalı"
        f.status = to; f.note = note.take(300); f.lastTestAt = now; save(); return null
    }

    fun render(): String = features.joinToString("\n") { "${it.status.label.padEnd(20)} ${it.name}" + if (it.lastResult.isNotBlank()) "  ← ${it.lastResult}" else "" }

    private fun save() {
        store?.write(buildJsonArray { features.forEach { f -> add(buildJsonObject { put("id", f.id); put("name", f.name); put("status", f.status.name); put("result", f.lastResult); put("at", f.lastTestAt); put("note", f.note) }) } })
    }
}
