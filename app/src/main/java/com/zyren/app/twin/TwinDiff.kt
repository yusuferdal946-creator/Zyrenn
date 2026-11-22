package com.zyren.app.twin

import kotlinx.serialization.json.*

object TwinDiff {
    /** İki ikiz anlık görüntüsü arasındaki farkları okunur satırlar olarak döner. */
    fun diff(old: JsonObject, new: JsonObject): List<String> {
        val out = mutableListOf<String>()
        for (k in listOf("pil", "sarj", "ramBosMb", "depoGb", "sesYuzde", "parlaklik", "rahatsizEtme", "bildirimErisimi", "erisilebilirlik")) {
            val a = old[k]?.jsonPrimitive?.contentOrNull; val b = new[k]?.jsonPrimitive?.contentOrNull
            if (a != b) out += "$k: $a → $b"
        }
        val pa = old["izinler"] as? JsonObject; val pb = new["izinler"] as? JsonObject
        if (pa != null && pb != null) for ((k, v) in pb) { val a = pa[k]?.jsonPrimitive?.contentOrNull; if (a != v.jsonPrimitive.contentOrNull) out += "izin $k: $a → ${v.jsonPrimitive.contentOrNull}" }
        val aa = (old["uygulamalar"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        val ab = (new["uygulamalar"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        (ab - aa).sorted().forEach { out += "YENİ uygulama: $it" }
        (aa - ab).sorted().forEach { out += "KALDIRILAN uygulama: $it" }
        return out
    }
}
