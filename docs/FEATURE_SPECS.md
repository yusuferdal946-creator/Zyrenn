# Yeni Özellik Şartnamesi (PLANLAMA BELGESİ — kod yok, test yok)

## Durum ölçeği (bir özellik yalnızca en son basamakta "TAMAM" sayılır)
`PLANLANDI` → `KOD YAZILDI` → `DERLENDİ` → `CİHAZDA TEST EDİLDİ` → `TAMAM`
v0.3.0'da 12 özelliğin çoğunun ilk sürümü **KOD YAZILDI** (derlenmedi, çalıştırılmadı). PLANLANDI kalanlar: Telefon–PC bağlantısı, LiteRT-LM geçişi. Aşağıdaki 'v0.3.0 uygulama durumu' bölümüne bakın.

## Aşama 0 — KAPI (yeni özelliklerden önce zorunlu)
1. v0.2.0'ı GitHub Actions ile derle; derleme hatalarını düzelt → durum `DERLENDİ`.
2. `docs/TEST_CHECKLIST.md`'yi Infinix X6873'te uygula; sonuçları `TEST_RESULTS.md`'ye yaz.
3. Çalışan özellikler `CİHAZDA TEST EDİLDİ` olur. Aşama 1'e ancak çekirdek (sohbet, hafıza, dosya, izin) testten geçince geçilir.

## Planlanan sıra
Aşama 1: Test/Kalite + Hata/Performans Merkezi (mevcut denetim kaydı ve ResourceMonitor üzerine kurulur; diğer her şeyin ölçü aracı)
Aşama 2: Akıllı Planlama → Aşama 3: Bilgi Merkezi → Aşama 4: Güvenli Otomasyon
Aşama 5: Yaşayan Hafıza, Çoklu Uzman Ajanlar, Kendi Araçlarını Geliştirme
Aşama 6: Simülasyon Laboratuvarı, Otonom Görev Merkezi, Telefonun Dijital İkizi, Holografik 3D Kontrol Merkezi
Her aşama sonunda: derle → cihazda test → ancak sonra işaretle.

---
## A) Talep edilen 5 özellik

### 1. Akıllı Planlama
- Kapsam: hedefi alt görevlere böler; günlük/uzun vadeli plan; ilerleme, öncelik, geciken/başarısız görevi yeniden planlama.
- Mimari: Room tabloları `goals`, `tasks` (durum, öncelik, bitiş, bağımlılık, deneme sayısı); araçlar `plan_olustur`, `gorev_guncelle`, `plan_goster`; "Plan" ekranı.
- Bağımlılıklar: Hafıza (karar/tercih), ChatEngine araç döngüsü, Güvenli Otomasyon (zamanlama/hatırlatma), Test/Kalite (görev "bitti" kanıtı).
- Güvenlik: plan yalnızca öneri/kayıttır; plandan doğan her eylem normal izin+onay hattından geçer; model plana kendi başına "tamamlandı" yazamaz — kanıt (test/kullanıcı onayı) ister.
- Test: görev bölme çıktısı şema doğrulaması; gecikme→yeniden planlama birim testi; uygulama yeniden başlayınca kalıcılık; cihazda 3 gün gerçek kullanım.

### 2. Otomatik Test ve Kalite Kontrol
- Kapsam: üretilen kodu çalıştır/test et, güncelleme sonrası duman testi (smoke) seti, hata raporu, başarısız testi hafızaya yaz, test edilmeyeni "tamamlandı" gösterme.
- Mimari: `FeatureRegistry` (her özellik: durum ölçeği + son test sonucu + tarih); test tanımları (JS/kabuk betikleri + uygulama içi duman testleri); `test_calistir` aracı; durum panosu.
- Bağımlılıklar: ShellRunner, JsRunner, Hata/Çözüm hafızası, denetim kaydı. Kod derleme gerektiren testler GitHub Actions'ta (telefonda derleyici yok).
- Güvenlik: testler sandbox'ta; test yan etkisi olan eylemler (SMS, arama) gerçek gönderim yapmaz, sahte uygulayıcı (mock) kullanır; gerçek cihaz testi yalnızca kullanıcı tetikler.
- Test: testlerin kendisini doğrulamak için bilinçli bozuk örnek (testin başarısız olduğunu görmek); kayıt kalıcılığı; "test yok = TAMAM değil" kuralı birim testi.

### 3. Bilgi Merkezi
- Kapsam: araştırma/belge arşivi, kaynak+bağlantı, konu sınıflama, eski-yeni karşılaştırma, projelerde kullanma.
- Mimari: Room `knowledge_items` (başlık, özet, konu, etiketler, kaynak URL, alınma tarihi, sürüm, içerik dosyası yolu); FTS arama; araçlar `bilgi_kaydet`, `bilgi_ara`, `bilgi_karsilastir`; web araştırması sonucu "kaydet" akışı.
- Bağımlılıklar: WebTools, Workspace, Hafıza (özet ilişkisi). Yerel gömme (embedding) yok; ilk sürüm anahtar kelime/FTS.
- Güvenlik: web içeriği "güvenilmez" etiketli saklanır; kaynak ve tarih zorunlu; model kaynaksız bilgiyi "doğrulanmış" işaretleyemez; silme/dışa aktarma onaylı.
- Test: aynı kaynağın iki sürümünü karşılaştırma; konu sınıflama tutarlılığı; ZIP taşıma ile bilgi merkezinin geçmesi.

### 4. Hata ve Performans Merkezi
- Kapsam: Zyren hataları, RAM/CPU/pil/depolama izleme, yavaşlama tespiti, neden araştırma+çözüm önerisi, durum raporu.
- Mimari: mevcut `audit` hata kayıtları + `ResourceMonitor`'a zaman serisi (Room `metrics`, düşük örnekleme, 7 gün sınır); eşik kuralları (ör. boş RAM <500 MB); `sistem_raporu` aracı; "Durum" ekranı.
- Bağımlılıklar: ResourceMonitor, denetim kaydı, WebTools (neden araştırma), Hafıza (Hata/Çözüm).
- Güvenlik: izleme yalnızca Zyren'in kendi süreci + genel sistem sayaçları; başka uygulamaların içeriğine bakmaz; örnekleme sıklığı pil için sınırlı; çözüm önerileri uygulanmadan önce onay.
- Test: yapay yük (büyük model yükleme) ile eşik tetikleme; 24 saat pil etkisi ölçümü; rapor doğruluğunu Android ayarlarıyla karşılaştırma.

### 5. Güvenli Otomasyon
- Kapsam: tekrarlı görevler, kullanıcı kuralları ("her sabah 8'de pil durumu söyle"), zamanlama, işlem geçmişi, riskli işlemde onay, istenince durdurma.
- Mimari: Room `automations` (kural, zaman/tetikleyici, aktif, son çalışma); WorkManager (periyodik/zamanlı); yürütme her zaman `ToolRegistry.execute` üzerinden; **Genel Durdurma** düğmesi (tüm otomasyonları askıya al); geçmiş ekranı.
- Bağımlılıklar: ToolRegistry, PermissionManager, Planlama, Test/Kalite.
- Güvenlik: kritik yetkiler (SMS, arama, silme, üzerine yazma, erişilebilirlik) arka planda ASLA sessiz çalışmaz — bildirimle onay beklenir, onaylanmazsa atlanır; her otomasyon için açık yetki listesi; çalışma sınırı (günlük adet); hata sayacı eşiğinde otomatik durma; her adım denetim kaydına yazılır.
- Test: durdurma düğmesinin her koşulda çalışması; arka planda kritik işlemin onaysız yürümediği; cihaz yeniden başlatma/Doze sonrası zamanlama; pil tüketimi.

---
## B) "7 büyük sistem" — DİKKAT: ayrıntıları elimde yok
Bu 7 sistemin adı verildi, ancak şartnameleri bu sohbette paylaşılmadı. Aşağıdakiler **yalnızca isimlerden çıkarılmış geçici varsayımlardır**; onay/düzeltme gelmeden geliştirilmez.

| Sistem (varsayım) | Mevcut yapıyla bağ | Güvenlik sınırı | Test gereksinimi | Aşama |
|---|---|---|---|---|
| Yaşayan Hafıza: hafızanın kullanımla güncellenmesi, önem/eskime puanı, özetleme, çelişki tespiti | MemoryRepository, Bilgi Merkezi, Planlama | Kullanıcı görür/düzenler/siler; otomatik yazılanlar işaretli; sınır ve silme politikası | Çelişki/tekrar senaryoları, geri alma | 5 |
| Çoklu Uzman Ajanlar: rol bazlı (kod, araştırma, telefon, test) ajanlar, ortak koordinatör | ChatEngine araç döngüsü, plugin sistemi | Her ajanın ayrı yetki listesi; ajanlar birbirinin izinlerini devralmaz; adım/maliyet sınırı | Rol yetki ihlali denemesi; döngü/sonsuz çağrı koruması | 5 |
| Kendi Araçlarını Geliştirme: yeni araç/eklenti taslağı yazma | Kodlama araçları, Test/Kalite, eklenti sistemi | Üretilen araç sandbox'ta, test geçmeden ve kullanıcı onayı olmadan etkinleşmez; çekirdek dosyalarını değiştiremez; yetkileri açıkça bildirir | Zararlı/aşırı yetkili araç reddi; test kapısı | 5 |
| Simülasyon Laboratuvarı: eylemi gerçek yapmadan önce kuru çalıştırma/deneme | Çalışma alanı kopyası, mock uygulayıcılar, Test/Kalite | Simülasyon gerçek SMS/arama/silme yapmaz; sonuç "simüle" etiketli | Simüle ≠ gerçek sonuç farkının raporlanması | 6 |
| Otonom Görev Merkezi: uzun görevleri adım adım kendi yürütme | Planlama, Otomasyon, ajanlar | Adım/süre/yetki bütçesi; kritik adımda onay; tek tuşla durdurma; sürekli açık kayıt | Kesinti/yeniden başlatma sonrası devam; durdurma | 6 |
| Telefonun Dijital İkizi: telefon durumunun (uygulamalar, ayarlar, kaynaklar) modeli | PhoneController, ResourceMonitor, Simülasyon | Yalnızca erişim izni verilen veriler; ikiz yerelde kalır, dışa gönderilmez; kişisel içerik (mesaj/kişi) kopyalanmaz | İkiz–gerçek durum tutarlılığı | 6 |
| Holografik 3D Kontrol Merkezi: sistemin 3D/görsel panosu | Hata/Performans, Planlama, Otomasyon verileri | Salt görüntüleme; kontrol eylemleri normal onay hattından; GPU/pil sınırı | Düşük FPS/pil testi, 12 GB RAM cihazda akıcılık | 6 |
Not: gerçek "holografik" donanım yoktur; telefon ekranında 3D görselleştirme (Compose Canvas/OpenGL) olarak ele alınır. Bu yorum onay bekler.

## C) Kuralın özeti
- Hiçbir özellik testsiz "tamamlandı" işaretlenmez; `FeatureRegistry` bunu kodla da zorlar (Aşama 1'de).
- Her yeni sistem mevcut `Capability` + onay + denetim hattına bağlanır; yan kapı açılmaz.
- Mevcut 47 dosya silinmez/baştan yazılmaz; eklemeler yeni paketlerle yapılır.


---
## v0.3.0 uygulama durumu (12 özelliğin tamamı korunuyor)
Durum: hepsi **KOD YAZILDI — derlenmedi, cihazda test edilmedi**. "Gerçek olan / eksik olan" sütunları dürüst sınırları belirtir.

| # | Özellik | Modül | Gerçek olan | Eksik / sınır |
|---|---|---|---|---|
| 1 | Akıllı Planlama | `plan/PlanEngine`, araçlar `hedef_ekle…`, Merkez>Plan | Kalıcı hedef/görev, öncelik, ilerleme, geciken/başarısızı yeniden planlama (artan bekleme, 3 denemede erteleme), kanıtsız BİTTİ reddi | Hedefi görevlere bölen LLM'dir (motor yalnızca kaydeder); takvim/bildirim entegrasyonu yok |
| 2 | Otomatik Test ve Kalite Kontrol | `quality/`, `app/src/test`, CI, `SelfTest` | Birim testleri (CI), cihazda kendi kendine test (Keystore/Room dahil), `FeatureRegistry` ile testsiz TAMAM'ı kodla engelleme, başarısız testi hafızaya yazma | Güncelleme sonrası **otomatik** yeniden test yok (elle başlatılır); Compose arayüz testi yok |
| 3 | Bilgi Merkezi | `knowledge/KnowledgeHub`, Merkez>Bilgi | Kayıt, konu sınıflama (anahtar kelime), kaynak+URL, sürüm ve karşılaştırma, arama, projeye aktarma | Gömme/anlamsal arama yok; sınıflama basit; web kaynaklılar "doğrulanmamış" etiketli |
| 4 | Hata ve Performans Merkezi | `monitor/PerfMonitor`, denetim kaydı, Ayar>Hata raporu | RAM/CPU/pil/depolama zaman serisi, model gecikmesi, yavaşlama tespiti, eşik tabanlı neden/çözüm önerisi, rapor | Ölçüm 15 dk'da bir (WorkManager) veya elle; nedenler kural tabanlı, otomatik web araştırması yok |
| 5 | Güvenli Otomasyon | `automation/`, WorkManager, Merkez>Otomasyon | Günlük/aralık kuralları, geçmiş, genel duraklatma, kritik işlemi arka planda atlama, hata sayacıyla durma, günlük sınır | ≥15 dk, tam zamanlı değil; arka plan yalnızca izin listesi araçları |
| 6 | Yaşayan Hafıza | `core/memory` (önem, kullanım, bakım) | Kullanımla önem, tekrar birleştirme, eskime, olası çelişki raporu, sabitleme | LLM ile özetleme yok; bakım kuralını kullanıcı ekler/çalıştırır |
| 7 | Simülasyon Laboratuvarı | `ToolRegistry.simulate`, Merkez>Simülasyon, sohbet 🧪 | Araçları çalıştırmadan izin/risk/onay + ikiz tahmini | Çalışma alanı kopyasında gerçek deneme (sandbox klon) yok |
| 8 | Kendi Araçlarını Geliştirme | `CustomTools`, `JsSandbox` | Saf-JS araç: onay (kritik) + kendi testi geçme şartı + kalıcı kayıt + silme | Kotlin/eklenti kodu üretimi ve derlemesi yok (bilinçli) |
| 9 | Otonom Görev Merkezi | Plan + Ajan + Otomasyon birleşimi, `gorev_yurut`, 🤖, Durdur | Plan görevini rol seçip ajanla yürütme (5 adım, aynı izin/onay/kilit hattı), sonucu nota yazma, durdurma | **Sürekli/kendiliğinden otonomi yok**: her yürütmeyi kullanıcı veya (güvenli) otomasyon tetikler |
| 10 | Telefonun Dijital İkizi | `twin/TwinService`, `TwinDiff` | Durum anlık görüntüsü (uygulama adları, ayarlar, kaynaklar, izinler), fark, simülasyon tahmini | Kişisel içerik kopyalanmaz (bilinçli); ikiz üzerinde "ya olsaydı" durum simülasyonu yok |
| 11 | Holografik 3D Kontrol Merkezi | `ui/CenterScreen` Nodes3D | Gerçek durum verisiyle döndürülebilir 3D düğüm görünümü (Compose Canvas), dokununca ayrıntı | Gerçek hologram değil; yalnızca görselleştirme; performans cihazda test edilmedi |
| 12 | Çoklu Uzman Ajanlar | `agents/`, `ChatEngine.runAgent`, Merkez>Ajanlar | 4 rol (kod, araştırma, telefon, test), rol başına araç izin listesi, aynı merkezi izin hattı, 5 adım, ajan başka ajan çağıramaz | Sırayla çalışır (paralel yok), aynı model; ajanlar arası mesajlaşma yok |

Hâlâ PLANLANDI: **Telefon–PC bağlantısı** (yalnızca ZIP taşıma var; kimlik doğrulamalı şifreli aktarım tasarımı gerekir), **LiteRT-LM geçişi**, **şifreli yedek dışa aktarma**.


## Ek: Uygulama Atölyesi (0.4.0) — KOD YAZILDI, test edilmedi
- Amaç: kullanıcı ne istediğini söyler; Zyren tek dosyalık HTML mini uygulama üretir, doğrular, sürümler, çalıştırır.
- Güvenlik sınırı: `MiniAppGuard` (yasak kalıplar, boyut sınırı), kilitli WebView (ağ yükü kapalı, istekler engelli, CSP, izin reddi), geçemeyen çıktı kaydedilmez, silme kritik+onaylı, sürümler geri alınabilir.
- Sınırlar: gerçek APK değil; kamera/mikrofon/konum/ağ/telefon erişimi yok. Model kalitesi sağlayıcıya bağlı.
- Test gereksinimi: birim (yazıldı: MiniAppGuardTest, AtelierTest) + cihazda üretim/çalıştırma/kısayol denemesi.

## Ek: Acil durdur (0.4.0) — KOD YAZILDI, test edilmedi
- Her ekranda düğme: kilit modu, görev iptali, otomasyon duraklatma, ses durdurma. Test gereksinimi: cihazda uzun bir görev sırasında deneme.
