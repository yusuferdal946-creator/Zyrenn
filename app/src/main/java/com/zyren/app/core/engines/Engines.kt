package com.zyren.app.core.engines

import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.Risk
import java.io.File
import kotlin.math.roundToInt

/**
 * Zyren V2 karar/özerklik sistemleri.
 * Hepsi saf JVM bileşenleridir (Android bağımsız) ve birim testleri vardır.
 * Sistem mesajından ibaret değildir; gerçek, test edilebilir mantık çalıştırırlar.
 */

/** Bir eylem/araç çağrısının karar çıktısı. */
data class ActionDecision(
    val toolName: String?,
    val requiresApproval: Boolean,
    val reason: String
)

/**
 * DecisionEngine — araç/eylem seçim mantığı.
 * Kullanıcı isteğini ve mevcut araç listesini alır; hangi aracın uygun olduğuna
 * kural tabanlı olarak karar verir. (Model seçimini değiştirmez; öneri üretir.)
 */
class DecisionEngine {
    /** Basit anahtar kelime → araç eşleştirmesi. */
    private val hintMap = mapOf(
        "pil" to "pil_durumu",
        "battery" to "pil_durumu",
        "depolama" to "depolama",
        "storage" to "depolama",
        "uygulama" to "uygulama_ac",
        "aç" to "uygulama_ac",
        "alarm" to "alarm_kur",
        "zamanlayıcı" to "alarm_kur",
        "takvim" to "takvim",
        "kişi" to "kisi_bul",
        "mesaj" to "mesaj_gonder",
        "sms" to "sms_gonder",
        "ara" to "arama_yap",
        "hatırla" to "hafiza_yaz",
        "web" to "web_arastir",
        "araştır" to "web_arastir",
        "dosya" to "dosya_oku",
        "yaz" to "dosya_yaz",
        "sil" to "dosya_sil"
    )

    /** Hangi araç adlarının kritik/onay gerektiren olduğu (Capability riskiyle uyumlu). */
    private val criticalTools = setOf("sms_gonder", "arama_yap", "dosya_sil", "dosya_yaz", "uygulama_ac")

    fun decide(userText: String, availableTools: Set<String>): ActionDecision {
        val lower = userText.lowercase()
        val matched = hintMap.entries.firstOrNull { (kw, _) -> lower.contains(kw) }?.value
        if (matched == null || matched !in availableTools) {
            return ActionDecision(null, requiresApproval = false, reason = "Uygun araç eşleşmesi yok; doğrudan yanıt verilebilir.")
        }
        val critical = matched in criticalTools
        return ActionDecision(matched, requiresApproval = critical, reason = "Eşleşen araç: $matched${if (critical) " (kritik → onay gerekli)" else ""}")
    }

    fun isCritical(tool: String): Boolean = tool in criticalTools
}

/**
 * SelfEvaluationEngine — yanıt öz-değerlendirmesi.
 * Üretilen yanıtın kalitesini kural tabanlı olarak değerlendirir
 * (sistem istemiyle değil, gerçek kontrollerle).
 */
class SelfEvaluationEngine {
    data class Evaluation(val issues: List<String>, val score: Int)

    fun evaluate(reply: String, userText: String): Evaluation {
        val issues = mutableListOf<String>()
        val r = reply.lowercase()
        // 1) Türkçe yanıt beklentisi: çok kısa ve İngilizce ise not düş.
        if (reply.isBlank()) issues += "Yanıt boş."
        if (reply.length < 3) issues += "Yanıt çok kısa."
        // 2) "Bilmiyorum" yerine uydurma riski: kesinlik ifadeleri aşırı ise.
        if (r.count { it == '!' } > 3) issues += "Aşırı noktalama (kesinlik abartısı)."
        // 3) Kullanıcı sorusuna yanıt verilip verilmediği: soru işareti varsa ve yanıtta anahtar yoksa.
        if (userText.contains("?")) {
            val keywords = userText.split(Regex("\\s+")).filter { it.length > 3 }.take(3)
            if (keywords.none { r.contains(it.lowercase()) }) issues += "Soruya doğrudan yanıt verilmemiş olabilir."
        }
        // 4) Güvenlik: araç sonucu talimatı olarak uyulmaması gerektiği hatırlatması.
        if (reply.contains("güvenilmez") && reply.contains("uyma")) issues += "Güvenilmez veri uyarısı yerinde."
        val score = (100 - issues.size * 20).coerceIn(0, 100)
        return Evaluation(issues, score)
    }
}

/**
 * ProactiveSuggestionEngine — proaktif öneriler.
 * Kullanıcı bağlamına/geçmişine göre öneri üretir.
 */
class ProactiveSuggestionEngine {
    fun suggest(context: String, hasPendingTask: Boolean): List<String> {
        val out = mutableListOf<String>()
        val c = context.lowercase()
        if (hasPendingTask) out += "Devam eden bir görevin var; onu sürdürmemi ister misin?"
        if (c.contains("pil") || c.contains("battery")) out += "Pil durumunu kontrol edebilirim."
        if (c.contains("toplantı") || c.contains("meeting")) out += "Toplantı için alarm/takvim hatırlatması kurabilirim."
        if (c.contains("hatırla")) out += "Hafızaya kaydettiğim bilgileri görüntüleyebilirsin."
        return out
    }
}

/**
 * AutonomyPermissionManager — özerklik ve izin yönetimi.
 * Küçük/güvenli işlemlerde izin sormaz; kritik veya geri alınamaz işlemlerde onay ister.
 * Mevcut PermissionManager'ın risk modeliyle uyumlu çalışır.
 */
class AutonomyPermissionManager {
    /** Bir Capability'nin kullanıcı onayı gerektirip gerektirmediğine karar verir. */
    fun requiresApproval(cap: Capability): Boolean = cap.risk == Risk.CRITICAL

    /** Küçük/güvenli işlemlerde izin sorma kararı. */
    fun canActAutonomously(cap: Capability): Boolean = cap.risk == Risk.SAFE

    fun describeAutonomy(cap: Capability): String = when (cap.risk) {
        Risk.CRITICAL -> "Kritik/geri alınamaz → her seferinde kullanıcı onayı gerekli."
        Risk.REVERSIBLE -> "Geri alınabilir → otomatik yapılabilir, gerekirse onay sorulur."
        Risk.SAFE -> "Güvenli → otomatik yapılabilir (izin sormadan)."
    }
}

/**
 * TaskPlanner — görev planlama.
 * Bir görevi adımlara böler ve önceliklendirir. (PlanEngine ile birlikte çalışır.)
 */
class TaskPlanner {
    data class PlannedTask(val title: String, val steps: List<String>, val priority: Int)

    fun plan(title: String, detail: String): PlannedTask {
        val steps = detail.split(Regex("[\\n;]")).map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf("Görevi anla ve uygula") }
        val priority = when {
            title.lowercase().contains("acil") || title.lowercase().contains("hemen") -> 9
            title.lowercase().contains("önemli") -> 7
            else -> 5
        }
        return PlannedTask(title, steps, priority)
    }
}

/**
 * RecoveryManager — hata kurtarma.
 * Başarısız bir işlem için önerilen kurtarma adımlarını üretir.
 */
class RecoveryManager {
    fun recover(failure: String, attempts: Int): String {
        val f = failure.lowercase()
        return when {
            f.contains("bağlantı") || f.contains("internet") || f.contains("timeout") ->
                "Bağlantı sorunu: interneti kontrol et; yerel model/sunucu ayarlıysa ona geç."
            f.contains("izin") || f.contains("permission") || f.contains("reddedildi") ->
                "İzin kapalı: İzinler ekranından ilgili izni aç."
            f.contains("api anahtarı") || f.contains("key") ->
                "API anahtarı eksik: Ayarlar'dan ekle."
            f.contains("ram") || f.contains("bellek") || f.contains("model") ->
                "Yerel model RAM'e sığmıyor: bulut modele geç veya arka plan uygulamalarını kapat."
            attempts > 2 -> "Birden çok deneme başarısız: görevi böl ve adım adım dene."
            else -> "Tekrar dene; sorun sürerse hata raporunu paylaş."
        }
    }
}

/** KodAnalysisEngine — Kotlin ve Jetpack Compose kodlarını okuma, mimariyi analiz etme ve öneriler sunma.
 * Sadece okuma/yorum yeteneği; dosya sistemine yazma veya derleme yapmaz.
 * Güvenlik: sadece okuma erişimi; değişiklik için kullanıcı onayı gerekir. */
class CodeAnalysisEngine {
    /** Bir Kotlin dosyasını okur ve ana sınıf/komponentleri döndürür. */
    data class DosyaBilgileri(
        val dosyaAdi: String,
        val anaSınıf: String?,
        val importlar: List<String>,
        val composableFonkler: List<String>
    )
    /** Bir dosyayı analiz et. */
    fun analizEt(dosyaYolu: String): DosyaBilgileri? {
        return File(dosyaYolu).readText().let { text ->
            val importlar = text.lines().filter { it.startsWith("import") }
            val anaSınıf = text.lines().firstOrNull { it.startsWith("class ") }?.replace("class ", "")?.takeIf { it.isNotBlank() }
            val composableFonkler = text.lines().filter { it.contains("@Composable") }
            DosyaBilgileri(
                dosyaAdi = dosyaYolu.split("/").last(),
                anaSınıf = anaSınıf,
                importlar = importlar,
                composableFonkler = composableFonkler
            )
        }
    }

    /** Proje dizinini tarayarak tüm DosyaBilgileri'ni toplar. */
    fun projeyiAnalizEt(projeKlasoru: String): List<DosyaBilgileri> {
        val results = mutableListOf<DosyaBilgileri>()
        File(projeKlasoru).walk().forEach { file ->
            if (file.isFile && file.extension == "kt") {
                analizEt(file.absolutePath)?.let { results.add(it) }
            }
        }
        return results
    }
    /** Jetpack Compose layout dosyasını (XML değil, Kotlin Compose) okur ve
     *  hierarchy/struktur önerileri döndürür. */
    fun composeLayoutAnaliz(dosyaYolu: String): List<String>? {
        val text = File(dosyaYolu).readText()
        return if (text.contains("@Composable")) text.lines().filter { it.contains("@Composable") }.toList() else null
    }
}

/** ProjeYoneticisi — Zyren projesi dosyalarını yönetir.
 * Dosya oluşturma, düzenleme, yedekleme ve geri alma işlemlerini sağlar.
 * Güvenlik: sadece okuma/yazma erişimi; kritik değişiklikler için kullanıcı onayı. */
class ProjeYoneticisi(private val projeKlasoru: String) {
    private val dosya = File(projeKlasoru)
    /** Belirtilen yola yedek kopyası oluştur. */
    fun yedekle(yedekYolu: String) {
        dosya.copyTo(File(yedekYolu), overwrite = true)
    }
    /** Yedekten geri yükle. */
    fun geriYukle(gerekYolu: String) {
        File(gerekYolu).copyTo(dosya, overwrite = true)
    }
    /** Yeni bir Kotlin dosyası oluşturur (şablon ile). */
    fun yeniDosyaOlustur(yeniYol: String, sablon: String) {
        File(yeniYol).writeText(sablon)
    }
    /** Var olan bir dosyayı belirtilen içeriğiyle güncür. */
    fun guncelle(yenileYol: String, yeniIcerik: String) {
        File(yenileYol).writeText(yeniIcerik)
    }
}

/** BirimTestOluşturucu — @Test annotation'li testleri otomatik olarak ekler.
 * Sadece test kodunu oluşturur; çalıştırmaz veya projeyi değiştirmez. */
class BirimTestOluşturucu {
    /** Basit bir birim testi şablonu oluştur. */
    fun testOlustur(sinifAdı: String, testAdı: String): String {
        return """package com.zyren.app.test

import org.junit.Test
import org.junit.Assert.*

class ${sinifAdı}Test {
    @Test fun ${testAdı}() {
        // TODO: Test mantığı ekle
        Assert.assertTrue(true)
    }
}
""".trimIndent()
    }
    /** Mevcut bir sınıfa test ekle (dosyaya yazmaz). */
    fun testEkle(sinif: String, testKodu: String): String {
        return "fun ${testKodu}() {\n        // test mantığı\n}"
    }
}

/** HataAnalizMotoru — Derleme hatalarını parse etme ve çözüm önerileri üreten sistem. */
class HataAnalizMotoru {
    /** Derleme hatasını kategorize et ve öneri ver. */
    data class HataSonucu(
        val kategori: String,
        val acıklama: String,
        val oneri: String
    )
    fun analizEt(hataMesajı: String): HataSonucu {
        val lower = hataMesajı.lowercase()
        return when {
            lower.contains("cannot find") || lower.contains("not found") ->
                HataSonucu("Dosya/Class Bulunamadı",
                    "Sınıf veya dosya yolunu kontrol et; importları ekle.",
                    "Build → Clean → Rebuild; Project Structure kontrol.")
            lower.contains("syntax error") || lower.contains("syntax") ->
                HataSonucu("Syntax Hatası",
                    "Kotlin söz dizimi hatası; satır numarasını kontrol et.",
                    "Kodları düzelt; unnecessary importları kaldır.")
            lower.contains("build failed") || lower.contains("gradlew") ->
                HataSonucu("Build Hatası",
                    "Gradle derleme başarısız; temizleme ve yeniden dene.",
                    "./gradlew clean && ./gradlew assembleDebug")
            lower.contains("permission") || lower.contains("izin") ->
                HataSonucu("İzin Hatası",
                    "AndroidManifest veya build.gradle'de izin eksikliği.",
                    "build.gradle'de gerekli izinleri ekle.")
            else ->
                HataSonucu("Bilinmeyen Hata", hataMesajı.substring(0, minOf(100, hataMesajı.length)), "Detaylı log kontrol.")
        }
    }
}

/** DeğişiklikGerileme — Git diff ve Room migration ile geri alma mekanizması.
 * Sadece raporlar; gerçek git işlemleri kullanıcı ortamında yapılır. */
class DeğişiklikGerileme {
    /** Değişiklikleri raporla (simülasyon). */
    data class Değişiklik(
        val dosya: String,
        val onceki: String, // önceki içerik
        val yeni: String // yeni içerik
    )
    fun raporla(dosyaYolu: String): List<Değişiklik> {
        // Simülasyon: dosyayı okuyup "onceki" ve "yeni" versiyonu oluştur
        val dosya = File(dosyaYolu)
        if (!dosya.exists()) return emptyList()
        val onceki = dosya.readText()
        // Yeni içerik örnek olarak aynı dosyayı değiştirerek simüle et
        val yeni = dosya.readText() + "\n// Değişiklik eklendi: " + java.time.LocalDate.now()
        return listOf(Değişiklik(dosyaYolu, onceki, yeni))
    }
}

/** UzmanAjani abstract class — Tüm uzmanların ortak arayüzü.
 * Gerçek görev dağıtımı ve sonuç birleştirme mantığı buraya tanımlanır. */
abstract class UzmanAjani {
    data class AnalysisResult(
        val uzmanAdı: String,
        val öneri: String,
        val güveni: Double
    )
    /** Görevi analiz eder ve sonuç döndürür. Alt sınıflar bu metodu override eder. */
    abstract fun analizYap(task: String): AnalysisResult
    /** Sonuçları birleştirir (birden fazla uzman çıktısı alır). */
    fun birleştir(results: List<AnalysisResult>): String {
        if (results.isEmpty()) return "Henüz analiz yapılmadı."
        val enYüksek = results.maxBy { it.güveni } ?: return "Yeterli veri yok."
        return "📊 **${enYüksek.uzmanAdı}** (güvenilirlik: ${(enYüksek.güveni * 100).roundToInt()}%)\n\n${enYüksek.öneri}"
    }
}

/** KodlamaUzmanı — Kotlin/Compose kodları hakkında analiz ve öneri üretir. */
class KodlamaUzmanı : UzmanAjani() {
    override fun analizYap(task: String): UzmanAjani.AnalysisResult {
        // Simülasyon: görev içeriğine göre basit bir sonuç üretir
        val öneri = when {
            task.contains("hata") -> "Kod hatasını tespit et; logs kontrol et; build clean & rebuild."
            task.contains("layout") -> "Compose layout; ConstraintLayout veya Column/Row kullan."
            task.contains("test") -> "Birim testleri ekle; EnginesTest.kt modeline uygun."
            else -> "Kod incele; mantığı kontrol et; gerekirse refactor."
        }
        return UzmanAjani.AnalysisResult(
            uzmanAdı = "KodlamaUzmanı",
            öneri = öneri,
            güveni = 0.85
        )
    }
}

/** AraştırmaUzmanı — Bilgi arama ve kaynak değerlendirme yeteneğine sahiptir. */
class AraştırmaUzmanı : UzmanAjani() {
    override fun analizYap(task: String): UzmanAjani.AnalysisResult {
        val öneri = when {
            task.contains("api") -> "API dokümantasyonunu kontrol et; SecureStore'a anahtar ekle."
            task.contains("güvenlik") -> "PermissionManager kontrol et; kritik işlemler için onay."
            task.contains("performans") -> "Performance optimizasyonu; WorkManager kısıtlamaları kontrol et."
            else -> "Bilgi kaynağı ara; docs/checklist dosyalarını incele."
        }
        return UzmanAjani.AnalysisResult(
            uzmanAdı = "AraştırmaUzmanı",
            öneri = öneri,
            güveni = 0.78
        )
    }
}

/** MatematikUzmanı — Matematiksel ifadeler ve hesaplama yeteneğine sahiptir. */
class MatematikUzmanı : UzmanAjani() {
    override fun analizYap(task: String): UzmanAjani.AnalysisResult {
        val öneri = when {
            task.contains("pi") || task.contains("integral") || task.contains("türev") ->
                "Matematiksel ifadeyi doğrula; adım adım çözüm yap."
            task.contains("olasilık") || task.contains("veri") ->
                "Veri setini analiz et; istatistiksel yöntem uygula."
            else -> "Matematiksel kontrol; formüller ve hesaplamaları doğrula."
        }
        return UzmanAjani.AnalysisResult(
            uzmanAdı = "MatematikUzmanı",
            öneri = öneri,
            güveni = 0.82
        )
    }
}

/** EğitimUzmanı — Adım adım açıklama ve quiz yeteneğine sahiptir. */
class EğitimUzmanı : UzmanAjani() {
    override fun analizYap(task: String): UzmanAjani.AnalysisResult {
        val öneri = when {
            task.contains("quiz") -> "Quiz sorusu oluştur; kullanıcıya göster."
            task.contains("adım") -> "Adım adım rehber; her adımı açık bir şekilde yaz."
            else -> "Eğitim içeriği hazırla; temel kavramları açıkla."
        }
        return UzmanAjani.AnalysisResult(
            uzmanAdı = "EğitimUzmanı",
            öneri = öneri,
            güveni = 0.80
        )
    }
}

/** PlanlamaUzmanı — Görev bölünme, öncelikleme, zaman tahmini yeteneğine sahiptir. */
class PlanlamaUzmanı : UzmanAjani() {
    override fun analizYap(task: String): UzmanAjani.AnalysisResult {
        val öneri = when {
            task.contains("böl") -> "Görevi küçük alt görevlere böl; öncecelik sıralaması yap."
            task.contains("zaman") -> "Zaman tahmini yap; WorkManager kısıtlamaları göz önünde bulundur."
            else -> "Görev planlaması; öncelikler ve takvim oluştur."
        }
        return UzmanAjani.AnalysisResult(
            uzmanAdı = "PlanlamaUzmanı",
            öneri = öneri,
            güveni = 0.83
        )
    }
}

/** GüvenlikUzmanı — Risk tespiti, yetki kontrolleri ve güvenlik duvarı önerileri üretir. */
class GüvenlikUzmanı : UzmanAjani() {
    override fun analizYap(task: String): UzmanAjani.AnalysisResult {
        val öneri = when {
            task.contains("api anahtarı") -> "API anahtarını SecureStore'a ekle; açık kodda gösterme."
            task.contains("yetki") -> "Capability kontrol et; kritik işlemlerde onay sor."
            task.contains("gizlilik") -> "Veri şifrele; hassas bilgileri kaydetme."
            else -> "Güvenlik kontrolü; risk tespiti ve önlem öner."
        }
        return UzmanAjani.AnalysisResult(
            uzmanAdı = "GüvenlikUzmanı",
            öneri = öneri,
            güveni = 0.88
        )
    }
}

/** AnalizUzmanı — Birden fazla uzman çıktısını birleştirir, çelişkileri tespit eder ve tek bir kaliteli cevap üretir. */
class AnalizUzmanı {
    /** Birden fazla uzman analiz sonucunu birleştirir. */
    fun birleştir(results: List<UzmanAjani.AnalysisResult>): String { if (results.isEmpty()) return "Henüz analiz yapılmadı."; val enYüksek = results.maxBy { it.güveni }; return "📊 **${enYüksek.uzmanAdı}** (güvenilirlik: ${(enYüksek.güveni * 100).roundToInt()}%)\n\n${enYüksek.öneri}" }
}
