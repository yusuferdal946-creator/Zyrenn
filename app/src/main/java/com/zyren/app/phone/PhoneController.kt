package com.zyren.app.phone

import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import com.zyren.app.core.PermissionBridge
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Android'in resmî API'leri: Intent, ContentResolver, SmsManager, AudioManager, AccessibilityService, NotificationListener.
 * Android'in izin vermediği işlerde (ör. Wi-Fi/Bluetooth'u doğrudan açıp kapatma) ilgili sistem ekranını açar.
 */
class PhoneController(private val ctx: Context) {
    private val tr = Locale("tr", "TR")

    private fun launch(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }
    private fun fmt(ms: Long) = SimpleDateFormat("dd.MM HH:mm", tr).format(Date(ms))

    fun battery(): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Pil bilgisi alınamadı"
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        return "Pil %${level * 100 / scale}, " + (if (charging) "şarj oluyor" else "şarjda değil") + ", $temp°C"
    }

    fun storage(): String {
        val sf = StatFs(Environment.getDataDirectory().path)
        val gb = 1073741824.0
        return "Depolama: ${"%.1f".format(sf.availableBytes / gb)} GB boş / ${"%.1f".format(sf.totalBytes / gb)} GB toplam"
    }

    fun openApp(name: String): String {
        val pm = ctx.packageManager
        val n = name.lowercase(tr).trim()
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(q, 0)
            .map { it to it.loadLabel(pm).toString() }
            .filter { (_, l) -> val ll = l.lowercase(tr); ll == n || ll.contains(n) || (ll.length >= 3 && n.startsWith(ll)) }
            .minByOrNull { (_, l) -> if (l.lowercase(tr) == n) 0 else 1 }
            ?: return "HATA: '$name' adlı uygulama bulunamadı"
        val intent = pm.getLaunchIntentForPackage(match.first.activityInfo.packageName) ?: return "HATA: ${match.second} açılamadı"
        launch(intent)
        return "${match.second} açıldı"
    }

    fun openSettings(which: String): String {
        val action = when (which) {
            "wifi" -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            "internet" -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "pil_tasarrufu" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "ses" -> Settings.ACTION_SOUND_SETTINGS
            "ekran" -> Settings.ACTION_DISPLAY_SETTINGS
            "depolama" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "erisilebilirlik" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "bildirim_erisimi" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val i = Intent(action)
        if (which == "uygulama_ayarlari") {
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        } else launch(i)
        return "'$which' ayar ekranı açıldı (Android 10+ bazı ayarların doğrudan açılıp kapatılmasına izin vermez; anahtarı siz çevirin)"
    }

    fun volume(action: String, percent: Int?): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = maxOf(1, max / 8)
        val target = when (action) {
            "yukselt" -> minOf(max, cur + step)
            "azalt" -> maxOf(0, cur - step)
            "sessiz" -> 0
            else -> ((percent ?: return "HATA: 'yuzde' gerekli") * max / 100).coerceIn(0, max)
        }
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return "Medya sesi: ${target * 100 / max}%"
    }

    fun brightness(percent: Int?, delta: Int?): String {
        if (!Settings.System.canWrite(ctx)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            return "Parlaklık için 'Sistem ayarlarını değiştirme' izni gerekli; açılan ekrandan Zyren'e izin verip komutu tekrarlayın."
        }
        val cr = ctx.contentResolver
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
        val target = (percent ?: (cur + (delta ?: return "HATA: 'yuzde' veya 'degisim' gerekli"))).coerceIn(1, 100)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target * 255 / 100)
        return "Parlaklık: %$target"
    }

    fun dnd(on: Boolean): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return "Rahatsız Etmeyin için 'Rahatsız Etmeyin erişimi' izni gerekli; açılan ekrandan Zyren'e izin verip komutu tekrarlayın."
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
        return "Rahatsız Etmeyin " + if (on) "açıldı" else "kapatıldı"
    }

    fun alarm(hour: Int, minute: Int, message: String): String {
        launch(Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "Alarm isteği saat uygulamasına iletildi: %02d:%02d (kurulduğunu saat uygulamasından doğrulayın; en yakın o saatte çalar)".format(hour, minute)
    }

    fun timer(seconds: Int, message: String): String {
        launch(Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_MESSAGE, message)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "Zamanlayıcı isteği iletildi: $seconds sn"
    }

    fun calendarInsert(title: String, begin: String, end: String?, location: String?): String {
        val z = ZoneId.systemDefault()
        val b = LocalDateTime.parse(begin).atZone(z).toInstant().toEpochMilli()
        val e = if (end.isNullOrBlank()) b + 3_600_000 else LocalDateTime.parse(end).atZone(z).toInstant().toEpochMilli()
        launch(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, b).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location ?: ""))
        return "Takvim etkinliği taslağı açıldı; Kaydet'e siz basın."
    }

    suspend fun calendarRead(days: Int): String {
        if (!PermissionBridge.ensure(ctx, android.Manifest.permission.READ_CALENDAR)) return "HATA: takvim izni verilmedi"
        val start = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, start); ContentUris.appendId(b, start + days * 86_400_000L)
        val lines = mutableListOf<String>()
        ctx.contentResolver.query(b.build(),
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
            null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext() && lines.size < 20) lines += "${fmt(c.getLong(1))} · ${c.getString(0)}"
        }
        return if (lines.isEmpty()) "Önümüzdeki $days gün için etkinlik yok" else lines.joinToString("\n")
    }

    suspend fun findContact(name: String): String {
        if (!PermissionBridge.ensure(ctx, android.Manifest.permission.READ_CONTACTS)) return "HATA: kişiler izni verilmedi"
        val out = linkedMapOf<String, String>()
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        ctx.contentResolver.query(p,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { c ->
            while (c.moveToNext() && out.size < 8) {
                val num = c.getString(1).orEmpty()
                out.putIfAbsent(num.filter { it.isDigit() || it == '+' }, "${c.getString(0)}: $num")
            }
        }
        return if (out.isEmpty()) "Eşleşen kişi yok: $name" else out.values.joinToString("\n")
    }

    suspend fun sendSms(number: String, text: String): String {
        if (!PermissionBridge.ensure(ctx, android.Manifest.permission.SEND_SMS)) {
            launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", text))
            return "SMS izni yok (Android kısıtı olabilir); mesaj SMS uygulamasında hazırlandı, Gönder'e siz basın."
        }
        val sm = smsManager()
        sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
        return "SMS gönderim isteği iletildi: $number (teslimat doğrulanmadı)"
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    fun whatsapp(number: String, text: String): String {
        var d = number.filter { it.isDigit() }
        if (d.startsWith("0") && d.length == 11) d = "90" + d.drop(1)
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d?text=" + Uri.encode(text))))
        return "WhatsApp'ta sohbet hazır mesajla açıldı; Gönder tuşuna siz basın (resmî yöntem, otomatik gönderim yok)."
    }

    suspend fun call(number: String): String {
        return if (PermissionBridge.ensure(ctx, android.Manifest.permission.CALL_PHONE)) {
            launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))); "Aranıyor: $number"
        } else {
            launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
            "Arama izni yok; numara çevirici ekranında açıldı, arama tuşuna siz basın."
        }
    }

    suspend fun callLog(n: Int): String {
        if (!PermissionBridge.ensure(ctx, android.Manifest.permission.READ_CALL_LOG)) return "HATA: arama geçmişi izni verilmedi (Android kısıtlı izin olabilir)"
        val lines = mutableListOf<String>()
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
            while (c.moveToNext() && lines.size < n) {
                val type = when (c.getInt(2)) { CallLog.Calls.INCOMING_TYPE -> "gelen"; CallLog.Calls.OUTGOING_TYPE -> "giden"; CallLog.Calls.MISSED_TYPE -> "cevapsız"; else -> "diğer" }
                lines += "${fmt(c.getLong(3))} · $type · ${c.getString(1) ?: c.getString(0)} · ${c.getInt(4)} sn"
            }
        }
        return if (lines.isEmpty()) "Arama geçmişi boş" else lines.joinToString("\n")
    }

    fun youtube(query: String): String {
        return try {
            launch(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query))
            "YouTube'da aranıyor: $query"
        } catch (e: Exception) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
            "YouTube uygulaması yok/açılamadı; tarayıcıda arama açıldı: $query"
        }
    }

    private fun listenerEnabled() =
        Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true

    fun notifications(n: Int): String {
        if (!listenerEnabled()) {
            launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return "Bildirim erişimi kapalı. Açılan ekrandan Zyren'e izin verin (Android 13+ sideload için önce Uygulama bilgisi > ⋮ > 'Kısıtlı ayarlara izin ver')."
        }
        val list = NotificationStore.recent(n)
        if (list.isEmpty()) return "Zyren açıldığından beri yakalanmış bildirim yok"
        return "<bildirimler>\n" + list.joinToString("\n") { "${fmt(it.time)} · ${it.pkg} · ${it.title}: ${it.text}" } + "\n</bildirimler>"
    }

    private fun a11y(): ZyrenAccessibilityService? {
        val s = ZyrenAccessibilityService.instance
        if (s == null) launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return s
    }
    private val a11yMsg = "Erişilebilirlik servisi kapalı. Açılan ekranda Zyren'i etkinleştirin (Android 13+ sideload için önce Uygulama bilgisi > ⋮ > 'Kısıtlı ayarlara izin ver')."

    fun screenText(): String = a11y()?.screenText()?.let { "<ekran>\n$it\n</ekran>" } ?: a11yMsg
    fun clickText(t: String): String = a11y()?.let { if (it.clickText(t)) "Dokunuldu: $t" else "HATA: ekranda '$t' bulunamadı/dokunulamadı" } ?: a11yMsg
    fun typeText(t: String): String = a11y()?.let { if (it.typeText(t)) "Yazıldı" else "HATA: odaklı metin alanı yok" } ?: a11yMsg
    fun global(action: String): String = a11y()?.let { if (it.global(action)) "Yapıldı: $action" else "HATA: '$action' desteklenmiyor" } ?: a11yMsg
}
