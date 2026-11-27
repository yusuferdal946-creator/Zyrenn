# PROJECT_STATUS — Zyren 0.5.0 (devir notu)
Güncelleme: 2026-11-22

## 0) Gerçek durum — önce bunu okuyun
- **Derlenmedi.** Yazıldığı ortamda Kotlin derleyicisi, Gradle, Android SDK ve ağ yoktu (kontrol edildi). APK **üretilemedi**.
- **Çalıştırılmış şeyler (yalnızca bunlar):** `scripts/check_docs.py` (belge bütünlüğü), `scripts/check_policy.py` (statik güvenlik politikası; kasıtlı ihlallerle sınandı ve yakaladı), kaynak dosyalarda parantez dengesi / çapraz başvuru taramaları. Başka hiçbir test çalıştırılmadı.
- **GitHub Actions çalıştırılmadı, APK üretilmedi** (erişim yok). Bunları çalıştırma adımları: `docs/CI_CALISTIRMA.md`.
- **104 birim testi yazıldı, hiçbiri çalıştırılmadı** (CI'da çalışacak). Testleri/kodu elle karşılaştırırken 3 gerçek hata bulunup düzeltildi (otomasyon tetikleyici doğrulaması, hafıza çelişki anahtarı, şifreleme kodunda tür hatası).
- **Cihazda (Infinix X6873) hiçbir şey test edilmedi.**
- Sürümler araştırıldı: AGP 8.9.1 (API 36 için gerekli — doğrulandı), MediaPipe 0.10.27 (dokümandaki güncel; **bakım modunda**), Rhino 1.7.15 (var olduğu doğrulandı). Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.10.01, Room 2.6.1, WorkManager 2.9.1, OkHttp 4.12.0 **hafızadan, doğrulanmadı**.

## 0a) CI/belge düzeltmeleri (0.5.0)
- Workflow yeniden düzenlendi (checks → unit-tests → build / lint / isteğe bağlı emulator-tests); lint artık `continue-on-error` ile gizlenmiyor; test raporları her durumda yüklenir; wrapper artifact yolları açık.
- `scripts/check_project.py` eklendi: sürüm ve test sayısı tutarlılığı CI'da zorunlu.
- **Hâlâ yapılamayan:** derleme, 104 birim + 2 cihaz testi, APK, wrapper jar üretimi (bu ortamda mümkün değil). Hiçbiri "tamam" sayılmaz.

## 0b) 0.5.0 değişikliği (bu tur) — yine derlenmedi
- Kişilik/mizah ayarı eklendi (kod yazıldı, derlenmedi, cihazda denenmedi). Sürüm numaraları 0.5.0'a eşitlendi. Derleme ortamı yine yoktu (Gradle/SDK/ağ yok); APK üretilemedi.
- **Uygulama Atölyesi** ve **Acil durdur** eklendi (KOD YAZILDI, derlenmedi, cihazda denenmedi). Sınır: Atölye uygulamaları kum havuzunda çalışan HTML'dir; gerçek APK değildir (ağ/kamera/telefon erişimi yok). Gerçek APK üretimi (GitHub Actions ile otomatik derleme + kurulum) YAPILMADI; Android kurulum için her seferinde kullanıcı onayı da ister.
- Açık kaynak merkezi (GitHub lisans/güncellik), bilgi grafiği (metin) ve paralel ajan eklendi (KOD YAZILDI, test edilmedi).
- **Yapılmadı / sınırlı (şartnameye göre):** uyandırma kelimesi (sürekli mikrofon dinleme pil/gizlilik/Android kısıtı nedeniyle eklenmedi), PDF metin okuma (kütüphane gerekir), ayrı proje çalışma alanları, bildirim geçmişi (gizlilik için bilerek diske yazılmıyor), Telefon–PC eşleştirme, otomatik altyazı çıkarma, GitHub üzerinden otomatik APK üretimi ve bağımlılık analizi, bilgi grafiği görselleştirmesi. Yeniden başlatma sonrası görev devamı: WorkManager kalıcıdır ama cihazda doğrulanmadı.

## 1a) Bu tur (0.3.1) — kod yazıldı, derlenmedi, test edilmedi
- `usesCleartextTraffic` kaldırıldı → ağ güvenlik yapılandırması: http yalnızca 127.0.0.1/localhost; istemci koruması aynı liste; web okuma yalnızca https.
- **AES-256-GCM şifreli yedek dışa/içe aktarma** (`BackupCrypto`, parola → PBKDF2; anahtar saklanmaz; kurcalama/kesme/yer değiştirme tespiti; Dosya sekmesinde 🔒 düğmeleri; düz içe aktarma şifreli paketi reddeder). BackupCrypto için 13 test.
- Onay penceresi: sıraya alma (Mutex) + 120 sn zaman aşımı → red (üst üste binen onayda askıda kalma hatası giderildi).
- `scripts/check_policy.py` CI'a eklendi: her araç yetkiye bağlı, kritik yetkiler kritik, riskli yetkiler varsayılan kapalı, araçlar izin sistemine erişemez, onay işleyicisi yalnızca arayüzde, arka planda kritik araç yok.
- Bağımlılık doğrulaması (docs/ANDROID16.md): KSP, AGP/API 36, Rhino, MediaPipe doğrulandı; Kotlin 2.0.21 eskimiş (2.4.20 güncel) → ertelendi.

## 1) Önceki sürümde YAPILAN (kod yazıldı)
**Güvenlik düzeltmeleri** (ayrıntı: docs/SECURITY.md)
- Terminal: gerçek sınırlama için yerleşik komut yorumlayıcı (`SafeShell`, süreç başlatmaz); ham kabuk varsayılan kapalı + "izole değil" uyarısı.
- JS çalıştırıcı: WebView (ana iş parçacığını dondurabiliyordu) yerine Rhino kum havuzu; süre/bellek/yığın/çıktı sınırı, Java erişimi yok.
- ZIP: yol kaçışı, boyut/sayı sınırı, geçici dizinde açıp başarılıysa değiştirme; sembolik bağlar yedeğe/aramaya girmez.
- API anahtarı: Keystore AES-GCM (deprecated EncryptedSharedPreferences kaldırıldı); `.zyren/` ayrılmış yol.
- İlk kurulum izin seçim ekranı; riskli/hassas yetkiler varsayılan KAPALI; şüpheli işlem → kilit modu.
- Android 11+/16: TTS ve konuşma tanıma `queries`, veri çıkarma kuralları (bkz. docs/ANDROID16.md).
**Yeni modüller (12 özellik)** — tablo aşağıda, ayrıntı docs/FEATURE_SPECS.md
**CI** (.github/workflows/build.yml): belge+sızıntı denetimi → birim testleri → APK+lint+wrapper üretimi; elle tetiklenen emülatör işi.

## 2) TEST EDİLEN
| Ne | Sonuç |
|---|---|
| scripts/check_docs.py (12 özellik iki belgede, kanıtsız TAMAM yok) | Geçti (yerelde çalıştırıldı) |
| Parantez dengesi, VM↔UI ve kapsayıcı başvuruları, import hedefleri (betikle) | Temiz |
| Kotlin derleme | **Yapılamadı** |
| scripts/check_policy.py (63 araç↔yetki, kritik işlemler, onay hattı, cleartext, yedek) | Geçti (yerelde çalıştırıldı) |
| Birim testleri (104) | **Çalıştırılmadı** |
| Cihaz / emülatör testi | **Yapılmadı** |

## 3) BEKLEYEN (öncelik sırasıyla)
1. GitHub Actions'ta derle; derleme hatalarını düzelt (beklenen: küçük Kotlin/Compose/Rhino/MediaPipe API uyumsuzlukları).
2. Birim testlerini çalıştır; başarısız olanları düzelt (test kodu da hata içerebilir).
3. Cihazda: docs/TEST_CHECKLIST.md; ardından Merkez > Kalite'den durumları kanıt notuyla yükselt.
4. targetSdk 36 geçişi, Kotlin 2.0.21→2.4.x yükseltmesi, LiteRT-LM geçişi, Telefon–PC bağlantısı (kimlik doğrulamalı, şifreli taşıma tasarımı).
5. Güncelleme sonrası otomatik yeniden test; LLM ile hafıza özetleme; sandbox klonunda simülasyon.

## 4) 12 özellik — durum (hiçbiri test edilmedi)
| Özellik | Durum | Aşama | Bağımlılıklar | Güvenlik sınırı | Test gereksinimi |
|---|---|---|---|---|---|
| Akıllı Planlama | KOD YAZILDI | 2 | Hafıza, araç döngüsü, Kalite | Plan yalnızca kayıt; kanıtsız BİTTİ reddedilir | Birim (yazıldı) + cihaz 3 gün |
| Otomatik Test ve Kalite Kontrol | KOD YAZILDI | 1 | ShellRunner'sız; JsSandbox, Room, Keystore | Testler sandbox'ta; durumu yalnızca kullanıcı yükseltir | CI + SelfTest cihazda |
| Bilgi Merkezi | KOD YAZILDI | 3 | WebTools, Workspace | Web içeriği "doğrulanmamış"; yazma izin hattından | Birim + sürüm karşılaştırma |
| Hata ve Performans Merkezi | KOD YAZILDI | 1 | ResourceMonitor, denetim, WorkManager | Yalnızca Zyren süreci + genel sayaçlar | Yapay yük + 24 sa pil ölçümü |
| Güvenli Otomasyon | KOD YAZILDI | 4 | ToolRegistry, PermissionManager, WorkManager | Arka planda kritik yok, duraklatma/kilit, hata durdurma | Doze/yeniden başlatma, pil |
| Yaşayan Hafıza | KOD YAZILDI | 5 | MemoryRepository, Room | Sabitlenen/hata-çözüm korunur; kullanıcı görür/siler | Birim + çelişki senaryoları |
| Simülasyon Laboratuvarı | KOD YAZILDI | 6 | ToolRegistry, İkiz | Gerçek işlem yapmaz, "simüle" etiketli | Simüle≠gerçek karşılaştırması |
| Kendi Araçlarını Geliştirme | KOD YAZILDI | 5 | JsSandbox, ToolRegistry | Saf JS, onay+test şartı, çekirdeği değiştiremez | Zararlı/sonsuz döngü araç reddi |
| Otonom Görev Merkezi | KOD YAZILDI | 6 | Plan, Ajanlar, Otomasyon | Kendiliğinden otonomi yok; 5 adım; Durdur; aynı izin hattı | Kesinti/durdurma testleri |
| Telefonun Dijital İkizi | KOD YAZILDI | 6 | PhoneController, ResourceMonitor | Kişisel içerik kopyalanmaz, cihazda kalır | İkiz–gerçek tutarlılık |
| Holografik 3D Kontrol Merkezi | KOD YAZILDI | 6 | FeatureRegistry verisi | Salt görüntüleme | FPS/pil, 12 GB cihazda akıcılık |
| Çoklu Uzman Ajanlar | KOD YAZILDI | 5 | ChatEngine, eklentiler | Rol başına araç listesi, ajan ajan çağıramaz | Rol ihlali denemesi |

## 5) Temel özellikler (7 bölüm + önceki aşamalar) — durum (hiçbiri test edilmedi)
| Alan | Durum | Not |
|---|---|---|
| Bulut model sistemi, sohbet, hafıza, izin paneli, sandbox, sürüm, yedek | KOD YAZILDI | |
| Güvenli terminal + JS | KOD YAZILDI | Android'de derleyici yok; ham kabuk izole değil |
| Telefon (pil, uygulama, ayar, alarm, takvim, kişi, SMS, arama, bildirim, ekran) | KOD YAZILDI | Android 16'da erişilebilirlik kısıtlı olabilir; wifi/bluetooth doğrudan açılıp kapatılamaz |
| Ses (TR tanıma + TTS + konuşma modu) | KOD YAZILDI | Uyandırma kelimesi yok |
| Araştırma, görsel analiz/üretme, video kırp/birleştir, .srt | KOD YAZILDI | Otomatik altyazı çıkarma yok |
| Yerel model (MediaPipe .task) + yerel sunucu | KOD YAZILDI | MediaPipe bakım modunda |
| Telefon–PC bağlantısı | PLANLANDI | Yalnızca ZIP taşıma (çalışma alanı + planlar + hafıza) |
| LiteRT-LM geçişi | PLANLANDI | |

## 6) Değişen / eklenen dosyalar (bu tur)
Yeni: `core/security/{PathGuard,AnomalyDetector}`, `core/workspace/ZipSafe`, `core/tools/{JsSandbox,SafeShell,CustomTools,FeatureTools}`, `store/JsonStore`, `plan/`, `knowledge/`, `automation/`, `monitor/`, `quality/`, `twin/`, `agents/`, `core/memory/MemoryMaintenance`, `ui/{Onboarding,CenterScreen}`, `app/src/test/*` (5 dosya), `app/src/androidTest/*`, `scripts/check_docs.py`, `docs/{SECURITY,ANDROID16}.md`, `res/xml/data_extraction_rules.xml`.
Değişen: `Workspace`, `PermissionManager`, `SecureStore`(+Settings), `Runners`(ham kabuk), `ToolRegistry`, `ChatEngine`, `BuiltinTools`, `AppContainer`/`ZyrenApp`, `Db`/`MemoryRepository`, `ZyrenViewModel`, `MainActivity`, `ChatScreen`, `TerminalScreen`, `build.gradle(.kts)`, `AndroidManifest`, CI, belgeler. Yedek: `Zyren-yedek-v0.2.0-oncesi.zip`.
