package com.zyren.app.core.tools

import com.zyren.app.AppContainer
import com.zyren.app.core.security.Capability

private fun now() = System.currentTimeMillis()

/** Uygulama Atölyesi araçları. Hepsi ToolRegistry/izin hattından geçer; silme KRİTİK (her seferinde onay). */
fun registerAtelierTools(c: AppContainer) {
    val reg = c.tools

    reg.add(Tool("atolye_uygulama_yap", "atelier", "ad, istek — istenen mini uygulamayı (tek dosyalık HTML, kum havuzunda; ağ/kamera/telefon erişimi yok) üretir, doğrular ve Atölye'ye kaydeder", Capability.APP_BUILD,
        { "Uygulama üret: " + it.str("ad").orEmpty() }) { a ->
        val app = c.atelier.create(a.req("ad"), a.req("istek"), now()).getOrThrow()
        c.atelier.openRequest.value = app.id
        "Uygulama hazır: ${c.atelier.summary(app)}. Atölye sekmesinde açıldı. Çalışma anı hatası çıkarsa 'Zyren düzelt' ile ya da atolye_uygulama_degistir ile düzeltilir."
    })
    reg.add(Tool("atolye_uygulama_degistir", "atelier", "id, istek — mevcut mini uygulamayı değiştirir (yeni sürüm olur, eskisi geri alınabilir)", Capability.APP_BUILD,
        { "Uygulamayı değiştir: " + it.str("id").orEmpty() }) { a ->
        val app = c.atelier.revise(a.req("id"), a.req("istek"), now()).getOrThrow()
        c.atelier.openRequest.value = app.id
        "Güncellendi: ${c.atelier.summary(app)}"
    })
    reg.add(Tool("atolye_uygulama_listele", "atelier", "kayıtlı mini uygulamaları listeler", Capability.APP_BUILD, { "Uygulamaları listele" }) {
        c.atelier.list().joinToString("\n") { c.atelier.summary(it) }.ifBlank { "Henüz uygulama yok." }
    })
    reg.add(Tool("atolye_uygulama_ac", "atelier", "id — mini uygulamayı Atölye sekmesinde açar", Capability.APP_BUILD,
        { "Uygulamayı aç: " + it.str("id").orEmpty() }) { a ->
        val id = a.req("id")
        if (c.atelier.get(id) == null) throw IllegalArgumentException("uygulama bulunamadı: $id")
        c.atelier.openRequest.value = id
        "Açılıyor: $id"
    })
    reg.add(Tool("atolye_uygulama_geri_al", "atelier", "id, surum — uygulamayı eski bir sürüme döndürür (sürümler silinmez)", Capability.APP_BUILD,
        { "Sürüme dön: " + it.str("id").orEmpty() + " v" + it.str("surum").orEmpty() }) { a ->
        val v = a.int("surum") ?: throw IllegalArgumentException("'surum' sayı olmalı")
        "Geri alındı: " + c.atelier.summary(c.atelier.rollback(a.req("id"), v, now()).getOrThrow())
    })
    reg.add(Tool("atolye_uygulama_sil", "atelier", "id — mini uygulamayı siler (çöp klasörüne taşınır); her seferinde kullanıcı onayı ister", Capability.APP_DELETE,
        { "Uygulamayı sil: " + it.str("id").orEmpty() }) { a ->
        if (c.atelier.delete(a.req("id"), now())) "Silindi (çöpe taşındı)." else "HATA: uygulama bulunamadı."
    })
}
