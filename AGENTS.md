# Zyren (Zyrenn) — Proje Notları

## Çalıştırma / Doğrulama
- Yerel sandbox'ta **git CLI ve Android SDK yok**. Derleme/test yalnızca GitHub Actions'ta olur (`.github/workflows/build.yml`): `checks → unit-tests → build (APK artifact zyren-debug-apk) → lint`.
- Kaynak kontrolü `git_commit` aracıyla yapılır (GitHub'a bağlı). Dal oluşturma/derleme GitHub Actions üzerinden yürütülür.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (artifact); kullanıcı GitHub Actions'tan indirir.

## V2 geliştirme ilkeleri
- Mevcut çalışan özellikler ve kullanıcı verileri korunur. Mevcut dosyalar silinmez/baştan yazılmaz; ekleme ve küçük bağlama değişiklikleri yapılır.
- Paket adı `com.zyren.app` korunur. V2 için versionCode 5 / versionName 0.5.0.
- API anahtarları/imza dosyaları değiştirilmez; `.env` ve `local.properties` dokunulmaz.
- Başarısız testler gizlenmez/atlanmaz; düzeltilir.

## Veritabanı migrasyonu (Room)
- `ZyrenDatabase` şu an version = 1, exportSchema = false. V2'de şema değişiklikleri (çoklu sohbet, yeni tablolar vb.) olacaksa: her değişiklik için `Migration` nesnesi eklenir ve `addMigrations(...)` ile kaydedilir; `exportSchema = true` + `room.schemaLocation` açılarak şema JSON'ları korunur.
- Kullanıcı verisi koruması: `.zyren/memory.json` yedek/geri yükleme akışı (MemoryRepository.snapshotTo/mergeFrom) korunur ve genişletilir.
- Rollback: git geçmişi + yedek ZIP mekanizması (Workspace/BackupCrypto).
