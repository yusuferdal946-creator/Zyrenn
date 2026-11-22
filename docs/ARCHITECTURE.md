# Mimari
Tek uygulama, tek kapsayıcı (`AppContainer`). Paketler:
- `core/model`: `ModelProvider`; `OpenAiCompatProvider` (bulut + yerel sunucu), `LocalLlmProvider` (MediaPipe). Yeni sağlayıcı = preset veya sınıf.
- `core/security`: `SecureStore` (şifreli anahtar), `Settings`, `PermissionManager` (Capability+Risk, onay, denetim).
- `core/memory`: Room (hafıza, sohbet, denetim), `MemoryRepository` (+ taşıma anlık görüntüsü).
- `core/workspace`: sandbox; sürüm geçmişi, çöp kutusu, ZIP yedek/geri yükleme, içe aktarma.
- `core/tools`: `ToolRegistry` (eklenti kapısı + izin kapısı), `BuiltinTools`, `ShellRunner`, `JsRunner`, `WebTools`.
- `core/ChatEngine`: hafızalı sistem istemi + araç döngüsü; `QuickCommands` kesin kalıplı komutlar.
- `phone`: `PhoneController`, `ZyrenNotificationListener`, `ZyrenAccessibilityService`.
- `voice`: `VoiceController`. `media`: `MediaTools`. `core/res`: `ResourceMonitor`.
- `ui`: Compose ekranları + `ZyrenViewModel`.

Kural: her araç bir `Capability`'ye bağlıdır → izin açık mı → kritikse kullanıcı onayı → denetim kaydı. Geri alma: sürüm/çöp/yedek.
Araç protokolü: model `<arac>{"ad":"…","args":{…}}</arac>` yazar; sonuç "güvenilmez veri" olarak geri verilir; en çok 8 adım.
Taşıma: yedeğe `.zyren/memory.json` eklenir; içe aktarmada hafıza birleştirilir.

## Planlanan genişleme (henüz kod yok — bkz. docs/FEATURE_SPECS.md)
Yeni sistemler mevcut hatta bağlanır: `Capability` → izin/onay → denetim kaydı → (gerekirse) geri alma.
- Yeni paketler: `plan/`, `quality/` (FeatureRegistry, testler), `knowledge/`, `monitor/`, `automation/` (WorkManager), sonra `agents/`, `lab/`, `twin/`, `viz/`.
- Hepsi araç olarak `ToolRegistry`'ye ve eklenti olarak `PluginRegistry`'ye kaydolur; arka plan işleri kritik yetkiyi sessizce kullanamaz.
- Kod değişikliği ilkesi: mevcut dosyalar silinmez/baştan yazılmaz; ekleme ve küçük bağlama değişiklikleri yapılır.


## v0.3.0 mimarisi (kod yazıldı, derlenmedi)
Tek hat: **araç → `ToolRegistry` (eklenti kapısı) → `PermissionManager` (izin → kilit modu → onay → denetim) → eylem.** Yeni modüller bu hattan geçer; yan kapı yoktur.
- Güvenlik çekirdeği (saf JVM, birim testli): `PathGuard`, `ZipSafe`, `SafeShell`, `JsSandbox`, `AnomalyDetector`, `defaultEnabled`.
- Durum depoları: `.zyren/*.json` (atomik `JsonStore`): planlar, bilgi, kurallar+geçmiş, ikiz, performans, özellik durumu, özel araçlar. Yedek/taşıma ZIP'ine otomatik girer; geri yüklemede `reload()` ile bellek diskle eşitlenir.
- Mantık modülleri (saf): `PlanEngine`, `KnowledgeHub`, `AutomationEngine`, `PerfMonitor`, `FeatureRegistry`, `MemoryMaintenance`, `TwinDiff`, `AgentRoles`.
- Android'e bağlı: `TwinService`, `PhoneController`, `AutomationWorker` (WorkManager), `SelfTest`, ekranlar.
- Ajanlar: `ChatEngine.runAgent(rol)` rolün eklenti araçlarıyla, 5 adım; `simulationMode` açıkken tüm araçlar simüle edilir.
- Kalite kapısı: `FeatureRegistry` testsiz TAMAM'ı kodla reddeder; yalnızca arayüz durumu yükseltebilir.
- Arka plan: yalnızca `AutomationEngine.BACKGROUND_ALLOWED` içindeki, kritik olmayan araçlar.
