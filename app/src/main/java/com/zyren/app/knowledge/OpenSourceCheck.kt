package com.zyren.app.knowledge

import java.time.Duration
import java.time.Instant

/** Açık kaynak değerlendirmesi (saf mantık): lisans uyumu ve bakım güncelliği. Kararlar YALNIZCA öneridir; entegrasyon yine kullanıcı onayıyla yapılır. */
object OpenSourceCheck {
    private val permissive = setOf("MIT", "APACHE-2.0", "BSD-2-CLAUSE", "BSD-3-CLAUSE", "ISC", "UNLICENSE", "0BSD", "ZLIB", "CC0-1.0")
    private val weakCopyleft = setOf("MPL-2.0", "LGPL-2.1", "LGPL-3.0", "EPL-2.0")

    /** (etiket, uygun mu) — GPL/AGPL ve lisanssız projeler uygulamaya doğrudan eklenmemeli. */
    fun license(spdx: String?): Pair<String, Boolean> {
        val id = spdx?.trim()?.uppercase().orEmpty()
        return when {
            id.isEmpty() || id == "NOASSERTION" || id == "NULL" -> "LİSANS YOK/BELİRSİZ → kullanma (hakları saklıdır)" to false
            id in permissive -> "$id → serbest (kaynak belirterek kullanılabilir)" to true
            id in weakCopyleft -> "$id → zayıf copyleft (kütüphane olarak genelde uygun; değişiklikleri paylaşma yükümlülüğü olabilir)" to true
            id.contains("GPL") -> "$id → güçlü copyleft (uygulamanın tamamını etkileyebilir) → önerilmez" to false
            else -> "$id → tanınmayan lisans; elle incele" to false
        }
    }

    fun freshness(pushedIso: String?, nowMs: Long): String {
        val t = runCatching { Instant.parse(pushedIso) }.getOrNull() ?: return "son güncelleme bilinmiyor"
        val days = Duration.between(t, Instant.ofEpochMilli(nowMs)).toDays()
        return when {
            days < 0 -> "son güncelleme gelecekte görünüyor (saat farkı?)"
            days <= 90 -> "aktif (son push $days gün önce)"
            days <= 365 -> "yavaş (son push $days gün önce)"
            else -> "BAKIMSIZ olabilir (son push ${days / 365} yıldan eski)"
        }
    }

    /** Genel sonuç: uygun / dikkat / uygun değil. */
    fun verdict(licenseOk: Boolean, archived: Boolean, pushedIso: String?, nowMs: Long): String {
        val t = runCatching { Instant.parse(pushedIso) }.getOrNull()
        val stale = t == null || Duration.between(t, Instant.ofEpochMilli(nowMs)).toDays() > 730
        return when {
            !licenseOk -> "UYGUN DEĞİL (lisans)"
            archived -> "UYGUN DEĞİL (arşivlenmiş/bakımı bırakılmış)"
            stale -> "DİKKAT (uzun süredir güncellenmemiş)"
            else -> "UYGUN görünüyor (yine de güvenlik taraması ve testle doğrulayın)"
        }
    }
}
