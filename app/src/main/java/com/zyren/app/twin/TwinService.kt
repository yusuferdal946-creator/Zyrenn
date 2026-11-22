package com.zyren.app.twin

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.zyren.app.core.PermissionBridge
import com.zyren.app.core.res.ResourceMonitor
import com.zyren.app.core.tools.Args
import com.zyren.app.phone.ZyrenAccessibilityService
import com.zyren.app.store.JsonStore
import kotlinx.serialization.json.*
import java.util.Locale

/**
 * Telefonun dijital ikizi: cihaz durumunun (uygulama adları, ayarlar, kaynaklar, Zyren izinleri) yerel anlık görüntüsü.
 * Kişisel içerik (mesaj, kişi, bildirim metni) KOPYALANMAZ; yalnızca durum ve uygulama adları. Cihazdan dışarı gönderilmez.
 */
class TwinService(private val ctx: Context, private val resources: ResourceMonitor, private val store: JsonStore) {
    private val tr = Locale("tr", "TR")

    fun snapshot(now: Long): JsonObject {
        val r = resources.snapshot()
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val bright = runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 }.getOrDefault(-1)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() }.distinct().sorted().take(400)
        val perms = listOf("MIKROFON" to Manifest.permission.RECORD_AUDIO, "KISILER" to Manifest.permission.READ_CONTACTS, "SMS" to Manifest.permission.SEND_SMS,
            "ARAMA" to Manifest.permission.CALL_PHONE, "ARAMA_GECMISI" to Manifest.permission.READ_CALL_LOG, "TAKVIM" to Manifest.permission.READ_CALENDAR)
        return buildJsonObject {
            put("zaman", now); put("pil", r.batteryPct); put("sarj", r.charging); put("ramBosMb", (r.ramAvailMb / 100) * 100)
            put("depoGb", "%.1f".format(r.storageFreeGb)); put("sesYuzde", am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max)
            put("parlaklik", bright); put("rahatsizEtme", nm.currentInterruptionFilter)
            put("bildirimErisimi", Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true)
            put("erisilebilirlik", ZyrenAccessibilityService.instance != null)
            put("izinler", buildJsonObject { perms.forEach { (k, v) -> put(k, PermissionBridge.has(ctx, v)) } })
            put("uygulamalar", buildJsonArray { apps.forEach { add(it) } })
        }
    }

    fun history(): List<JsonObject> = store.read().map { it.jsonObject }
    fun save(s: JsonObject) { store.write(JsonArray((history() + s).takeLast(10))) }
    fun last(): JsonObject? = history().lastOrNull()

    fun diffLast(): String {
        val h = history()
        if (h.size < 2) return "Karşılaştırma için en az 2 anlık görüntü gerekir (İkiz > Güncelle'yi iki kez çalıştırın)."
        val d = TwinDiff.diff(h[h.size - 2], h.last())
        return if (d.isEmpty()) "Son iki anlık görüntü arasında fark yok." else d.joinToString("\n")
    }

    fun summary(): String {
        val s = last() ?: return "Henüz anlık görüntü yok."
        val g = { k: String -> s[k]?.jsonPrimitive?.contentOrNull }
        return "Pil %${g("pil")} · RAM boş ~${g("ramBosMb")} MB · Depo ${g("depoGb")} GB · Ses %${g("sesYuzde")} · Parlaklık %${g("parlaklik")} · " +
            "${(s["uygulamalar"] as? JsonArray)?.size ?: 0} uygulama · bildirim erişimi ${g("bildirimErisimi")} · erişilebilirlik ${g("erisilebilirlik")}\nizinler: ${s["izinler"]}"
    }

    /** Simülasyon için ikiz tahmini (yalnızca son anlık görüntüye dayanır). */
    fun hint(tool: String, args: Args): String? {
        val s = last() ?: return "ikiz anlık görüntüsü yok (İkiz > Güncelle)"
        val perms = s["izinler"] as? JsonObject
        fun p(k: String) = perms?.get(k)?.jsonPrimitive?.booleanOrNull == true
        return when (tool) {
            "uygulama_ac" -> {
                val n = args.str("ad")?.lowercase(tr).orEmpty()
                val has = (s["uygulamalar"] as? JsonArray)?.any { it.jsonPrimitive.content.lowercase(tr).contains(n) } == true
                if (has) "'$n' yüklü görünüyor → açılırdı" else "'$n' yüklü görünmüyor → HATA verirdi"
            }
            "sms_gonder" -> if (p("SMS")) "SMS izni verilmiş" else "SMS izni yok → Android izin penceresi çıkardı / reddedilirse SMS uygulamasında hazır mesaj açılırdı"
            "kisi_bul" -> if (p("KISILER")) "kişiler izni var" else "kişiler izni yok → izin istenirdi"
            "ara" -> if (p("ARAMA")) "arama izni var" else "arama izni yok → numara çevirici açılırdı"
            "arama_gecmisi" -> if (p("ARAMA_GECMISI")) "arama geçmişi izni var" else "arama geçmişi izni yok"
            "bildirimleri_oku" -> if (s["bildirimErisimi"]?.jsonPrimitive?.booleanOrNull == true) "bildirim erişimi açık" else "bildirim erişimi kapalı → ayar ekranı açılırdı"
            "ekran_oku", "ekrana_dokun", "ekrana_yaz", "sistem_tusu" -> if (s["erisilebilirlik"]?.jsonPrimitive?.booleanOrNull == true) "erişilebilirlik servisi açık" else "erişilebilirlik kapalı → HATA/ayar ekranı"
            else -> null
        }
    }
}
