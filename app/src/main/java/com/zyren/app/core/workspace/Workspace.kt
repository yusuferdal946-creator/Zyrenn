package com.zyren.app.core.workspace

import android.content.Context
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.PathGuard
import com.zyren.app.core.security.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zyren'in kendi çalışma alanı (uygulamanın özel dizini). Tüm yollar PathGuard'dan geçer (../, mutlak yol, sembolik bağ engelli).
 * Üzerine yazma -> eski sürüm saklanır + onay; silme -> çöp kutusu + onay; yedek geri yükleme -> güvenlik yedeği + geçici dizinde
 * doğrulayıp değiştirme (yarıda hata olursa çalışma alanı bozulmaz). `.zyren/` uygulamaya ayrılmıştır, araçlar yazamaz.
 */
class Workspace(context: Context, private val perms: PermissionManager) {
    data class Entry(val name: String, val path: String, val isDir: Boolean, val size: Long, val modified: Long)

    val root: File = File(context.filesDir, "workspace").apply { mkdirs() }
    val guard = PathGuard(root)
    private val versionsDir = File(context.filesDir, "versions").apply { mkdirs() }
    private val trashDir = File(context.filesDir, "trash").apply { mkdirs() }
    private val backupsDir = File(context.filesDir, "backups").apply { mkdirs() }
    private val stagingDir = File(context.filesDir, "staging")
    private val manifest = ".zyren-yedek"
    private val maxImport = 1024L * 1024 * 1024

    var preBackup: (suspend () -> Unit)? = null
    var postRestore: (suspend () -> Unit)? = null

    private fun resolve(rel: String): File = guard.resolve(rel)
    private fun relOf(f: File) = guard.relOf(f)
    private fun toEntry(f: File) = Entry(f.name, relOf(f), f.isDirectory, if (f.isFile) f.length() else 0, f.lastModified())
    private fun notReserved(f: File) = require(!guard.isReserved(f)) { "Bu yol Zyren'e ayrılmıştır" }
    private fun uniqueName(f0: File): File {
        var f = f0; var i = 1
        while (f.exists()) { f = File(f0.parentFile, f0.nameWithoutExtension + "-" + i + (if (f0.extension.isEmpty()) "" else "." + f0.extension)); i++ }
        return f
    }

    fun fileFor(rel: String): File = resolve(rel)

    private suspend fun <T> guarded(cap: Capability, detail: String, block: () -> T): Result<T> {
        if (!perms.check(cap, detail)) return Result.failure(SecurityException("İzin verilmedi: ${cap.label}"))
        return withContext(Dispatchers.IO) { runCatching(block) }
    }

    suspend fun list(rel: String = ""): Result<List<Entry>> = guarded(Capability.FILE_READ, "Listele: /$rel") {
        resolve(rel).listFiles()
            ?.filter { !it.name.startsWith(".zyren") }
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { toEntry(it) }.orEmpty()
    }

    suspend fun read(rel: String): Result<String> = guarded(Capability.FILE_READ, "Oku: /$rel") {
        val f = resolve(rel)
        notReserved(f)
        require(f.isFile) { "Dosya bulunamadı" }
        require(f.length() <= 1_000_000) { "Dosya 1 MB'tan büyük; düzenleyicide açılmaz" }
        f.readText()
    }

    suspend fun write(rel: String, text: String): Result<Unit> {
        val f = runCatching { resolve(rel) }.getOrElse { return Result.failure(it) }
        if (f.isDirectory) return Result.failure(IllegalArgumentException("Bu bir klasör"))
        if (guard.isReserved(f)) return Result.failure(SecurityException("Bu yol Zyren'e ayrılmıştır"))
        val overwrite = f.exists()
        val cap = if (overwrite) Capability.FILE_OVERWRITE else Capability.FILE_WRITE
        return guarded(cap, (if (overwrite) "Üzerine yaz: /" else "Oluştur: /") + relOf(f)) {
            if (overwrite) snapshot(f)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".zyren-tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
        }
    }

    suspend fun writeNew(rel: String, bytes: ByteArray): Result<String> {
        val f0 = runCatching { resolve(rel) }.getOrElse { return Result.failure(it) }
        return guarded(Capability.FILE_WRITE, "Oluştur: /${relOf(f0)}") {
            notReserved(f0)
            val f = uniqueName(f0)
            f.parentFile?.mkdirs(); f.writeBytes(bytes); relOf(f)
        }
    }

    suspend fun importStream(dirRel: String, name: String, input: InputStream): Result<String> {
        val safeName = name.replace('/', '_').replace('\\', '_').ifBlank { "dosya" }
        val f0 = runCatching { resolve(if (dirRel.isEmpty()) safeName else "$dirRel/$safeName") }.getOrElse { return Result.failure(it) }
        return guarded(Capability.FILE_WRITE, "İçe aktar: /${relOf(f0)}") {
            notReserved(f0)
            val f = uniqueName(f0)
            f.parentFile?.mkdirs()
            input.use { src -> f.outputStream().use { ZipSafe.copyLimited(src, it, maxImport) } }
            relOf(f)
        }
    }

    suspend fun mkdir(rel: String): Result<Unit> = guarded(Capability.FILE_WRITE, "Klasör oluştur: /$rel") {
        val f = resolve(rel); notReserved(f)
        require(f.mkdirs()) { "Klasör oluşturulamadı (zaten var olabilir)" }
    }

    /** Dosya/klasörü çöp kutusuna taşır (izin denetimi çağıranda). */
    fun trashFile(f: File) {
        val dest = File(trashDir, "${System.currentTimeMillis()}__${relOf(f).replace('/', '_')}")
        if (!f.renameTo(dest)) { f.copyRecursively(dest); f.deleteRecursively() }
    }

    suspend fun delete(rel: String): Result<Unit> = guarded(Capability.FILE_DELETE, "Sil (çöpe taşı): /$rel") {
        val f = resolve(rel)
        notReserved(f)
        require(f.path != guard.root.path) { "Çalışma alanı kökü silinemez" }
        require(f.exists()) { "Bulunamadı" }
        trashFile(f)
    }

    suspend fun search(query: String): Result<List<Entry>> = guarded(Capability.FILE_READ, "Ara: $query") {
        val q = query.trim().lowercase()
        val textExt = setOf("txt", "md", "kt", "java", "py", "js", "json", "xml", "html", "css", "csv", "yml", "yaml", "kts", "gradle", "sh", "srt")
        guard.walk().filter { it.isFile && !guard.isReserved(it) }.filter { f ->
            f.name.lowercase().contains(q) ||
                (f.extension.lowercase() in textExt && f.length() < 512_000 && runCatching { f.readText().lowercase().contains(q) }.getOrDefault(false))
        }.take(50).map { toEntry(it) }.toList()
    }

    // ---- Sürüm geçmişi ----
    private fun snapshot(f: File) {
        val dir = File(versionsDir, relOf(f)).apply { mkdirs() }
        f.copyTo(File(dir, System.currentTimeMillis().toString()), overwrite = true)
        dir.listFiles()?.sortedByDescending { it.name }?.drop(10)?.forEach { it.delete() }
    }

    fun versions(rel: String): List<Long> = runCatching {
        File(versionsDir, relOf(resolve(rel))).listFiles()?.mapNotNull { it.name.toLongOrNull() }?.sortedDescending().orEmpty()
    }.getOrDefault(emptyList())

    suspend fun restoreVersion(rel: String, version: Long): Result<Unit> =
        guarded(Capability.BACKUP_RESTORE, "Eski sürüme dön: /$rel") {
            val f = resolve(rel); notReserved(f)
            val v = File(File(versionsDir, relOf(f)), version.toString())
            require(v.isFile) { "Sürüm bulunamadı" }
            if (f.exists()) snapshot(f)
            v.copyTo(f, overwrite = true)
        }

    // ---- Yedekleme / taşıma ----
    private fun backupName(suffix: String = "") =
        "zyren-yedek-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + suffix + ".zip"

    private fun makeBackup(prune: Boolean = true): File {
        val out = File(backupsDir, backupName())
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(manifest)); zip.write("zyren".toByteArray()); zip.closeEntry()
            guard.walk().filter { it.isFile }.forEach { f ->
                zip.putNextEntry(ZipEntry(relOf(f)))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        if (prune) backupsDir.listFiles()?.filter { it.name.endsWith(".zip") }?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
        return out
    }

    suspend fun backup(): Result<File> {
        runCatching { preBackup?.invoke() }
        return guarded(Capability.FILE_READ, "Çalışma alanını yedekle") { makeBackup() }
    }
    fun latestBackup(): File? = backupsDir.listFiles()?.filter { it.name.endsWith(".zip") }?.maxByOrNull { it.name }
    fun backupCount(): Int = backupsDir.listFiles()?.count { it.name.endsWith(".zip") } ?: 0

    /** Başka cihazdan alınan ZIP: boyut sınırıyla kopyalanır, sonra normal (onaylı, doğrulamalı) geri yükleme akışı. */
    suspend fun importPackage(input: InputStream): Result<Unit> {
        val bin = input.buffered()
        val head = ByteArray(16)
        bin.mark(16); val n = bin.read(head); bin.reset()
        if (n > 0 && BackupCrypto.isEncrypted(head, n)) { bin.close(); return Result.failure(IllegalArgumentException("Bu paket ŞİFRELİ: 'Şifreli paket içe aktar' ile parolayı girin")) }
        val tmp = File(backupsDir, "zyren-ice-aktarilan.tmp")
        val copied = withContext(Dispatchers.IO) { runCatching { bin.use { s -> tmp.outputStream().use { ZipSafe.copyLimited(s, it, maxImport) } } } }
        return finishImport(tmp, copied)
    }

    private suspend fun finishImport(tmp: File, copied: Result<*>): Result<Unit> {
        if (copied.isFailure) { tmp.delete(); return Result.failure(copied.exceptionOrNull()!!) }
        val r = restoreBackup(tmp)
        if (r.isSuccess) tmp.renameTo(File(backupsDir, backupName("-ice"))) else tmp.delete()
        return r
    }

    /** Şifreli dışa aktarma: yedek ZIP'i AES-256-GCM ile şifrelenip `out`a yazılır. Parola saklanmaz. */
    suspend fun exportEncrypted(out: java.io.OutputStream, pass: CharArray): Result<Unit> {
        BackupCrypto.checkPassphrase(pass)?.let { return Result.failure(IllegalArgumentException(it)) }
        val zip = backup().getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) { runCatching { zip.inputStream().buffered().use { BackupCrypto.encrypt(it, out, pass) } } }
    }

    /** Şifreli paketi çözer (boyut sınırlı, geçici dosyaya), sonra normal onaylı geri yükleme akışına sokar. */
    suspend fun importEncrypted(input: InputStream, pass: CharArray): Result<Unit> {
        val tmp = File(backupsDir, "zyren-ice-aktarilan.tmp")
        val dec = withContext(Dispatchers.IO) {
            runCatching { tmp.outputStream().buffered().use { os -> input.buffered().use { BackupCrypto.decrypt(it, LimitedOutputStream(os, maxImport), pass) } } }
        }
        return finishImport(tmp, dec)
    }

    /**
     * 1) onay  2) güvenlik yedeği  3) ZIP'i GEÇİCİ dizine, sınırlarla ve yol doğrulamasıyla aç  4) ancak başarılıysa çalışma alanını değiştir.
     * Üçüncü adım başarısızsa çalışma alanına dokunulmaz.
     */
    suspend fun restoreBackup(zip: File): Result<Unit> {
        val r = guarded(Capability.BACKUP_RESTORE, "Yedekten geri yükle: ${zip.name}") {
            makeBackup(prune = false)
            stagingDir.deleteRecursively(); stagingDir.mkdirs()
            try {
                ZipSafe.extract(zip, stagingDir, skipName = manifest)
                root.listFiles()?.forEach { it.deleteRecursively() }
                stagingDir.listFiles()?.forEach { c -> if (!c.renameTo(File(root, c.name))) { c.copyRecursively(File(root, c.name)); c.deleteRecursively() } }
            } finally { stagingDir.deleteRecursively() }
            Unit
        }
        if (r.isSuccess) runCatching { postRestore?.invoke() }
        return r
    }
}
