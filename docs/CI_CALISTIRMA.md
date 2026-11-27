# CI'ı çalıştırma ve sonuçları bana iletme (sürüm 0.5.0; derleme/test/APK buradan yapılamadı)
Bu paketin hazırlandığı ortamda GitHub'a, internete, Kotlin derleyicisine ve Android SDK'ya erişim yoktu; bu yüzden
**derleme, 104 birim testi ve APK üretimi çalıştırılmadı.** Aşağıdaki adımlar bunları sizin yapmanızı tek tıka indirir.

## A) GitHub Actions (önerilen, ücretsiz)
1. github.com'da özel depo aç. ZIP'in **içindekileri** (app, docs, scripts, .github, *.kts, gradle.properties …) "Upload files" ile yükle.
   `.github/workflows/build.yml` görünmüyorsa: Add file > Create new file > adı `.github/workflows/build.yml`, içeriği yapıştır.
2. Actions sekmesi > **CI** otomatik başlar (veya Run workflow). Sırayla:
   - `checks`: proje/sürüm/belge denetimi, anahtar sızıntısı taraması, güvenlik politikası (≈1 dk)
   - `unit-tests`: **104 birim test** (`gradle :app:testDebugUnitTest`); özet sayılar iş özetinde, raporlar `unit-test-report` artifact'ında
   - `build`: **APK** (`zyren-debug-apk`) + Gradle Wrapper dosyaları (`gradle-wrapper-dosyalari`; gradlew, gradlew.bat, jar, properties)
   - `lint`: ayrı iş; gerçek lint hataları işi kırmızı yapar (gizlenmez), APK'yı engellemez; rapor `lint-report`
   - `emulator-tests` (isteğe bağlı): Run workflow > **emulator** işaretle; 2 cihaz testi; rapor `instrumented-test-report`
3. Kırmızı olan işe tıkla → günlükteki ilk kırmızı hata satırlarını bana yapıştır; düzeltirim. (İlk derlemede hata çıkması beklenir.)
4. Artifacts > `zyren-debug-apk` > indir > ZIP içindeki `app-debug.apk`'yı telefona kur.

## B) Kendi bilgisayarında (JDK 17 + Gradle 8.11.1)
`gradle :app:testDebugUnitTest` → test raporu `app/build/reports/tests/testDebugUnitTest/index.html`.
`gradle :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (Android SDK 36 gerekir).
Güvenlik politikası ve belge denetimi yalnızca Python ile çalışır: `python3 scripts/check_policy.py && python3 scripts/check_docs.py`.

## Sonuçları işaretleme
CI yeşil olunca `TEST_RESULTS.md` içindeki ilgili kutuyu `- [x]` yap (ancak o zaman `check_docs.py` "TAMAM/CİHAZDA TEST EDİLDİ" iddialarına izin verir).
Cihaz testi için `docs/TEST_CHECKLIST.md`; özelliğin durumunu Zyren > Merkez > Kalite'den kanıt notuyla yükselt.
