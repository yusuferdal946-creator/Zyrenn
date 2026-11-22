# Android 16 (API 36) uyumluluk notları
Durum: araştırma ve kod düzeltmesi yapıldı; **hiçbir madde cihazda test edilmedi**.

## Doğrulananlar (kaynaklı)
- **AGP:** compileSdk 36 için AGP ≥ 8.9.1 gerekir (8.5'in yalnızca API 34'e kadar test edildiği bildirilmiş). Projede AGP 8.9.1 + Gradle 8.11.1 + compileSdk 36 kullanılıyor. AGP 9.x de mevcut (9.0.1 Ocak 2026; Gradle 9.1+, Kotlin 2.2.10+ gerektirir) — geçiş bilinçli ertelendi.
- **targetSdk 35'te bırakıldı:** Android 16 cihazda uyumluluk modunda çalışır; targetSdk 36'ya geçiş cihaz testlerinden sonra yapılacak.
- **MediaPipe LLM Inference (Android) "bakım modunda"**; Google LiteRT-LM'e geçmeyi öneriyor. Dokümandaki güncel bağımlılık 0.10.27 → projeye alındı (0.10.14'ten). **LiteRT-LM geçişi yapılmadı** (API doğrulanmadı) → özellik listesinde "PLANLANDI".
- **Erişilebilirlik:** Android 16'da `isAccessibilityTool=true` bildirmeyen uygulamalar, uygulamaların `accessibilityDataSensitive` ile işaretlediği görünümlere erişemez. Zyren erişilebilirlik aracı DEĞİLDİR ve bu bayrağı bilerek **bildirmez** (yanlış bildirim Play/Play Protect tarafından engellenir). Sonuç: banka vb. hassas ekranlar `ekran_oku` ile okunamayabilir. Advanced Protection Mode, araç olmayan erişilebilirlik servislerini kısıtlayabilir (Android 17 haberlerinde otomatik kısıtlama bildirilmiş) → **erişilebilirlik özellikleri (ekran oku/dokun/yaz) Android 16/17'de güvenilir olmayabilir**; çekirdek özellikler bunlara bağlı değildir.
- **Sideload (APK) kısıtı:** Android 13+ bildirim erişimi ve erişilebilirlik için "Kısıtlı ayarlara izin ver" gerektirir (Uygulama bilgisi > ⋮).

## Kodda yapılan Android 11+/16 düzeltmeleri
- `queries` eklendi: TTS servisi ve konuşma tanıma servisi (eksikse sesli özellikler Android 11+'ta sessizce çalışmazdı).
- Başlatıcı uygulama listesi için `queries` (QUERY_ALL_PACKAGES kullanılmıyor).
- SMS: izin yoksa/reddedilirse SMS uygulamasında hazır mesaj açılır (gönder'e kullanıcı basar). SEND_SMS ve READ_CALL_LOG sideload'da Android tarafından kısıtlı olabilir.
- Arama: CALL_PHONE yoksa çevirici açılır. Bildirimler: yalnızca bellekte tutulur.
- `allowBackup=false` + `dataExtractionRules` (Android 12+ cihaz aktarımı/bulut yedeği dışı).

## Ağ: cleartext (bu tur)
`usesCleartextTraffic="true"` kaldırıldı; `network_security_config.xml` ile http yalnızca `localhost`/`127.0.0.1`'e izinli, diğer her şey https. Android'in bu yapılandırmayı OkHttp ile birlikte uygulaması beklenir ama cihazda doğrulanmadı.

## Bağımlılık sürümleri — doğrulama sonucu (bu tur, kaynaklı)
| Bağımlılık | Durum |
|---|---|
| KSP 2.0.21-1.0.28 | **Doğrulandı**: google/ksp sürümü (Kasım 2024), Kotlin 2.0.21 ile eşleşiyor; başka projeler AGP 8.7.3/Kotlin 2.0.21/Room 2.6.1/OkHttp 4.12.0/coroutines 1.9.0 ile birlikte kullanıyor |
| WorkManager 2.9.1 | Var olduğu biliniyor (2.10.0 da var); bu projede birlikte derleme doğrulanmadı |
| AGP 8.9.1 + Gradle 8.11.1 + compileSdk 36 | API 36 için AGP ≥ 8.9.1 **doğrulandı**; Kotlin 2.0.21 ile birlikte derleme doğrulanmadı |
| Rhino 1.7.15, MediaPipe 0.10.27 | Var olduğu doğrulandı; Android'de çalışması cihazda doğrulanmadı |
| Compose BOM 2024.10.01, Room 2.6.1, OkHttp 4.12.0, lifecycle 2.8.7, activity-compose 1.9.3, serialization 1.7.3, coroutines 1.9.0 | Hafızadan; geniş kullanımda olan eski ama kararlı sürümler, derlemeyle doğrulanacak |
| **Kotlin 2.0.21** | **Eskimiş**: güncel kararlı Kotlin 2.4.20 (2026-09-07). Derleyici olmadan toplu yükseltme riskli olduğundan ertelendi (migration adayı) |

## Doğrulanamayanlar / cihazda denenecekler
- Android 16'da SMS/arama/bildirim API'lerinin davranışı (resmî sürüm notları okunmadı; yalnızca yukarıdaki kaynaklar).
- Predictive back, edge-to-edge (targetSdk 36'da zorunlu): Compose Scaffold insets kullanıyor ama test edilmedi.
- MediaPipe 0.10.27 ile `LlmInference` seçenekleri (kod 0.10.14 API'siyle yazıldı; derleme gerektirir).
- 16 KB sayfa boyutu: MediaPipe yerel kütüphanelerinin uyumu doğrulanmadı (4 KB sayfalı cihazlarda sorun yok).


## 0.4.0 derleme uyumluluk durumu (DOĞRULANMADI — derleme yapılamadı)
- AGP 8.9.1 ↔ Gradle 8.11.1 ↔ JDK 17: sürüm tablosuna göre uyumlu olması beklenir; CI'da doğrulanacak.
- Kotlin 2.0.21 ↔ KSP 2.0.21-1.0.28 ↔ Compose derleyici eklentisi 2.0.21: aynı Kotlin sürümü; uyumlu olması beklenir.
- compileSdk 36 (AGP 8.9.1 destekler); targetSdk 35 bilerek bırakıldı (davranış değişiklikleri cihaz testinden sonra ele alınacak).
- CI, SDK platformu `android-36` ile Build Tools 36.0.0 ve 35.0.0'ı `android-actions/setup-android` ile kurar; paket adlarının geçerliliği ilk çalıştırmada doğrulanır.
- Bağımlılıkların (MediaPipe 0.10.27, Rhino 1.7.15, Room 2.6.1, WorkManager 2.9.1, OkHttp 4.12.0…) Maven'dan çözümlendiği DOĞRULANMADI (ağ yok). Kullanılmayan bağımlılık incelemesi yapılmadı.
- Atölye WebView'i: `blockNetworkLoads`, `shouldInterceptRequest` ve CSP birlikte kullanılır; Android 16'da cihazda denenmedi.
