package com.zyren.app.core.security

import android.content.Context
import com.zyren.app.core.memory.AuditDao
import com.zyren.app.core.memory.AuditEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Risk(val label: String) {
    SAFE("Güvenli"), REVERSIBLE("Geri alınabilir"), CRITICAL("Kritik · her seferinde onay")
}

enum class Capability(val label: String, val risk: Risk, val explain: String) {
    CHAT_CLOUD("Bulut modele mesaj gönderme", Risk.SAFE, "Yazdıklarınız seçtiğiniz bulut sağlayıcıya (Gemini/Groq/OpenRouter) gönderilir."),
    MEMORY_WRITE("Hafızaya kayıt yazma", Risk.SAFE, "Zyren sizin hakkınızdaki bilgileri hafızaya yazabilir (görür, düzenler, silersiniz)."),
    FILE_READ("Çalışma alanında dosya okuma/yedekleme", Risk.SAFE, "Yalnızca Zyren'in kendi çalışma klasörü."),
    FILE_WRITE("Yeni dosya/klasör oluşturma", Risk.REVERSIBLE, "Çalışma klasöründe yeni dosya açar; var olanın üzerine yazmaz."),
    FILE_OVERWRITE("Var olan dosyanın üzerine yazma", Risk.CRITICAL, "Eski sürüm saklanır; yine de her seferinde onay ister."),
    FILE_DELETE("Dosya silme (çöp kutusuna taşır)", Risk.CRITICAL, "Her seferinde onay ister."),
    BACKUP_RESTORE("Yedekten / eski sürümden geri yükleme", Risk.CRITICAL, "Çalışma alanını değiştirir; önce otomatik güvenlik yedeği alınır."),
    SHELL_SAFE("Güvenli terminal (yerleşik komutlar)", Risk.REVERSIBLE, "ls, cat, grep… yalnızca çalışma klasöründe; süreç başlatmaz."),
    SHELL_ANY("HAM KABUK (izole DEĞİL)", Risk.CRITICAL, "Android kabuğu; uygulamanın özel verilerine ve ağa erişebilir. Önerilmez; her seferinde onay."),
    CODE_RUN("JavaScript çalıştırma (kum havuzu)", Risk.REVERSIBLE, "Dosya/ağ/telefon erişimi olmayan, süre ve bellek sınırlı JS."),
    WEB("İnternet araştırması / sayfa okuma / görsel üretme", Risk.SAFE, "Arama sorgularınız DuckDuckGo/Vikipedi/Pollinations'a gider."),
    PHONE_INFO("Pil ve depolama bilgisi okuma", Risk.SAFE, "Yalnızca durum bilgisi."),
    APP_LAUNCH("Uygulama / sistem ayarı ekranı açma", Risk.SAFE, "Uygulama açar; ayar değiştirmez."),
    SETTINGS_CHANGE("Ses, parlaklık, Rahatsız Etmeyin değiştirme", Risk.REVERSIBLE, "Telefon ayarlarını değiştirir (Android ayrıca izin ister)."),
    ALARM_CALENDAR("Alarm, zamanlayıcı, takvim işlemleri", Risk.REVERSIBLE, "Alarm/takvim taslağı oluşturur, takvimi okuyabilir."),
    CONTACTS_READ("Kişileri okuma", Risk.REVERSIBLE, "Mesaj/arama için kişi bulur."),
    CALLLOG_READ("Arama geçmişini okuma", Risk.REVERSIBLE, "Son aramaları okur."),
    NOTIF_READ("Bildirimleri okuma", Risk.REVERSIBLE, "Gelen bildirimlerin içeriğini görür (diske yazılmaz)."),
    MESSAGE_APP("Mesajlaşma uygulamasında hazır mesaj açma", Risk.REVERSIBLE, "WhatsApp'ı hazır mesajla açar; gönder tuşuna siz basarsınız."),
    SMS_SEND("SMS gönderme", Risk.CRITICAL, "Her SMS için alıcı ve metin gösterilip onay istenir."),
    CALL_PHONE("Arama başlatma", Risk.CRITICAL, "Her arama için onay istenir."),
    ACCESSIBILITY("Ekranı okuma / ekrana dokunma", Risk.CRITICAL, "Erişilebilirlik servisi; ekrandaki her şeyi görebilir. Her işlem onaylı."),
    MEDIA_EDIT("Video/ses/altyazı dosyası oluşturma", Risk.REVERSIBLE, "Çalışma klasöründe yeni medya dosyası üretir."),
    PLAN_WRITE("Plan ve görev kaydı", Risk.SAFE, "Hedef/görev listesini günceller."),
    KNOWLEDGE_WRITE("Bilgi merkezine kayıt", Risk.REVERSIBLE, "Araştırma ve belgeleri arşivler."),
    AUTOMATION_MANAGE("Otomasyon kuralı ekleme/silme", Risk.CRITICAL, "Tekrarlayan görev kuralı oluşturur; her seferinde onay."),
    AUTOMATION_RUN("Arka planda otomasyon çalıştırma", Risk.REVERSIBLE, "Kurallar zamanı gelince çalışır; kritik işlemler arka planda ASLA yapılmaz."),
    TOOL_CREATE("Kendi aracını oluşturma", Risk.CRITICAL, "Saf-JS araç ekler (dosya/ağ erişimi yok); test geçmeden ve onayınız olmadan etkinleşmez."),
    APP_BUILD("Mini uygulama üretme, değiştirme, çalıştırma", Risk.REVERSIBLE, "Zyren istediğiniz uygulamayı tek dosyalık HTML olarak üretir ve kum havuzunda çalıştırır (ağ/kamera/telefon erişimi yok). Açıklamanız bulut modele gider."),
    APP_DELETE("Mini uygulama silme", Risk.CRITICAL, "Uygulamayı çöp klasörüne taşır; her seferinde onay ister."),
    AGENT_RUN("Uzman ajan çalıştırma", Risk.SAFE, "Alt ajanlar yalnızca kendi rollerinin araçlarını ve aynı izinleri kullanır.")
}

/** İlk kurulum varsayılanları: hassas/riskli her şey KAPALI, kullanıcı açıkça seçer. */
fun defaultEnabled(c: Capability): Boolean = when (c) {
    Capability.SHELL_ANY, Capability.SMS_SEND, Capability.CALL_PHONE, Capability.ACCESSIBILITY, Capability.NOTIF_READ,
    Capability.CALLLOG_READ, Capability.CONTACTS_READ, Capability.MESSAGE_APP, Capability.SETTINGS_CHANGE,
    Capability.AUTOMATION_RUN, Capability.TOOL_CREATE, Capability.AUTOMATION_MANAGE -> false
    else -> true
}

data class ApprovalRequest(val capability: Capability, val detail: String)

/**
 * Merkezi izin kapısı. Her araç buradan geçer: izin açık mı → kilit modu → kritikse kullanıcı onayı → denetim kaydı.
 * Ajanlar/araçlar bu sınıfa erişemez; izinleri yalnızca arayüz (kullanıcı) değiştirebilir.
 */
class PermissionManager(context: Context, private val audit: AuditDao) {
    private val prefs = context.getSharedPreferences("zyren_permissions", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(Capability.entries.associateWith { prefs.getBoolean(it.name, defaultEnabled(it)) })
    val enabled: StateFlow<Map<Capability, Boolean>> = _enabled
    private val _lockdown = MutableStateFlow(prefs.getBoolean("__lockdown", false))
    val lockdown: StateFlow<Boolean> = _lockdown
    private val _onboarded = MutableStateFlow(prefs.getBoolean("__onboarded", false))
    val onboarded: StateFlow<Boolean> = _onboarded
    private val detector = AnomalyDetector()

    var approvalHandler: (suspend (ApprovalRequest) -> Boolean)? = null

    fun setEnabled(cap: Capability, on: Boolean) {
        prefs.edit().putBoolean(cap.name, on).apply()
        _enabled.value = _enabled.value + (cap to on)
    }
    fun finishOnboarding() { prefs.edit().putBoolean("__onboarded", true).apply(); _onboarded.value = true }
    fun setLockdown(on: Boolean) { prefs.edit().putBoolean("__lockdown", on).apply(); _lockdown.value = on; if (!on) detector.reset() }

    suspend fun check(cap: Capability, detail: String): Boolean {
        if (_enabled.value[cap] != true) { log(cap.name, cap.risk.name, detail, "reddedildi: izin kapalı"); watch(cap, true); return false }
        if (_lockdown.value && cap.risk != Risk.SAFE) { log(cap.name, cap.risk.name, detail, "reddedildi: kilit modu"); return false }
        if (cap.risk == Risk.CRITICAL) {
            val ok = approvalHandler?.invoke(ApprovalRequest(cap, detail)) ?: false
            log(cap.name, cap.risk.name, detail, if (ok) "kullanıcı onayladı" else "kullanıcı reddetti / onay penceresi yok")
            watch(cap, !ok)
            return ok
        }
        log(cap.name, cap.risk.name, detail, "izin verildi")
        watch(cap, false)
        return true
    }

    /** Aracın riskini söyler (arka plan çalıştırıcı kritik araçları atlar). */
    fun riskOf(cap: Capability?): Risk = cap?.risk ?: Risk.SAFE

    suspend fun note(label: String, detail: String, outcome: String) = log(label, Risk.SAFE.name, detail, outcome)

    private suspend fun watch(cap: Capability, denied: Boolean) {
        val reason = detector.record(System.currentTimeMillis(), cap.risk == Risk.CRITICAL, denied) ?: return
        setLockdown(true)
        log("KILIT_MODU", Risk.CRITICAL.name, reason, "hata: kilit modu etkinleşti — riskli işlemler durduruldu")
    }

    private suspend fun log(cap: String, risk: String, detail: String, outcome: String) {
        audit.insert(AuditEntity(capability = cap, risk = risk, detail = detail.take(200), outcome = outcome.take(200)))
        audit.prune()
    }
}
