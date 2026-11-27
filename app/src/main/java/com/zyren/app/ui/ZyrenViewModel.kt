package com.zyren.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyren.app.ZyrenApp
import com.zyren.app.core.PermissionBridge
import com.zyren.app.core.memory.MemoryEntity
import com.zyren.app.core.model.ProviderPreset
import com.zyren.app.core.model.ProviderPresets
import com.zyren.app.core.res.ResourceInfo
import com.zyren.app.core.security.ApprovalRequest
import com.zyren.app.core.security.Capability
import com.zyren.app.core.workspace.Workspace
import com.zyren.app.voice.VoiceController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import com.zyren.app.core.tools.Args
import com.zyren.app.plan.TaskState
import com.zyren.app.quality.FeatureStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.ZoneId

data class PendingApproval(val req: ApprovalRequest, val result: CompletableDeferred<Boolean>)
data class EditorState(val path: String, val text: String, val versions: List<Long>, val isNew: Boolean)

class ZyrenViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as ZyrenApp).container
    private fun <T> Flow<T>.state(init: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)

    val messages = c.db.chatDao().observeAll(activeConversation).state(emptyList())
    val conversations = c.db.chatDao().observeConversations().state(emptyList())
    val memories = c.memory.all.state(emptyList())
    val audit = c.db.auditDao().observeRecent().state(emptyList())
    val errors = c.db.auditDao().observeErrors().state(emptyList())
    val usage = c.db.auditDao().observeUsage().state(emptyList())
    val enabled = c.permissions.enabled
    val pluginState = c.plugins.state
    val plugins = c.plugins.all()
    val workspacePath: String = c.workspace.root.absolutePath

    val sending = MutableStateFlow(false)
    val progress = MutableStateFlow("")
    /** Aktif sohbet kimliği (çoklu sohbet). 0 = varsayılan tek sohbet. */
    val activeConversation = MutableStateFlow(0L)
    val notice = MutableStateFlow<String?>(null)
    val approval = MutableStateFlow<PendingApproval?>(null)
    val pendingImage = MutableStateFlow<String?>(null)

    val dir = MutableStateFlow("")
    val entries = MutableStateFlow<List<Workspace.Entry>>(emptyList())
    val searchResults = MutableStateFlow<List<Workspace.Entry>?>(null)
    val editor = MutableStateFlow<EditorState?>(null)
    val backupInfo = MutableStateFlow("")
    val terminal = MutableStateFlow<List<String>>(emptyList())

    val providerId = MutableStateFlow(c.settings.providerId)
    val themeMode = MutableStateFlow(c.settings.themeMode)
    val autoFallback = MutableStateFlow(c.settings.autoFallback)
    val humorLevel = MutableStateFlow(c.settings.humorLevel)
    val chatStyle = MutableStateFlow(c.settings.chatStyle)
    val speakReplies = MutableStateFlow(c.settings.speakReplies)
    val conversationMode = MutableStateFlow(c.settings.conversationMode)
    val voiceState = MutableStateFlow("")
    val speechRate = MutableStateFlow(c.settings.speechRate)
    val voiceName = MutableStateFlow(c.settings.voiceName)
    val modelFiles = MutableStateFlow<List<String>>(emptyList())
    val resourceInfo = MutableStateFlow<ResourceInfo?>(null)

    private val voice = VoiceController(app, { text -> send(text, true) }, { voiceState.value = it })

    /** Aynı anda tek onay penceresi (sıraya girer); 120 sn içinde yanıt gelmezse REDDEDİLİR (sonsuz bekleme/üst üste binme yok). */
    private val approvalLock = Mutex()

    init {
        c.permissions.approvalHandler = { req ->
            approvalLock.withLock {
                val d = CompletableDeferred<Boolean>()
                approval.value = PendingApproval(req, d)
                try { withTimeoutOrNull(120_000) { d.await() } ?: false } finally { approval.value = null }
            }
        }
        voice.onSpeakDone = { if (conversationMode.value && !sending.value) listen() }
        refreshFiles(); refreshBackupInfo(); refreshModels()
    }

    override fun onCleared() { voice.shutdown() }

    private fun report(r: Result<*>, ok: String) {
        notice.value = if (r.isSuccess) ok else "Hata: ${r.exceptionOrNull()?.message}"
    }
    fun resolveApproval(ok: Boolean) { approval.value?.result?.complete(ok) }

    // ---- Sohbet ----
    private var sendJob: kotlinx.coroutines.Job? = null
    fun send(text: String, fromVoice: Boolean = false) {
        if (sending.value || text.isBlank()) return
        sendJob = viewModelScope.launch {
            sending.value = true
            val img = pendingImage.value; pendingImage.value = null
            val cid = activeConversation.value
            var reply: String? = null
            try {
                reply = c.engine.send(text, img, cid) { progress.value = it }
            } finally { sending.value = false; progress.value = "" }
            if (reply != null && (fromVoice || speakReplies.value) && c.plugins.isEnabled("voice")) voice.speak(reply)
            else if (fromVoice && conversationMode.value && reply == null) voiceState.value = "Konuşma modu durdu (hata)"
        }
    }
    fun clearChat() = viewModelScope.launch { c.db.chatDao().clear(activeConversation.value) }

    // ---- Çoklu sohbet ----
    /** Yeni bir sohbet başlatır ve ona geçer. */
    fun newConversation() = viewModelScope.launch {
        val cid = System.currentTimeMillis()
        activeConversation.value = cid
    }
    fun selectConversation(cid: Long) { activeConversation.value = cid }
    /** Bir sohbeti ve mesajlarını kalıcı olarak siler. */
    fun deleteConversation(cid: Long) = viewModelScope.launch {
        c.db.chatDao().clear(cid)
        if (activeConversation.value == cid) activeConversation.value = 0L
    }

    // ---- Ses ----
    fun listen() {
        if (!c.plugins.isEnabled("voice")) { notice.value = "Sesli asistan eklentisi kapalı"; return }
        viewModelScope.launch {
            if (PermissionBridge.ensure(getApplication(), android.Manifest.permission.RECORD_AUDIO)) voice.startListening()
            else notice.value = "Mikrofon izni verilmedi"
        }
    }
    fun stopVoice() { voice.stopSpeaking(); voice.stopListening() }
    fun setSpeak(v: Boolean) { c.settings.speakReplies = v; speakReplies.value = v }
    fun setConversation(v: Boolean) { c.settings.conversationMode = v; conversationMode.value = v; if (v) listen() else stopVoice() }

    // ---- Ses ayarları ----
    fun setSpeechRate(v: Float) { c.settings.speechRate = v; speechRate.value = v; voice.setRate(v) }
    fun setVoiceName(name: String?) { c.settings.voiceName = name; voiceName.value = name; voice.setVoice(name) }
    fun previewVoice() { voice.previewVoice() }
    fun voices() = voice.availableVoices().map { it.name }

    // ---- Görsel ekleme (analiz için) ----
    fun attachImage(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        if (!ProviderPresets.byId(providerId.value).supportsImages) {
            notice.value = "Seçili model görsel desteklemiyor. Görsel analizi için Gemini'yi seçin."
            return@launch
        }
        runCatching {
            val cr = getApplication<Application>().contentResolver
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > 1280 || opts.outHeight / sample > 1280) sample *= 2
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: error("Görsel okunamadı")
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.onSuccess { pendingImage.value = it; notice.value = "Görsel eklendi; sorunu yazıp gönder (görsel destekli model gerekir: Gemini)" }
            .onFailure { notice.value = "Hata: ${it.message}" }
    }
    fun dropImage() { pendingImage.value = null }

    /** Kamera önizlemesinden gelen Bitmap'i görsel olarak ekler (analiz için). */
    fun attachImageBitmap(bmp: Bitmap) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.onSuccess { pendingImage.value = it; notice.value = "Görsel eklendi; sorunu yazıp gönder" }
            .onFailure { notice.value = "Hata: ${it.message}" }
    }

    // ---- Hafıza ----
    fun addMemory(cat: String, text: String) = viewModelScope.launch {
        if (!c.permissions.check(Capability.MEMORY_WRITE, "Hafızaya ekle")) { notice.value = "Hafıza yazma izni kapalı"; return@launch }
        notice.value = if (c.memory.add(cat, text)) "Hafızaya eklendi" else "Boş veya zaten kayıtlı"
    }
    fun editMemory(m: MemoryEntity, cat: String, text: String) = viewModelScope.launch { c.memory.edit(m, cat, text) }
    fun pinMemory(m: MemoryEntity) = viewModelScope.launch { c.memory.togglePin(m) }
    fun deleteMemory(m: MemoryEntity) = viewModelScope.launch { c.memory.delete(m) }
    fun clearMemory() = viewModelScope.launch { c.memory.clear(); notice.value = "Hafıza temizlendi" }

    // ---- Dosyalar ----
    private fun child(name: String) = if (dir.value.isEmpty()) name else "${dir.value}/$name"

    fun refreshFiles() = viewModelScope.launch {
        c.workspace.list(dir.value).onSuccess { entries.value = it }.onFailure { notice.value = "Hata: ${it.message}" }
    }
    fun openDir(rel: String) { dir.value = rel; searchResults.value = null; refreshFiles() }
    fun up() = openDir(dir.value.substringBeforeLast('/', ""))

    fun openFile(rel: String) = viewModelScope.launch {
        c.workspace.read(rel)
            .onSuccess { editor.value = EditorState(rel, it, c.workspace.versions(rel), false) }
            .onFailure { notice.value = "Hata: ${it.message}" }
    }
    fun newFile(name: String) { editor.value = EditorState(child(name), "", emptyList(), true) }
    fun closeEditor() { editor.value = null }

    fun saveFile(text: String) = viewModelScope.launch {
        val e = editor.value ?: return@launch
        val r = c.workspace.write(e.path, text)
        report(r, "Kaydedildi")
        if (r.isSuccess) { editor.value = null; refreshFiles(); refreshBackupInfo() }
    }
    fun makeDir(name: String) = viewModelScope.launch { report(c.workspace.mkdir(child(name)), "Klasör oluşturuldu"); refreshFiles() }
    fun deleteEntry(e: Workspace.Entry) = viewModelScope.launch {
        report(c.workspace.delete(e.path), "Çöp kutusuna taşındı"); searchResults.value = null; refreshFiles()
    }
    fun restoreVersion(path: String, v: Long) = viewModelScope.launch {
        val r = c.workspace.restoreVersion(path, v)
        report(r, "Eski sürüme dönüldü")
        if (r.isSuccess) openFile(path)
    }
    fun search(q: String) = viewModelScope.launch {
        c.workspace.search(q).onSuccess { searchResults.value = it }.onFailure { notice.value = "Hata: ${it.message}" }
    }

    private fun displayName(uri: Uri): String =
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "dosya"

    /** Galeri/dosya seçiciden çalışma alanına kopyalar (video düzenleme, kod vb. için). */
    fun importFile(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val stream = app.contentResolver.openInputStream(uri)
        if (stream == null) { notice.value = "Dosya açılamadı"; return@launch }
        val r = c.workspace.importStream(dir.value, displayName(uri), stream)
        notice.value = r.fold({ "İçe aktarıldı: /$it" }, { "Hata: ${it.message}" }); refreshFiles()
    }

    // ---- Yedekleme / taşıma ----
    fun refreshBackupInfo() {
        val n = c.workspace.backupCount()
        backupInfo.value = if (n == 0) "Henüz yedek yok" else "$n yedek · son: ${c.workspace.latestBackup()?.name}"
    }
    fun backupNow() = viewModelScope.launch { report(c.workspace.backup(), "Yedek alındı"); refreshBackupInfo() }
    fun restoreLatestBackup() = viewModelScope.launch {
        val f = c.workspace.latestBackup() ?: run { notice.value = "Yedek yok"; return@launch }
        report(c.workspace.restoreBackup(f), "Yedekten geri yüklendi (öncesinde güvenlik yedeği alındı)")
        dir.value = ""; refreshFiles(); refreshBackupInfo()
    }
    fun exportBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val f = c.workspace.backup().getOrNull()
        if (f == null) { notice.value = "Yedek oluşturulamadı"; return@launch }
        val r = runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                ?: error("Hedef açılamadı")
        }
        report(r, "ZIP dışa aktarıldı (çalışma alanı + hafıza)"); refreshBackupInfo()
    }
    fun exportEncrypted(uri: Uri, pass: String) = viewModelScope.launch(Dispatchers.IO) {
        val ca = pass.toCharArray()
        try {
            val os = getApplication<Application>().contentResolver.openOutputStream(uri)
            if (os == null) { notice.value = "Hedef açılamadı"; return@launch }
            report(os.use { c.workspace.exportEncrypted(it, ca) }, "Şifreli paket dışa aktarıldı. Parolayı kaybederseniz KURTARILAMAZ.")
        } finally { java.util.Arrays.fill(ca, '\u0000') }
        refreshBackupInfo()
    }
    fun importEncrypted(uri: Uri, pass: String) = viewModelScope.launch(Dispatchers.IO) {
        val ca = pass.toCharArray()
        try {
            val s = getApplication<Application>().contentResolver.openInputStream(uri)
            if (s == null) { notice.value = "Dosya açılamadı"; return@launch }
            report(c.workspace.importEncrypted(s, ca), "Şifreli paket içe aktarıldı (çalışma alanı + hafıza birleştirildi)")
        } finally { java.util.Arrays.fill(ca, '\u0000') }
        dir.value = ""; refreshFiles(); refreshBackupInfo()
    }
    fun importPackage(uri: Uri) = viewModelScope.launch {
        val s = getApplication<Application>().contentResolver.openInputStream(uri)
        if (s == null) { notice.value = "Dosya açılamadı"; return@launch }
        report(c.workspace.importPackage(s), "Paket içe aktarıldı (çalışma alanı + hafıza birleştirildi)")
        dir.value = ""; refreshFiles(); refreshBackupInfo()
    }

    // ---- Terminal ----
    fun runShell(cmd: String) = viewModelScope.launch {
        terminal.value = terminal.value + "$ $cmd"
        val r = c.safeShell.run(cmd)
        terminal.value = (terminal.value + r.fold({ it }, { "HATA: ${it.message}" })).takeLast(200)
        if (r.isFailure) c.permissions.note("TERMINAL", cmd, "hata: " + (r.exceptionOrNull()?.message ?: ""))
        refreshFiles()
    }
    fun runRawShell(cmd: String) = viewModelScope.launch {
        terminal.value = terminal.value + "!! (ham kabuk, izole değil) $cmd"
        val r = c.shell.run(cmd)
        terminal.value = (terminal.value + r.fold({ it }, { "HATA: ${it.message}" })).takeLast(200)
        if (r.isFailure) c.permissions.note("TERMINAL_HAM", cmd, "hata: " + (r.exceptionOrNull()?.message ?: ""))
    }
    fun runJs(code: String) = viewModelScope.launch {
        if (!c.permissions.check(Capability.CODE_RUN, "JS: " + code.take(80))) { terminal.value = terminal.value + "HATA: izin kapalı"; return@launch }
        terminal.value = terminal.value + "js> $code"
        val r = withContext(Dispatchers.Default) { c.js.run(code) }
        terminal.value = (terminal.value + r).takeLast(200)
        if (r.startsWith("HATA") || r.contains("sonuç: HATA")) c.permissions.note("JS", code.take(100), "hata: " + r.take(120))
    }
    fun clearTerminal() { terminal.value = emptyList() }

    // ---- Ayarlar / model / kaynak ----
    fun selectProvider(id: String) { c.settings.providerId = id; providerId.value = id }
    fun modelFor(p: ProviderPreset) = c.settings.model(p)
    fun urlFor(p: ProviderPreset) = c.settings.baseUrl(p)
    fun hasKey(p: ProviderPreset) = runCatching { c.secure.hasKey(p.id) }.getOrDefault(false)
    fun saveProvider(p: ProviderPreset, model: String, url: String, key: String) {
        c.settings.save(p, model, url)
        if (key.isNotBlank()) c.secure.setApiKey(p.id, key)
        notice.value = "Ayarlar kaydedildi"
    }
    fun removeKey(p: ProviderPreset) { c.secure.setApiKey(p.id, null); notice.value = "Anahtar silindi" }
    fun setAutoFallback(v: Boolean) { c.settings.autoFallback = v; autoFallback.value = v }
    fun setHumor(v: Int) { c.settings.humorLevel = v; humorLevel.value = c.settings.humorLevel }
    fun setStyle(v: Int) { c.settings.chatStyle = v; chatStyle.value = c.settings.chatStyle }
    fun setThemeMode(v: Int) { c.settings.themeMode = v; themeMode.value = v }
    fun setPermission(cap: Capability, on: Boolean) = c.permissions.setEnabled(cap, on)
    fun setPlugin(id: String, on: Boolean) = c.plugins.setEnabled(id, on)

    fun refreshModels() { modelFiles.value = c.providers.local.modelFiles() }
    fun importModel(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val name = displayName(uri).replace('/', '_')
        val r = runCatching {
            val dest = File(c.providers.local.modelsDir(), name)
            app.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } } ?: error("Dosya açılamadı")
            val p = ProviderPresets.byId(ProviderPresets.MEDIAPIPE)
            c.settings.save(p, name, "")
        }
        report(r, "Model içe aktarıldı: $name"); refreshModels()
    }
    fun deleteModel(name: String) = viewModelScope.launch(Dispatchers.IO) {
        c.providers.local.release(); File(c.providers.local.modelsDir(), name).delete(); refreshModels()
    }
    fun releaseLocalModel() { c.providers.local.release(); notice.value = "Yerel model bellekten bırakıldı" }

    fun refreshResources() = viewModelScope.launch(Dispatchers.Default) { resourceInfo.value = c.resources.snapshot() }
    fun preset() = ProviderPresets.byId(providerId.value)

    // ---- Merkez: plan, bilgi, otomasyon, ajan, simülasyon, ikiz, performans, kalite ----
    val tick = MutableStateFlow(0)
    val lockdown = c.permissions.lockdown
    val onboarded = c.permissions.onboarded
    val simulation = MutableStateFlow(c.settings.simulationMode)
    val automationPaused = MutableStateFlow(c.settings.automationPaused)
    val centerText = MutableStateFlow("")
    val agentOut = MutableStateFlow("")
    private fun bump() { tick.value = tick.value + 1 }
    private fun now() = System.currentTimeMillis()

    fun finishOnboarding(choices: Map<Capability, Boolean>) { choices.forEach { (k, v) -> c.permissions.setEnabled(k, v) }; c.permissions.finishOnboarding() }
    /** ACİL DURDUR: kilit modu + çalışan sohbet/ajan/Atölye üretimi iptal + ses durdur + otomasyon duraklat + bekleyen onay reddi. Kilidi yalnızca kullanıcı açar. */
    fun panic() {
        c.permissions.setLockdown(true)
        setPaused(true)
        sendJob?.cancel(); agentJob?.cancel(); atelierJob?.cancel()
        approval.value?.result?.complete(false)
        stopVoice()
        sending.value = false; atelierBusy.value = false; progress.value = ""
        agentOut.value = "Acil durduruldu."
        notice.value = "⛔ Acil durdurma: her şey durduruldu, kilit modu açık"
        viewModelScope.launch { c.permissions.note("ACIL_DURDUR", "Kullanıcı acil durdurmayı kullandı", "kilit modu + otomasyon duraklatıldı + görevler iptal") }
    }

    // ---- Atölye (mini uygulama üretici) ----
    val atelierApps = MutableStateFlow(c.atelier.list())
    val atelierBusy = MutableStateFlow(false)
    val atelierMsg = MutableStateFlow("")
    val atelierOpen: MutableStateFlow<String?> = c.atelier.openRequest
    private var atelierJob: kotlinx.coroutines.Job? = null
    fun refreshApps() { atelierApps.value = c.atelier.list() }
    fun openMiniApp(id: String) { if (c.atelier.get(id) != null) c.atelier.openRequest.value = id }
    fun miniAppHtml(id: String): String? = c.atelier.html(id)

    private fun runAtelier(label: String, block: suspend () -> Result<com.zyren.app.atelier.MiniApp>) {
        if (atelierBusy.value) return
        atelierJob = viewModelScope.launch {
            atelierBusy.value = true; atelierMsg.value = "$label… (model yazıyor, güvenlik denetimi yapılıyor)"
            val r = try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            atelierBusy.value = false
            r.onSuccess { atelierMsg.value = "Hazır: ${it.name} (sürüm ${it.current})"; refreshApps(); c.atelier.openRequest.value = it.id }
                .onFailure { atelierMsg.value = "HATA: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
    fun createApp(name: String, desc: String) = runAtelier("Uygulama üretiliyor") { c.atelier.create(name, desc, now()) }
    fun reviseApp(id: String, req: String) = runAtelier("Değiştiriliyor") { c.atelier.revise(id, req, now()) }
    fun fixApp(id: String, err: String) = runAtelier("Hata düzeltiliyor") { c.atelier.fixRuntime(id, err, now()) }
    fun rollbackApp(id: String, v: Int) = viewModelScope.launch {
        c.atelier.rollback(id, v, now()).onSuccess { atelierMsg.value = "Sürüm $v'e dönüldü"; refreshApps(); c.atelier.openRequest.value = id }
            .onFailure { atelierMsg.value = "HATA: ${it.message}" }
    }
    fun deleteApp(id: String) = viewModelScope.launch { c.atelier.delete(id, now()); atelierMsg.value = "Silindi (çöpe taşındı)"; refreshApps() }

    fun unlock() { c.permissions.setLockdown(false); notice.value = "Kilit modu kaldırıldı" }
    fun setSimulation(v: Boolean) { c.settings.simulationMode = v; simulation.value = v }
    fun setPaused(v: Boolean) { c.settings.automationPaused = v; automationPaused.value = v }

    fun goals() = c.plans.goals.toList()
    fun addGoal(title: String, horizon: String, lines: String) {
        val r = runCatching {
            val items = lines.lines().filter { it.isNotBlank() }.map { l ->
                val p = l.split(';'); Triple(p[0].trim(), p.getOrNull(1)?.trim()?.toIntOrNull() ?: 5, p.getOrNull(2)?.trim()?.toIntOrNull())
            }
            c.plans.addGoal(title, horizon, items, now())
        }
        report(r, "Hedef eklendi"); bump()
    }
    fun setTask(id: String, st: TaskState) { notice.value = c.plans.setState(id, st, "", "kullanıcı onayı (arayüz)") ?: "Güncellendi"; bump() }
    fun reschedule() { val l = c.plans.reschedule(now()); notice.value = if (l.isEmpty()) "Geciken görev yok" else l.joinToString("; "); bump() }

    fun kbItems(q: String) = (if (q.isBlank()) c.knowledge.latest().sortedByDescending { it.createdAt } else c.knowledge.search(q))
    fun kbAdd(title: String, content: String, url: String) {
        report(runCatching { c.knowledge.add(title, content, url.ifBlank { null }, emptyList(), null, now()) }, "Kaydedildi"); bump()
    }
    fun kbRemove(id: String) { c.knowledge.remove(id); bump() }
    fun kbCompare(id: String): String = c.knowledge.compare(id)
    fun kbLabel(i: com.zyren.app.knowledge.KnowledgeItem) = c.knowledge.label(i)

    fun rules() = c.automation.rules.toList()
    fun autoHistory() = c.automation.history().take(20)
    fun autoAdd(name: String, trigger: String, tool: String, argsJson: String) = viewModelScope.launch {
        if (!c.permissions.check(Capability.AUTOMATION_MANAGE, "Kural: $name · $trigger → $tool")) { notice.value = "Onay verilmedi"; return@launch }
        val r = runCatching { c.automation.add(name, trigger, tool, if (argsJson.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(argsJson).jsonObject) }
        report(r, "Kural eklendi"); bump()
    }
    fun autoToggle(id: String, on: Boolean) { c.automation.setEnabled(id, on); bump() }
    fun autoRemove(id: String) { c.automation.remove(id); bump() }
    fun autoPause(id: String) { c.automation.pause(id); bump() }
    fun autoResume(id: String) { c.automation.resume(id); bump() }
    fun autoCancel(id: String) { c.automation.cancel(id); bump() }
    fun automationSummary(): String = c.automation.statusSummary()
    fun autoRunNow() = viewModelScope.launch {
        val l = c.automation.runDue(now(), ZoneId.systemDefault(), true, { c.tools.riskOf(it) }, { n, a -> c.tools.execute(n, a) })
        notice.value = if (l.isEmpty()) "Zamanı gelen kural yok" else l.joinToString("; "); bump()
    }

    fun twinUpdate() = viewModelScope.launch(Dispatchers.Default) {
        val s = c.twin.snapshot(now()); c.twin.save(s); centerText.value = "İkiz güncellendi.\n" + c.twin.summary()
    }
    fun twinShow() { centerText.value = c.twin.summary() }
    fun twinDiff() { centerText.value = c.twin.diffLast() }
    fun perfReport() = viewModelScope.launch(Dispatchers.Default) {
        c.perfSample()
        centerText.value = c.perf.report(c.db.auditDao().errorsSince(now() - 86_400_000L).map { "${it.capability}: ${it.outcome}" })
    }
    fun runTests() = viewModelScope.launch(Dispatchers.Default) { centerText.value = "Testler çalışıyor…"; centerText.value = c.runSelfTests(); bump() }
    fun features() = c.features.features.toList()
    fun featureSet(id: String, st: FeatureStatus, note: String) { notice.value = c.features.userSet(id, st, note, now()) ?: "Güncellendi"; bump() }
    fun simulate(name: String, argsJson: String): String = runCatching {
        val a = Args(Json.parseToJsonElement(argsJson.ifBlank { "{}" }).jsonObject)
        c.tools.simulate(name.trim(), a, c.twinHint(name.trim(), a))
    }.getOrElse { "HATA: ${it.message}" }
    fun customTools() = c.customTools.tools.toList()
    fun removeCustom(name: String) { c.customTools.remove(name); c.tools.remove(name); bump() }
    fun toolDocs(): String = c.tools.doc()
    private var agentJob: kotlinx.coroutines.Job? = null
    fun runAgent(role: String, task: String) {
        agentJob?.cancel()
        agentJob = viewModelScope.launch { agentOut.value = "Çalışıyor…"; agentOut.value = c.engine.runAgent(role, task, activeConversation.value) }
    }
    fun runTask(id: String) {
        val t = c.plans.find(id)?.second ?: return
        val role = com.zyren.app.agents.AgentRoles.guess(t.title)
        agentJob?.cancel()
        agentJob = viewModelScope.launch {
            agentOut.value = "Görev yürütülüyor ($role)…"; notice.value = "Ajan ($role) görevi yürütüyor; Ajanlar bölümünden izleyip durdurabilirsiniz"
            val out = c.engine.runAgent(role, "Görev: ${t.title}. Bitince ne yaptığını ve nasıl doğruladığını kısaca raporla.")
            c.plans.setState(id, if (out.startsWith("HATA")) TaskState.BASARISIZ else TaskState.DEVAM, "[ajan:$role] " + out.take(200), null)
            agentOut.value = out; bump()
        }
    }
    fun stopAgent() { agentJob?.cancel(); agentOut.value = "Durduruldu."; notice.value = "Ajan durduruldu" }
}
