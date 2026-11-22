# Değişiklik Günlüğü
## 0.4.0 — 2026-11-22 (derlenmedi, test edilmedi)
- Eklendi: Kişilik ve mizah ayarı (`core/Personality.kt`): mizah yoğunluğu (Kapalı/Az/Orta/Yüksek) ve konuşma tarzı (Dengeli/Resmî/Teknik/Samimi); Ayarlar'dan değiştirilir, sistem istemine yönerge olarak eklenir. Ciddi konularda mizah geri çekilir; duygu tahmini kesin gerçek sayılmaz.
- Eklendi: `PersonalityTest` (3 birim testi; toplam 76, hiçbiri çalıştırılmadı).
- Düzeltildi: sürüm numarası tutarsızlığı (build.gradle 0.3.0 / ayarlar ekranı 0.2.0) → 0.4.0, versionCode 4.
- Eklendi: **Uygulama Atölyesi** (`atelier/`, `ui/AtelierScreen.kt`, `core/tools/AtelierTools.kt`): "şunu yap" denince Zyren modelle tek dosyalık HTML uygulama üretir; çıktıyı `MiniAppGuard` ile denetler (ağ/dış kaynak/eval/kamera/yönlendirme yasak; ihlalde geri bildirimle en çok 3 deneme; geçemeyen asla kaydedilmez), sürümler (20'ye kadar, geri alma), kilitli WebView'de çalıştırır (ağ yükü kapalı, tüm istekler engelli, CSP, izin istekleri reddedilir), çalışma anı JS hatasını yakalayıp "Zyren düzelt" ile düzelttirir, ana ekrana kısayol ekler. 6 araç (`atolye_*`), 2 yeni yetki: APP_BUILD (geri alınabilir), APP_DELETE (kritik, onaylı).
- Eklendi: **⛔ Acil durdur** (her ekranda): kilit modu + sohbet/ajan/Atölye üretimi iptal + ses durdur + otomasyon duraklat + bekleyen onayı reddet; kilidi yalnızca kullanıcı açar.
- Eklendi: **Açık kaynak merkezi** (`acik_kaynak_ara`, `acik_kaynak_incele`): GitHub API'siyle (anahtarsız) lisans uyumu, bakım güncelliği, arşiv bilgisi ve öneri; kod indirmez/eklemez, bağımlılık analizi yapmaz.
- Eklendi: **Bilgi grafiği** (`bilgi_grafigi`): hafıza + bilgi merkezi kayıtları arasında sözcük benzerliğine dayalı ilişkiler, kaynak belirtilerek (görsel arayüz YOK, metin çıktısı).
- Eklendi: **Paralel ajan** (`paralel_ajan`): en çok 3 uzman ajan aynı anda; aynı izin/onay hattı.
- Eklendi: testler `OpenSourceAndGraphTest` (3); `MiniAppGuardTest` (6), `AtelierTest` (6); toplam 91, hiçbiri çalıştırılmadı. `check_policy.py` yeni dosya/yetkileri kapsıyor (73 araç).
- CI: workflow yeniden düzenlendi (checks/unit-tests/build/lint/emulator-tests), lint gizlenmiyor, rapor artifact'ları, `scripts/check_project.py` (sürüm + test sayısı denetimi); README/TEST_RESULTS/CI_CALISTIRMA/ANDROID16 gerçek duruma göre güncellendi.
- Değişmedi: izin, güvenlik, onay ve araç hatları.

## 0.3.1 — Ağ sertleştirme, şifreli yedek, politika denetimi (derlenmedi, test edilmedi)
`usesCleartextTraffic` kaldırıldı (http yalnızca 127.0.0.1/localhost); AES-256-GCM parola tabanlı şifreli yedek dışa/içe aktarma; onay penceresi sıralama+zaman aşımı; `scripts/check_policy.py` ve CI adımı; 14 yeni birim testi (toplam 73); CI çalıştırma kılavuzu; bağımlılık doğrulama notları.

## 0.3.0 — Güvenlik sertleştirme + 12 özelliğin ilk sürümü (derlenmedi, test edilmedi)
**Güvenlik:** SafeShell (gerçek sınırlama) ve varsayılan kapalı ham kabuk; Rhino JS kum havuzu (WebView donma riski giderildi); ZIP geri yükleme sertleştirildi (sınırlar, geçici dizin); Keystore AES-GCM anahtar deposu; ilk kurulum izin ekranı ve güvenli varsayılanlar; şüpheli işlem algılama ve kilit modu; `.zyren/` ayrılmış yol; TTS/konuşma `queries` düzeltmesi; veri çıkarma kuralları.
**Yeni:** Akıllı Planlama, Bilgi Merkezi, Güvenli Otomasyon (WorkManager), Hata/Performans Merkezi, Kalite kapısı + kendi kendine test, Yaşayan Hafıza bakımı, Simülasyon, Özel (saf JS) araçlar, Dijital İkiz, Uzman Ajanlar, Otonom görev yürütme, 3D Merkez.
**Altyapı:** AGP 8.9.1/compileSdk 36, MediaPipe 0.10.27 (bakım modunda), Rhino, WorkManager; 59 birim testi; CI: belge+sızıntı denetimi → test → APK/lint/wrapper.
**Bilinen:** derleme ve cihaz testi bekliyor; bkz. PROJECT_STATUS.md.

## 0.2.0 — Aşama 2–5 kodu (derlenmedi, test edilmedi)
- Araç döngüsü, kabuk/JS çalıştırma, hata→hafıza, terminal ekranı
- Telefon yönetimi (Intent/ContentResolver/SmsManager/Accessibility/NotificationListener), hızlı komutlar
- Türkçe ses: konuşma tanıma, TTS, konuşma modu
- Web araştırma, görsel ekleme/analiz, görsel üretme
- MP4 kırpma/birleştirme, .srt yazma
- Eklenti aç/kapat, yetki genişletme, hata raporu
- Yerel model (MediaPipe), yerel sunucu, internet yoksa otomatik geçiş, kaynak izleme
- ZIP paket içe/dışa aktarma (çalışma alanı + hafıza)
## 0.1.0 — Aşama 1 (derlenmedi, test edilmedi)
- İskelet, bulut model, hafıza, izin yöneticisi, sandbox/yedek, arayüz

## Planlama (kod yok)
- 5 yeni özellik + 7 sistem için şartname ve yol haritası eklendi (docs/FEATURE_SPECS.md). Yeni özellik kodu yok; kapı: v0.2.0 derleme+cihaz testi.
