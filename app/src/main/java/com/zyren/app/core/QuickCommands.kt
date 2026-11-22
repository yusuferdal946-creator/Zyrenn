package com.zyren.app.core

import com.zyren.app.core.tools.Args
import com.zyren.app.core.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Basit, kesin kalıplı Türkçe komutlar modele gitmeden (internetsiz, hızlı, pil dostu) doğrudan araca bağlanır.
 * Eşleşmezse null döner ve komut modele gider. İzinler yine ToolRegistry/PermissionManager'dan geçer.
 */
class QuickCommands(private val tools: ToolRegistry) {
    private val tr = Locale("tr", "TR")

    private fun a(vararg p: Pair<String, String>) = Args(JsonObject(p.associate { it.first to JsonPrimitive(it.second) }))

    suspend fun tryHandle(raw: String): String? {
        var t = raw.lowercase(tr).trim().trimEnd('.', '!', '?')
        t = t.removePrefix("zyren").trimStart(',', ' ').trim()
        if (t.isEmpty() || t.length > 60) return null

        if (Regex("(pil|şarj).*(kaç|durum|yüzde|ne kadar)|(kaç|ne kadar).*(pil|şarj)").containsMatchIn(t))
            return tools.execute("pil_durumu", a())
        if (Regex("depolama.*(kontrol|durum|kaç|ne kadar)").containsMatchIn(t)) return tools.execute("depolama_durumu", a())
        if (Regex("(wi-?fi|kablosuz).*(aç|kapat)").containsMatchIn(t)) return tools.execute("ayar_ekrani_ac", a("ayar" to "wifi"))
        if (Regex("bluetooth.*(aç|kapat)").containsMatchIn(t)) return tools.execute("ayar_ekrani_ac", a("ayar" to "bluetooth"))
        if (Regex("pil tasarrufu").containsMatchIn(t)) return tools.execute("ayar_ekrani_ac", a("ayar" to "pil_tasarrufu"))
        if (Regex("rahatsız etme").containsMatchIn(t))
            return tools.execute("rahatsiz_etme", a("durum" to if (t.contains("kapat")) "kapat" else "ac"))
        Regex("ses(i|)\\s*(yükselt|arttır|artır|azalt|kıs)").find(t)?.let {
            val up = it.groupValues[2].let { w -> w == "yükselt" || w == "arttır" || w == "artır" }
            return tools.execute("ses_ayarla", a("islem" to if (up) "yukselt" else "azalt"))
        }
        Regex("parlaklığ(ı|ı)\\s*(azalt|kıs|arttır|artır|yükselt)").find(t)?.let {
            val up = it.groupValues[2] != "azalt" && it.groupValues[2] != "kıs"
            return tools.execute("parlaklik_ayarla", a("degisim" to if (up) "20" else "-20"))
        }
        if (t.contains("alarm") || t.contains("hatırlat")) {
            val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(t) ?: return null
            var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            if ((t.contains("akşam") || t.contains("öğleden sonra")) && h < 12) h += 12
            if (h > 23 || min > 59) return null
            return tools.execute("alarm_kur", a("saat" to h.toString(), "dakika" to min.toString(), "mesaj" to "Zyren hatırlatıcı"))
        }
        Regex("youtube['’]?(?:da|de|ta|te)\\s+(.+?)\\s*(?:ara|izle|bul|aç)$").find(t)?.let {
            return tools.execute("youtube_ara", a("sorgu" to it.groupValues[1]))
        }
        Regex("^(.+?)\\s+(?:aç|başlat)$").find(t)?.let {
            val name = it.groupValues[1].substringBefore('\'').substringBefore('’').trim()
            if (name.isNotEmpty() && name.split(' ').size <= 3) {
                val r = tools.execute("uygulama_ac", a("ad" to name))
                // Uygulama değilse (ör. "dosyayı aç") komutu modele bırak
                return if (r.startsWith("HATA: '")) null else r
            }
        }
        return null
    }
}
