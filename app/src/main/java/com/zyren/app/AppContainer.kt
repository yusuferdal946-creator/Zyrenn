package com.zyren.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zyren.app.automation.AutomationEngine
import com.zyren.app.automation.AutomationWorker
import com.zyren.app.core.ChatEngine
import com.zyren.app.core.QuickCommands
import com.zyren.app.core.memory.MemoryRepository
import com.zyren.app.core.memory.ZyrenDatabase
import com.zyren.app.core.model.ProviderRegistry
import com.zyren.app.core.plugin.BuiltinPlugin
import com.zyren.app.core.plugin.PluginRegistry
import com.zyren.app.core.res.ResourceMonitor
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.PermissionManager
import com.zyren.app.core.security.SecureStore
import com.zyren.app.core.security.Settings
import com.zyren.app.core.tools.Args
import com.zyren.app.core.tools.CustomTools
import com.zyren.app.core.tools.JsSandbox
import com.zyren.app.core.tools.SafeShell
import com.zyren.app.core.tools.ShellRunner
import com.zyren.app.core.tools.ToolRegistry
import com.zyren.app.core.tools.WebTools
import com.zyren.app.core.tools.registerBuiltinTools
import com.zyren.app.core.tools.registerFeatureTools
import com.zyren.app.core.tools.registerAtelierTools
import com.zyren.app.core.tools.registerOpenSourceTools
import com.zyren.app.atelier.Atelier
import com.zyren.app.core.workspace.Workspace
import com.zyren.app.knowledge.KnowledgeHub
import com.zyren.app.monitor.PerfMonitor
import com.zyren.app.monitor.Sample
import com.zyren.app.phone.PhoneController
import com.zyren.app.plan.PlanEngine
import com.zyren.app.quality.FeatureRegistry
import com.zyren.app.quality.FeatureStatus
import com.zyren.app.quality.SelfTest
import com.zyren.app.store.JsonStore
import com.zyren.app.twin.TwinService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Tüm modüller tek yerde bağlanır: tek Zyren sistemi. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val db = Room.databaseBuilder(context, ZyrenDatabase::class.java, "zyren.db").build()
    val secure by lazy { SecureStore(context) }
    val settings = Settings(context)
    val permissions = PermissionManager(context, db.auditDao())
    val workspace = Workspace(context, permissions)
    val memory = MemoryRepository(db.memoryDao())
    val resources = ResourceMonitor(context)
    val providers = ProviderRegistry(context, resources)
    val phone = PhoneController(context)
    val shell = ShellRunner(workspace, permissions)
    val js = JsSandbox()
    val safeShell = SafeShell(workspace.guard, { cap, d -> permissions.check(cap, d) }, { f -> workspace.trashFile(f) })
    val web = WebTools()

    private val zdir = File(workspace.root, ".zyren")
    val perf = PerfMonitor(JsonStore(File(zdir, "perf.json")))
    val plans = PlanEngine(JsonStore(File(zdir, "plans.json")))
    val knowledge = KnowledgeHub(JsonStore(File(zdir, "knowledge.json")))
    val automation = AutomationEngine(JsonStore(File(zdir, "automation.json")), JsonStore(File(zdir, "automation_history.json")))
    val features = FeatureRegistry(JsonStore(File(zdir, "features.json")))
    val twin = TwinService(appContext, resources, JsonStore(File(zdir, "twin.json")))
    val atelier = Atelier(File(zdir, "miniapps")) { sys, usr -> engine.generate(sys, usr) }
    val customTools = CustomTools(JsonStore(File(zdir, "custom_tools.json")), js)

    val plugins = PluginRegistry(context).apply {
        register(BuiltinPlugin("files", "Dosya yönetimi", "Sandbox, sürüm geçmişi, yedek, taşıma",
            setOf(Capability.FILE_READ, Capability.FILE_WRITE, Capability.FILE_OVERWRITE, Capability.FILE_DELETE, Capability.BACKUP_RESTORE)))
        register(BuiltinPlugin("code", "Kodlama", "Güvenli terminal, JS kum havuzu, (kapalı) ham kabuk", setOf(Capability.SHELL_SAFE, Capability.SHELL_ANY, Capability.CODE_RUN)))
        register(BuiltinPlugin("memory", "Hafıza araçları", "Hafıza yazma, bakım, hata/çözüm kaydı", setOf(Capability.MEMORY_WRITE)))
        register(BuiltinPlugin("research", "Araştırma", "Web arama ve sayfa okuma", setOf(Capability.WEB)))
        register(BuiltinPlugin("phone", "Telefon yönetimi", "Uygulama, ayar, alarm, kişi, SMS, arama, bildirim, ekran",
            setOf(Capability.PHONE_INFO, Capability.APP_LAUNCH, Capability.SETTINGS_CHANGE, Capability.ALARM_CALENDAR, Capability.CONTACTS_READ,
                Capability.CALLLOG_READ, Capability.NOTIF_READ, Capability.MESSAGE_APP, Capability.SMS_SEND, Capability.CALL_PHONE, Capability.ACCESSIBILITY)))
        register(BuiltinPlugin("vision", "Görsel", "Görsel analizi ve üretme", setOf(Capability.WEB, Capability.CHAT_CLOUD)))
        register(BuiltinPlugin("media", "Video/medya", "MP4 kırpma/birleştirme, .srt", setOf(Capability.MEDIA_EDIT)))
        register(BuiltinPlugin("voice", "Sesli asistan", "Türkçe konuşma tanıma ve yanıt", emptySet()))
        register(BuiltinPlugin("plan", "Akıllı planlama", "Hedef/görev, öncelik, yeniden planlama", setOf(Capability.PLAN_WRITE)))
        register(BuiltinPlugin("knowledge", "Bilgi merkezi", "Araştırma arşivi, sürüm karşılaştırma", setOf(Capability.KNOWLEDGE_WRITE, Capability.FILE_READ)))
        register(BuiltinPlugin("automation", "Güvenli otomasyon", "Zamanlı kurallar (arka planda yalnızca güvenli araçlar)", setOf(Capability.AUTOMATION_MANAGE, Capability.AUTOMATION_RUN)))
        register(BuiltinPlugin("monitor", "Hata ve performans merkezi", "RAM/CPU/pil/depolama izleme, rapor", setOf(Capability.PHONE_INFO)))
        register(BuiltinPlugin("twin", "Dijital ikiz", "Telefon durumu anlık görüntüsü ve fark", setOf(Capability.PHONE_INFO)))
        register(BuiltinPlugin("quality", "Kalite kapısı", "Özellik durumu, kendi kendine test", setOf(Capability.FILE_READ)))
        register(BuiltinPlugin("lab", "Simülasyon laboratuvarı", "Gerçek işlem yapmadan deneme", emptySet()))
        register(BuiltinPlugin("atelier", "Uygulama atölyesi", "İstediğiniz mini uygulamayı üretir, sürümler, kum havuzunda çalıştırır", setOf(Capability.APP_BUILD, Capability.APP_DELETE, Capability.CHAT_CLOUD)))
        register(BuiltinPlugin("agents", "Uzman ajanlar", "Rol bazlı alt ajanlar", setOf(Capability.AGENT_RUN)))
        register(BuiltinPlugin("selftools", "Özel araçlar", "Kullanıcı onaylı saf-JS araçlar", setOf(Capability.TOOL_CREATE, Capability.CODE_RUN)))
    }
    val tools = ToolRegistry(permissions, plugins).also {
        registerBuiltinTools(it, workspace, safeShell, shell, js, web, phone, memory)
    }
    val quick = QuickCommands(tools)

    fun twinHint(name: String, args: Args): String? = twin.hint(name, args)
    val engine = ChatEngine(settings, secure, providers, memory, db.chatDao(), permissions, tools, quick, resources, perf) { n, a -> twinHint(n, a) }

    fun perfSample() {
        val r = resources.snapshot()
        perf.add(Sample(System.currentTimeMillis(), r.ramAvailMb, r.ramTotalMb, r.batteryPct, r.charging, r.storageFreeGb, r.appCpuPct))
    }

    suspend fun runSelfTests(): String {
        val rs = SelfTest.run(this)
        val now = System.currentTimeMillis()
        rs.groupBy { it.feature }.forEach { (f, l) ->
            features.recordAuto(f, l.all { it.passed }, l.joinToString("; ") { (if (it.passed) "✓" else "✗") + it.name + (if (it.passed) "" else "(${it.detail})") }, now)
        }
        rs.filter { !it.passed }.forEach {
            memory.add(MemoryRepository.ERR, "Kendi kendine test başarısız [${it.name}]: ${it.detail}")
            permissions.note("SELFTEST", it.name, "hata: ${it.detail}")
        }
        return "Kendi kendine test (bu cihazda, ${rs.size} durum): ${rs.count { it.passed }} geçti, ${rs.count { !it.passed }} başarısız\n" +
            rs.joinToString("\n") { (if (it.passed) "✓ " else "✗ ") + it.feature + "/" + it.name + (if (it.passed) "" else " — ${it.detail}") } +
            "\n\nNot: bu testler özelliği 'cihazda test edildi' yapmaz; bu durumu yalnızca siz Merkez > Kalite'den onaylayabilirsiniz."
    }

    private fun seedFeatures() {
        val k = FeatureStatus.KOD_YAZILDI
        listOf(
            "sohbet_model" to "Ana model sistemi (bulut)", "yerel_model" to "Yerel model (MediaPipe)", "arac_dongusu" to "Araç döngüsü", "hafiza" to "Kalıcı hafıza",
            "yasayan_hafiza" to "Yaşayan hafıza (bakım)", "sandbox" to "Sandbox / güvenli dosya erişimi", "terminal" to "Güvenli terminal", "zip" to "ZIP yedek/geri yükleme",
            "js_sandbox" to "JS kum havuzu", "api_anahtar" to "Şifreli API anahtarı", "izin_yonetimi" to "Merkezi izin + şüpheli işlem + kilit", "telefon" to "Telefon kontrolü",
            "ses" to "Sesli asistan", "arastirma" to "Araştırma", "gorsel_medya" to "Görsel/tasarım/medya", "planlama" to "Akıllı planlama", "bilgi_merkezi" to "Bilgi merkezi",
            "otomasyon" to "Güvenli otomasyon (WorkManager)", "performans" to "Hata/performans merkezi", "ikiz" to "Telefonun dijital ikizi", "simulasyon" to "Simülasyon laboratuvarı",
            "ajanlar" to "Çoklu uzman ajanlar", "ozel_araclar" to "Kendi araçlarını geliştirme", "merkez_3d" to "3D kontrol merkezi", "kalite_kapisi" to "Kalite kapısı / kendi testleri"
        ).forEach { features.ensure(it.first, it.second, k) }
        features.ensure("telefon_pc", "Telefon–PC bağlantısı (yalnızca ZIP taşıma var)", FeatureStatus.PLANLANDI)
        features.ensure("litert_gecisi", "LiteRT-LM geçişi (MediaPipe bakım modunda)", FeatureStatus.PLANLANDI)
        features.ensure("otonom_merkez", "Otonom görev merkezi (plan görevini ajanla yürütme; sürekli otonomi YOK)", k)
    }

    init {
        seedFeatures()
        // Cihazlar arası taşıma: yedeğe hafıza anlık görüntüsü ekle / geri yüklemede birleştir ve bellekteki durumu diskle eşitle
        workspace.preBackup = { withContext(Dispatchers.IO) { memory.snapshotTo(File(zdir, "memory.json")) } }
        workspace.postRestore = {
            memory.mergeFrom(File(zdir, "memory.json"))
            plans.reload(); knowledge.reload(); automation.reload(); features.reload(); perf.reload(); customTools.reload()
            seedFeatures()
        }
        registerFeatureTools(this)
        registerAtelierTools(this)
        registerOpenSourceTools(this)
        customTools.tools.forEach { customTools.register(tools, it) }
    }
}

class ZyrenApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Arka plan: en sık 15 dk; yeniden başlatmada kalıcı. İşçi kendi içinde izin/duraklatma/kilit denetler.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "zyren-automation", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutomationWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
