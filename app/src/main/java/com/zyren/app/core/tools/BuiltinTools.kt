package com.zyren.app.core.tools

import com.zyren.app.core.memory.MemoryRepository
import com.zyren.app.core.security.Capability
import com.zyren.app.core.workspace.Workspace
import com.zyren.app.media.MediaTools
import com.zyren.app.phone.PhoneController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun <T> Result<T>.text(ok: (T) -> String): String = fold(ok) { "HATA: ${it.message}" }
private val none: (Args) -> String = { "" }

/** Eklenti kimlikleri: files, code, research, phone, vision, media, memory. */
fun registerBuiltinTools(
    reg: ToolRegistry, ws: Workspace, safe: SafeShell, shell: ShellRunner, js: JsSandbox, web: WebTools,
    phone: PhoneController, memory: MemoryRepository
) {
    // ---- Dosyalar (izin kontrolü Workspace içinde) ----
    reg.add(Tool("dosya_listele", "files", "klasor? — çalışma alanındaki dosyaları listeler", null, none) { a ->
        ws.list(a.str("klasor") ?: "").text { l ->
            if (l.isEmpty()) "(boş)" else l.joinToString("\n") { (if (it.isDir) "[klasör] " else "") + it.path + (if (it.isDir) "" else " (${it.size} B)") }
        }
    })
    reg.add(Tool("dosya_oku", "files", "yol — dosya içeriğini okur", null, none) { a -> ws.read(a.req("yol")).text { it } })
    reg.add(Tool("dosya_yaz", "files", "yol, icerik — dosya oluşturur; varsa üzerine yazar (kullanıcı onayı + eski sürüm saklanır)", null, none) { a ->
        ws.write(a.req("yol"), a.str("icerik") ?: "").text { "Yazıldı: ${a.req("yol")}" }
    })
    reg.add(Tool("dosya_sil", "files", "yol — dosyayı çöp kutusuna taşır (onay ister)", null, none) { a ->
        ws.delete(a.req("yol")).text { "Silindi (çöpe taşındı): ${a.req("yol")}" }
    })
    reg.add(Tool("dosya_ara", "files", "sorgu — dosya adı/içeriğinde arar", null, none) { a ->
        ws.search(a.req("sorgu")).text { l -> if (l.isEmpty()) "Sonuç yok" else l.joinToString("\n") { it.path } }
    })

    // ---- Kodlama ----
    reg.add(Tool("komut_calistir", "code", "komut — GÜVENLİ terminal (yerleşik: ls, cat, grep, find, mkdir, cp, mv, rm…; yalnızca çalışma alanı; boru/yönlendirme yok)", null, none) { a ->
        safe.run(a.req("komut")).text { it }
    })
    reg.add(Tool("ham_kabuk", "code", "komut — HAM Android kabuğu, İZOLE DEĞİL; varsayılan kapalı; her çalıştırmada kullanıcı onayı. Yalnızca gerçekten gerekirse", null, none) { a ->
        shell.run(a.req("komut")).text { it }
    })
    reg.add(Tool("js_calistir", "code", "kod veya dosya — JavaScript'i kum havuzunda (Rhino; süre/bellek sınırlı, dosya/ağ yok) çalıştırır; çıktı ve hatayı döner", Capability.CODE_RUN,
        { "JS: " + (it.str("dosya") ?: it.str("kod").orEmpty()).take(80) }) { a ->
        val code = a.str("dosya")?.let { ws.read(it).getOrThrow() } ?: a.req("kod")
        withContext(Dispatchers.Default) { js.run(code) }
    })
    reg.add(Tool("hata_cozumu_kaydet", "memory", "hata, cozum — karşılaşılan hatayı ve işe yarayan çözümü kalıcı hafızaya yazar", Capability.MEMORY_WRITE,
        { "Hata/çözüm kaydı" }) { a ->
        if (memory.add(MemoryRepository.ERR, "Hata: ${a.req("hata")} → Çözüm: ${a.req("cozum")}")) "Kaydedildi" else "Zaten kayıtlı"
    })
    reg.add(Tool("hafiza_ekle", "memory", "bilgi, kategori? — kullanıcı hakkında kalıcı bilgi kaydeder (Tercih/Proje/Karar/Görev)", Capability.MEMORY_WRITE,
        { "Hafızaya ekle: " + it.str("bilgi").orEmpty().take(80) }) { a ->
        if (memory.add(a.str("kategori") ?: "Tercih", a.req("bilgi"))) "Kaydedildi" else "Zaten kayıtlı"
    })

    // ---- Araştırma ----
    reg.add(Tool("web_ara", "research", "sorgu — internette arar (başlık, bağlantı, özet döner)", Capability.WEB, { "Web ara: " + it.str("sorgu").orEmpty() }) { a ->
        web.search(a.req("sorgu"))
    })
    reg.add(Tool("sayfa_oku", "research", "url — web sayfasının metnini okur", Capability.WEB, { "Sayfa oku: " + it.str("url").orEmpty() }) { a ->
        web.fetch(a.req("url"))
    })

    // ---- Görsel ----
    reg.add(Tool("gorsel_uret", "vision", "istem — ücretsiz Pollinations servisiyle görsel üretip çalışma alanına (gorseller/) kaydeder", Capability.WEB,
        { "Görsel üret: " + it.str("istem").orEmpty().take(80) }) { a ->
        val bytes = web.downloadImage(a.req("istem"))
        ws.writeNew("gorseller/" + System.currentTimeMillis() + ".jpg", bytes).text { "Kaydedildi: $it" }
    })

    // ---- Medya ----
    reg.add(Tool("video_kirp", "media", "girdi, cikti, bas_sn, bit_sn — MP4 kırpar (yeniden kodlamaz, anahtar kareye yuvarlar)", Capability.MEDIA_EDIT,
        { "Video kırp: " + it.str("girdi").orEmpty() }) { a ->
        val i = ws.fileFor(a.req("girdi")); val o = ws.fileFor(a.req("cikti"))
        require(i.isFile) { "Girdi yok" }; require(!o.exists()) { "Çıktı zaten var" }
        withContext(Dispatchers.IO) { MediaTools.trim(i, o, (a.req("bas_sn").toDouble() * 1000).toLong(), (a.req("bit_sn").toDouble() * 1000).toLong()) }
        "Oluşturuldu: ${a.req("cikti")}"
    })
    reg.add(Tool("video_birlestir", "media", "girdiler (JSON dizi), cikti — aynı biçimli MP4'leri art arda birleştirir", Capability.MEDIA_EDIT,
        { "Video birleştir → " + it.str("cikti").orEmpty() }) { a ->
        val ins = a.arr("girdiler")?.map { ws.fileFor(it.jsonPrimitive.content) } ?: throw IllegalArgumentException("'girdiler' dizi olmalı")
        val o = ws.fileFor(a.req("cikti"))
        require(ins.all { it.isFile }) { "Girdilerden biri yok" }; require(!o.exists()) { "Çıktı zaten var" }
        withContext(Dispatchers.IO) { MediaTools.merge(ins, o) }
        "Oluşturuldu: ${a.req("cikti")}"
    })
    reg.add(Tool("altyazi_yaz", "media", "dosya, satirlar — [{\"bas\":\"00:00:01,000\",\"bit\":\"00:00:03,000\",\"metin\":\"...\"}] ile .srt oluşturur (otomatik konuşma çözümleme YOK)", Capability.MEDIA_EDIT,
        { "Altyazı yaz: " + it.str("dosya").orEmpty() }) { a ->
        val rows = a.arr("satirlar") ?: throw IllegalArgumentException("'satirlar' dizi olmalı")
        val srt = rows.mapIndexed { i, e ->
            val o = e.jsonObject
            "${i + 1}\n${o["bas"]!!.jsonPrimitive.content} --> ${o["bit"]!!.jsonPrimitive.content}\n${o["metin"]!!.jsonPrimitive.content}\n"
        }.joinToString("\n")
        ws.write(a.req("dosya"), srt).text { "Yazıldı: ${a.req("dosya")}" }
    })

    // ---- Telefon ----
    reg.add(Tool("pil_durumu", "phone", "pil yüzdesi/şarj durumu", Capability.PHONE_INFO, { "Pil durumu" }) { phone.battery() })
    reg.add(Tool("depolama_durumu", "phone", "depolama doluluğu", Capability.PHONE_INFO, { "Depolama durumu" }) { phone.storage() })
    reg.add(Tool("uygulama_ac", "phone", "ad — yüklü uygulamayı adıyla açar", Capability.APP_LAUNCH, { "Uygulama aç: " + it.str("ad").orEmpty() }) { a -> phone.openApp(a.req("ad")) })
    reg.add(Tool("ayar_ekrani_ac", "phone", "ayar — wifi|internet|bluetooth|pil_tasarrufu|ses|ekran|depolama|erisilebilirlik|bildirim_erisimi|uygulama_ayarlari|genel", Capability.APP_LAUNCH,
        { "Ayar ekranı: " + it.str("ayar").orEmpty() }) { a -> phone.openSettings(a.req("ayar")) })
    reg.add(Tool("ses_ayarla", "phone", "islem (yukselt|azalt|sessiz|ayarla), yuzde?", Capability.SETTINGS_CHANGE,
        { "Ses: " + it.str("islem").orEmpty() }) { a -> phone.volume(a.req("islem"), a.int("yuzde")) })
    reg.add(Tool("parlaklik_ayarla", "phone", "yuzde? veya degisim? (+/- sayı)", Capability.SETTINGS_CHANGE,
        { "Parlaklık değiştir" }) { a -> phone.brightness(a.int("yuzde"), a.int("degisim")) })
    reg.add(Tool("rahatsiz_etme", "phone", "durum (ac|kapat)", Capability.SETTINGS_CHANGE,
        { "Rahatsız Etmeyin: " + it.str("durum").orEmpty() }) { a -> phone.dnd(a.req("durum") == "ac") })
    reg.add(Tool("alarm_kur", "phone", "saat, dakika, mesaj? — 24 saat içindeki en yakın saate alarm/hatırlatıcı kurar", Capability.ALARM_CALENDAR,
        { "Alarm: ${it.str("saat")}:${it.str("dakika")}" }) { a -> phone.alarm(a.int("saat") ?: throw IllegalArgumentException("'saat' gerekli"), a.int("dakika") ?: 0, a.str("mesaj") ?: "Zyren") })
    reg.add(Tool("zamanlayici_kur", "phone", "saniye, mesaj?", Capability.ALARM_CALENDAR, { "Zamanlayıcı: ${it.str("saniye")} sn" }) { a ->
        phone.timer(a.int("saniye") ?: throw IllegalArgumentException("'saniye' gerekli"), a.str("mesaj") ?: "Zyren")
    })
    reg.add(Tool("takvim_olustur", "phone", "baslik, baslangic (ISO: 2026-11-21T17:00), bitis?, konum? — taslak açar, kaydı kullanıcı yapar", Capability.ALARM_CALENDAR,
        { "Takvim: " + it.str("baslik").orEmpty() }) { a -> phone.calendarInsert(a.req("baslik"), a.req("baslangic"), a.str("bitis"), a.str("konum")) })
    reg.add(Tool("takvim_oku", "phone", "gun? — önümüzdeki günlerin etkinlikleri", Capability.ALARM_CALENDAR, { "Takvim oku" }) { a -> phone.calendarRead(a.int("gun") ?: 7) })
    reg.add(Tool("kisi_bul", "phone", "ad — kişilerde arar, numaraları döner", Capability.CONTACTS_READ, { "Kişi ara: " + it.str("ad").orEmpty() }) { a -> phone.findContact(a.req("ad")) })
    reg.add(Tool("sms_gonder", "phone", "numara, metin — SMS gönderir (her seferinde kullanıcı onayı)", Capability.SMS_SEND,
        { "SMS → ${it.str("numara")}: ${it.str("metin")}" }) { a -> phone.sendSms(a.req("numara"), a.req("metin")) })
    reg.add(Tool("whatsapp_mesaj", "phone", "numara, metin — WhatsApp'ı hazır mesajla açar (gönder tuşuna kullanıcı basar)", Capability.MESSAGE_APP,
        { "WhatsApp → ${it.str("numara")}: ${it.str("metin")}" }) { a -> phone.whatsapp(a.req("numara"), a.req("metin")) })
    reg.add(Tool("ara", "phone", "numara — arama başlatır (onay ister)", Capability.CALL_PHONE, { "Ara: " + it.str("numara").orEmpty() }) { a -> phone.call(a.req("numara")) })
    reg.add(Tool("arama_gecmisi", "phone", "adet? — son aramalar", Capability.CALLLOG_READ, { "Arama geçmişi" }) { a -> phone.callLog(a.int("adet") ?: 10) })
    reg.add(Tool("bildirimleri_oku", "phone", "adet? — son bildirimler (Bildirim erişimi açık olmalı)", Capability.NOTIF_READ, { "Bildirimleri oku" }) { a -> phone.notifications(a.int("adet") ?: 10) })
    reg.add(Tool("youtube_ara", "phone", "sorgu — YouTube'da arar", Capability.APP_LAUNCH, { "YouTube: " + it.str("sorgu").orEmpty() }) { a -> phone.youtube(a.req("sorgu")) })
    reg.add(Tool("ekran_oku", "phone", "ekrandaki metni okur (Erişilebilirlik; onay ister)", Capability.ACCESSIBILITY, { "Ekran metnini oku" }) { phone.screenText() })
    reg.add(Tool("ekrana_dokun", "phone", "metin — ekranda bu metne sahip öğeye dokunur (onay ister)", Capability.ACCESSIBILITY,
        { "Dokun: " + it.str("metin").orEmpty() }) { a -> phone.clickText(a.req("metin")) })
    reg.add(Tool("ekrana_yaz", "phone", "metin — odaklı alana yazar (onay ister)", Capability.ACCESSIBILITY,
        { "Yaz: " + it.str("metin").orEmpty() }) { a -> phone.typeText(a.req("metin")) })
    reg.add(Tool("sistem_tusu", "phone", "tus (geri|ana_ekran|son_uygulamalar|bildirimler) (onay ister)", Capability.ACCESSIBILITY,
        { "Sistem tuşu: " + it.str("tus").orEmpty() }) { a -> phone.global(a.req("tus")) })
}
