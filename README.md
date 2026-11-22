# Zyren 0.4.0

Kişisel, modüler, Türkçe yapay zekâ asistanı (Android · Kotlin + Jetpack Compose). Hedef cihaz: Infinix X6873, Android 16.
Ücretsiz teknolojiler önceliklidir; ücretli hiçbir şey zorunlu değildir.

> **Dürüst durum:** Kod yazıldı, fakat **hiç derlenmedi, birim testleri hiç çalıştırılmadı, cihazda hiç denenmedi**
> (yazıldığı ortamda derleyici/SDK/ağ/telefon yoktu). İlk derlemede hatalar çıkması beklenir. Ayrıntı: `PROJECT_STATUS.md`, `TEST_RESULTS.md`.
> APK bu paketle gelmez; GitHub Actions üretir (aşağıda).

## 1) APK'yı derleme (ücretsiz, bilgisayar yalnızca tarayıcı olarak yeter)
1. github.com'da ücretsiz hesap açın, **New repository** (Private) oluşturun.
2. ZIP'i bilgisayarda açın. Depoda **Add file > Upload files** ile **klasörün İÇİNDEKİLERİ** (app, docs, gradle.properties, build.gradle.kts, settings.gradle.kts, .github …) sürükleyip bırakın, **Commit**.
   - `.github/workflows/build.yml` yüklenmediyse: **Add file > Create new file**, adına `.github/workflows/build.yml` yazıp dosya içeriğini yapıştırın.
3. **Actions** sekmesi > **Build APK** otomatik çalışır (≈5-10 dk). Yeşil tik olunca çalıştırmaya girin > **Artifacts > zyren-debug-apk** indirin (ZIP içinde `app-debug.apk`).
4. APK'yı telefona aktarın (Drive/USB/Telegram), açın, "bilinmeyen kaynaklardan kuruluma izin ver" deyin, kurun.
5. **Kırmızı çarpı (derleme hatası) olursa:** Actions günlüğündeki kırmızı satırları kopyalayıp bana gönderin; düzeltirim. İlk derlemede bu normal olabilir.

## 1a) Şifreli yedek
Dosya sekmesi > **🔒 Şifreli dışa aktar**: AES-256-GCM, parola ≥10 karakter. Parola hiçbir yerde saklanmaz; **kaybederseniz paket kurtarılamaz.** "ZIP dışa aktar (ŞİFRESİZ)" ayrıca durur. APK'yı ve test sonuçlarını üretme adımları: `docs/CI_CALISTIRMA.md`.

## 1b) CI ne yapar
Workflow adı **CI** (`.github/workflows/build.yml`). Her yüklemede sırayla: proje/sürüm/belge denetimi → gizli anahtar taraması → güvenlik politikası → **91 birim testi** → APK derlemesi (`zyren-debug-apk`) ve lint (ayrı iş) → Gradle Wrapper (`gradle-wrapper-dosyalari`). Test ve lint raporları artifact olarak yüklenir. 2 emülatör testi yalnızca Actions > CI > Run workflow > "emulator" işaretlenerek çalışır; henüz hiç çalıştırılmadı.
**Durum: CI henüz hiç çalıştırılmadı; derleme ve testlerin geçtiği DOĞRULANMADI.** Gerçek sonuçlar `TEST_RESULTS.md`'de.

## 2) İlk kurulum (telefonda)
0. İlk açılışta **izin seçim ekranı** çıkar: hassas/riskli yetkiler kapalı başlar; neyi açacağınıza siz karar verirsiniz (sonradan İzin sekmesinden değişir).
1. **Ayar** sekmesi > sağlayıcı seçin (öneri: **Google Gemini**; görsel de anlar). aistudio.google.com'dan ücretsiz anahtar alın, yapıştırıp **Kaydet**.
   Alternatif: Groq (console.groq.com), OpenRouter. Model adları değişmiş olabilir; Ayar'da düzeltin.
2. **Sohbet**'e dönüp "merhaba" yazın.
3. Telefon yönetimi için istendikçe Android izinlerini verin. Bildirim okuma ve ekran kontrolü için **İzin** sekmesindeki "Aç" düğmeleriyle Android ayarlarından Zyren'i etkinleştirin.
   **Android 13+ sideload engeli:** Ayarlar > Uygulamalar > Zyren > ⋮ > **Kısıtlı ayarlara izin ver** (bildirim erişimi, erişilebilirlik, SMS, arama geçmişi için gerekebilir).

## 3) Kullanım örnekleri
- "pil kaç?", "YouTube'u aç", "YouTube'da lofi müzik ara", "yarın saat 8'e hatırlatıcı kur", "sesi yükselt" → modele gitmeden hızlı çalışır (internetsiz de).
- "Ahmet'e yarın saat 5'te görüşelim diye mesaj gönder" → kişiyi bulur, **SMS'i onayınızla** gönderir (veya WhatsApp'ı hazır mesajla açar).
- "notlar.md dosyasına şunu yaz…", "bu klasörde şu kelimeyi ara", "şu JS kodunu çalıştır ve hatayı düzelt" → dosya/kod araçları.
- "şunu internette araştır …" → web arama + sayfa okuma (kaynak bağlantısıyla).
- 📷: görsel ekleyip "bu ekran görüntüsündeki hata ne?" (görsel destekli model gerekir).
- 🎤: konuşarak komut; 🔊: sesli yanıt; 🔁: konuşma modu (yanıttan sonra tekrar dinler).
- **Merkez** sekmesi: Plan · Bilgi · Otomasyon (⛔ genel duraklatma) · Ajanlar · Simülasyon · İkiz · Performans · Kalite (kendi kendine test, durum onayı) · Araçlar · Terminal (güvenli) · 3D.
- Dosya sekmesi: düzenle, sürüm geçmişi, yedek, ZIP dışa/içe aktarma (şifreli DEĞİL).

## 4) Yerel (çevrimdışı) model — isteğe bağlı
Ayar > Yerel model > MediaPipe uyumlu `.task` dosyası seçin (ör. Gemma küçük sürümleri, 1-3 GB). Sağlayıcı olarak "Yerel model" seçin veya
"internet yoksa yerel modele geç"i açık bırakın. Yerel modelde araç kullanımı yoktur, yalnızca sohbet.
Alternatif: PC/Termux'ta llama.cpp veya Ollama çalıştırıp "Yerel sunucu" seçeneğine yerel ağ adresini yazın (http yalnızca yerel ağda).

Daha fazlası: `docs/ARCHITECTURE.md`, `docs/TEST_CHECKLIST.md`, `PROJECT_STATUS.md`, `CHANGELOG.md`.
