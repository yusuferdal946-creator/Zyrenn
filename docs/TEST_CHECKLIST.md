# Gerçek cihaz test listesi (Infinix X6873) — hepsi şu an: TEST EDİLMEDİ
İşaretle: ✅ çalıştı · ❌ çalışmadı (not düş) · ⚠️ kısmen

## Temel
- [ ] Uygulama açılıyor, 6 sekme geziliyor
- [ ] Gemini/Groq anahtarı kaydedilip sohbet yanıt veriyor
- [ ] "hatırla: kahveyi sade içerim" → Hafıza sekmesinde görünüyor; düzenle/sabitle/sil çalışıyor
- [ ] Uygulama kapatılıp açılınca sohbet ve hafıza duruyor
## Dosya / güvenlik
- [ ] Dosya oluştur, düzenle, üzerine yazma onay penceresi çıkıyor, "Sürümler"den geri dönülüyor
- [ ] Sil → onay → çöpe taşıma; Yedek al; ZIP dışa aktar; başka cihaza/aynı cihaza paket içe aktar (+hafıza birleşiyor)
- [ ] İzin sekmesinde anahtar kapatınca işlem reddediliyor; denetim kaydı görünüyor
## Kod
- [ ] Terminal: `ls`, `echo merhaba` (onaysız); `touch a.txt` (onay ister)
- [ ] JS: `console.log(1+1); 3*4` çıktı veriyor; hatalı kod hata döndürüyor
- [ ] Sohbette "a.js dosyası yaz ve çalıştır" → araç döngüsü çalışıyor
## Telefon
- [ ] "pil kaç?" · "depolama durumu" · "YouTube'u aç" · "YouTube'da lofi ara"
- [ ] "sesi yükselt" · "parlaklığı azalt" (izin ekranı) · "rahatsız etmeyin aç" (izin ekranı)
- [ ] "yarın saat 8'e hatırlatıcı kur" → Saat uygulamasında alarm var mı?
- [ ] Kişi bulma; SMS (onay → gerçekten gitti mi?); WhatsApp hazır mesaj; arama; arama geçmişi
- [ ] Bildirim erişimi açılınca "bildirimleri oku"; Erişilebilirlik açılınca "ekranı oku", "ana ekrana dön"
- [ ] Takvim taslağı açılıyor; "takvimimde ne var" okuyor
## Ses
- [ ] 🎤 Türkçe komut anlaşılıyor; 🔊 sesli yanıt Türkçe; 🔁 konuşma modu; konuşmayı kesme
## Araştırma / görsel / medya
- [ ] "… hakkında araştır" kaynak bağlantılı yanıt; 📷 ile görsel analiz (Gemini)
- [ ] "bir kedi görseli üret" → gorseller/ klasöründe dosya
- [ ] Dosya içe aktar ile iki MP4 → kırp / birleştir; .srt yazma
## Yerel model / kaynak
- [ ] .task modeli içe aktar → sohbet; RAM düşükken reddediyor mu; internet kapalıyken otomatik geçiş
- [ ] Ayar > Cihaz kaynakları değerleri mantıklı

## v0.3.0 ek maddeler — hepsi: TEST EDİLMEDİ
- [ ] İlk açılışta izin seçim ekranı çıkıyor; riskli yetkiler kapalı başlıyor; "Seçimlerimle başla" çalışıyor
- [ ] Terminal: `ls`, `cat ../x` (reddedilmeli), `cat /etc/passwd` (reddedilmeli), `ls | cat` (reddedilmeli), `rm dosya` (onay + çöpe)
- [ ] Ham kabuk izin ekranında kapalı; açılınca her komutta onay istiyor
- [ ] JS: `while(true){}` uygulamayı DONDURMADAN ~3 sn'de "zaman aşımı" veriyor; `java.lang.System.exit(0)` hata veriyor
- [ ] ZIP: bilerek bozuk/zip-slip paketi içe aktarma reddediliyor ve çalışma alanı bozulmuyor
- [ ] Merkez > Kalite > "kendi kendine testleri çalıştır": tüm durumlar geçiyor
- [ ] Hızlı arka arkaya 10+ kritik istek → kilit modu şeridi çıkıyor, "Kilidi aç" çalışıyor
- [ ] Plan: hedef ekle, ✓ ile bitir, yeniden planla; 🤖 ile ajan yürütme; ⛔ Durdur
- [ ] Bilgi: kaydet, aynı URL ile 2. sürüm, Karşılaştır
- [ ] Otomasyon: kural ekle (onay), "şimdi çalıştır"; genel duraklatma; İzin'de arka plan izni açıkken 15-30 dk sonra geçmişe düşüyor; kritik araçlı kural arka planda ATLANIYOR
- [ ] İkiz: Güncelle ×2 + Karşılaştır; Simülasyon modu açıkken SMS komutu gerçekten gitmiyor
- [ ] Performans raporu; 3D ekranı döndürme akıcılığı
- [ ] Android 16: ekran_oku banka/hassas ekranda boş/kısıtlı dönüyor mu (beklenen), erişilebilirlik açılabiliyor mu
- [ ] Sesli komut Türkçe çalışıyor (queries düzeltmesi sonrası)

## 0.3.1 ek maddeler — hepsi: TEST EDİLMEDİ
- [ ] Şifreli dışa aktar → aynı cihazda şifreli içe aktar: dosyalar+hafıza+planlar geri geliyor; yanlış parola reddediliyor ve çalışma alanı bozulmuyor
- [ ] Şifreli dosyayı bir hex editörde/ZIP olarak açmaya çalış: düz metin görünmüyor; düz "Paket içe aktar" şifreli paketi reddediyor
- [ ] llama.cpp'yi aynı telefonda (127.0.0.1) çalıştır: "Yerel sunucu" http ile çalışıyor; PC IP'si (http) REDDEDİLİYOR (beklenen)
- [ ] Aynı anda iki kritik istek (ör. ajan + otomasyon "şimdi çalıştır"): onay pencereleri SIRAYLA geliyor; 2 dk yanıt verme → reddedilmiş sayılıyor
