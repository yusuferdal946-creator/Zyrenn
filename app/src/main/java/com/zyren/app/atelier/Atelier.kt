package com.zyren.app.atelier

import com.zyren.app.store.JsonStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.io.File

@Serializable
data class MiniApp(val id: String, val name: String, val description: String, val current: Int, val versions: List<Int>, val updated: Long)

/**
 * Uygulama Atölyesi: kullanıcı ne istediğini söyler, Zyren modelle tek dosyalık HTML uygulama üretir, doğrular, sürümler ve saklar.
 * Üretim: en çok MAX_TRIES deneme; her denemede MiniAppGuard ihlalleri modele geri bildirilir. Hiçbir şey doğrulamadan kaydedilmez.
 * Dosyalar uygulamaya ayrılmış .zyren/ altındadır; araçlar/ajanlar buraya dosya aracıyla erişemez, yalnızca bu sınıf yazar.
 * `ask` modele erişim sağlar (izin ve CHAT_CLOUD denetimi ChatEngine içinde yapılır).
 */
class Atelier(
    private val dir: File,
    private val ask: suspend (system: String, user: String) -> Result<String>
) {
    companion object {
        const val MAX_TRIES = 3
        const val MAX_VERSIONS = 20
        const val MAX_APPS = 50
        const val SYSTEM = "Sen kıdemli bir ön yüz geliştiricisisin. Kullanıcının istediği uygulamayı TEK DOSYALIK HTML olarak yaz.\n" +
            "KURALLAR:\n" +
            "- Yalnızca tek bir ```html kod bloğu döndür; blok dışında kısa bir cümleden fazla yazma.\n" +
            "- Her şey tek dosyada: satır içi <style> ve <script>. Harici dosya, CDN, çerçeve, font, görsel URL'si YOK. Görsel gerekiyorsa CSS/SVG/canvas ile çiz.\n" +
            "- Ağ YOK (fetch/XMLHttpRequest/WebSocket yok), kamera/mikrofon/konum/pano YOK, eval YOK, sayfa yönlendirmesi YOK, <iframe> YOK.\n" +
            "- Düz modern JavaScript (ES2020'ye kadar) kullan. Veriyi kalıcı tutmak için localStorage'ı try/catch içinde kullan.\n" +
            "- Telefon için tasarla: <meta name=viewport content='width=device-width, initial-scale=1'>, dokunmaya uygun büyük düğmeler, taşma yok, prefers-color-scheme ile açık/koyu tema.\n" +
            "- Arayüz Türkçe olsun. Her düğme ve özellik GERÇEKTEN çalışsın; göstermelik buton yok. Boş durumları ve hatalı girişleri ele al.\n" +
            "- İstenen özellik bu kısıtlarla yapılamıyorsa (ör. kamera, internet, telefon kontrolü) uydurma; yapılabilen en yakın çalışan sürümü yaz ve sayfada küçük bir 'Sınırlama' notu göster."
    }

    private val store = JsonStore(File(dir, "index.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    /** Araç/arayüz "bu uygulamayı aç" isteği; arayüz izler ve Atölye sekmesinde açar. */
    val openRequest: MutableStateFlow<String?> = MutableStateFlow(null)
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    fun list(): List<MiniApp> = store.read().mapNotNull { runCatching { json.decodeFromJsonElement(MiniApp.serializer(), it) }.getOrNull() }.sortedByDescending { it.updated }
    fun get(id: String): MiniApp? = list().firstOrNull { it.id == id }
    private fun file(id: String, v: Int) = File(File(dir, id), "v$v.html")
    fun html(id: String, version: Int? = null): String? {
        val a = get(id) ?: return null
        val f = file(id, version ?: a.current)
        return if (f.isFile) f.readText() else null
    }

    private fun save(all: List<MiniApp>) {
        store.write(JsonArray(all.map<MiniApp, JsonElement> { json.encodeToJsonElement(MiniApp.serializer(), it) }))
        _tick.value = _tick.value + 1
    }

    private fun writeAtomic(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp"); tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    /** Modele sor → ayıkla → doğrula; ihlal varsa geri bildirimle yeniden dene. */
    private suspend fun produce(user: String): Result<String> {
        var prompt = user
        var lastProblems: List<String> = emptyList()
        repeat(MAX_TRIES) { attempt ->
            val reply = ask(SYSTEM, prompt).getOrElse { return Result.failure(it) }
            val html = MiniAppGuard.extractHtml(reply)
            if (html == null) {
                lastProblems = listOf("yanıtta ```html bloğu yok")
            } else {
                lastProblems = MiniAppGuard.validate(html)
                if (lastProblems.isEmpty()) return Result.success(html)
            }
            prompt = user + "\n\nÖNCEKİ DENEME REDDEDİLDİ (deneme ${attempt + 1}). Sorunlar:\n- " + lastProblems.joinToString("\n- ") +
                "\nBunları düzelterek TAM dosyayı yeniden yaz."
        }
        return Result.failure(IllegalStateException("Güvenlik/biçim denetimi $MAX_TRIES denemede geçilemedi: " + lastProblems.joinToString("; ")))
    }

    suspend fun create(name: String, description: String, now: Long): Result<MiniApp> {
        if (name.isBlank() || description.isBlank()) return Result.failure(IllegalArgumentException("ad ve açıklama gerekli"))
        if (list().size >= MAX_APPS) return Result.failure(IllegalStateException("En fazla $MAX_APPS uygulama; birini silin"))
        val html = produce("Uygulama adı: ${name.trim()}\nİstek: ${description.trim()}").getOrElse { return Result.failure(it) }
        return lock.withLock {
            val all = list()
            var id = MiniAppGuard.slug(name); var n = 2
            while (all.any { it.id == id }) { id = MiniAppGuard.slug(name) + "-" + n++ }
            writeAtomic(file(id, 1), html)
            val app = MiniApp(id, name.trim().take(60), description.trim().take(500), 1, listOf(1), now)
            save(all + app)
            Result.success(app)
        }
    }

    /** Mevcut sürümü değiştirir (yeni sürüm olarak eklenir; eskisi geri alınabilir). */
    suspend fun revise(id: String, request: String, now: Long): Result<MiniApp> {
        val app = get(id) ?: return Result.failure(IllegalArgumentException("uygulama bulunamadı: $id"))
        val cur = html(id) ?: return Result.failure(IllegalStateException("mevcut sürüm dosyası yok"))
        val html = produce("Aşağıdaki mevcut uygulamayı şu isteğe göre DEĞİŞTİR ve TAM dosyayı döndür. Çalışan kısımları bozma.\n" +
            "İstek: ${request.trim()}\n\nMEVCUT KOD:\n```html\n$cur\n```").getOrElse { return Result.failure(it) }
        return lock.withLock {
            val fresh = get(id) ?: return@withLock Result.failure(IllegalStateException("uygulama bu sırada silindi"))
            val v = (fresh.versions.maxOrNull() ?: 0) + 1
            writeAtomic(file(id, v), html)
            var versions = fresh.versions + v
            while (versions.size > MAX_VERSIONS) { file(id, versions.first()).delete(); versions = versions.drop(1) }
            val upd = fresh.copy(current = v, versions = versions, updated = now)
            save(list().map { if (it.id == id) upd else it })
            Result.success(upd)
        }
    }

    /** Çalışma anı hatasını modele verip düzelttirir. */
    suspend fun fixRuntime(id: String, error: String, now: Long) =
        revise(id, "Uygulama çalışırken şu JavaScript hatasını verdi, nedenini bul ve düzelt: ${error.take(300)}", now)

    suspend fun rollback(id: String, version: Int, now: Long): Result<MiniApp> = lock.withLock {
        val a = get(id) ?: return@withLock Result.failure(IllegalArgumentException("uygulama bulunamadı"))
        if (version !in a.versions || !file(id, version).isFile) return@withLock Result.failure(IllegalArgumentException("sürüm yok: $version (mevcut: ${a.versions.joinToString()})"))
        val upd = a.copy(current = version, updated = now)
        save(list().map { if (it.id == id) upd else it })
        Result.success(upd)
    }

    /** Silme: dosyalar çöp klasörüne taşınır (geri alınabilir), indeksten çıkar. */
    suspend fun delete(id: String, now: Long): Boolean = lock.withLock {
        val a = get(id) ?: return@withLock false
        val src = File(dir, a.id)
        if (src.isDirectory) {
            val trash = File(File(dir.parentFile ?: dir, "miniapps_trash"), "${a.id}-$now").apply { parentFile?.mkdirs() }
            if (!src.renameTo(trash)) src.deleteRecursively()
        }
        save(list().filter { it.id != id })
        true
    }

    fun summary(a: MiniApp) = "${a.id} — ${a.name} (sürüm ${a.current}/${a.versions.maxOrNull() ?: 1}): ${a.description.take(80)}"
}
