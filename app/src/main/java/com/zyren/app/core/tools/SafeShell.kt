package com.zyren.app.core.tools

import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.PathGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Güvenli terminal: İŞLETİM SİSTEMİ KABUĞU DEĞİLDİR, süreç başlatmaz. Yalnızca aşağıdaki yerleşik komutlar,
 * yalnızca PathGuard ile çözülen çalışma alanı yollarında çalışır. Boru, yönlendirme, değişken, komut ikamesi yoktur.
 */
class SafeShell(
    private val guard: PathGuard,
    private val check: suspend (Capability, String) -> Boolean,
    private val trash: (File) -> Unit
) {
    companion object {
        const val HELP = "pwd, echo, ls [-l] [yol], cat, head/tail [-n N] dosya, wc, grep [-i][-n][-r] kalıp [yol], find [yol] [-name kalıp] [-type f|d], mkdir, touch, cp, mv (üzerine yazmaz), rm (çöpe taşır; onay)"
    }

    fun tokenize(cmd: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var q: Char? = null
        var has = false
        for (ch in cmd) {
            if (q != null) { if (ch == q) q = null else sb.append(ch) }
            else if (ch == '\'' || ch == '"') { q = ch; has = true }
            else if (ch.isWhitespace()) { if (has || sb.isNotEmpty()) { out += sb.toString(); sb.clear(); has = false } }
            else if (ch in ";&|<>`\\" || ch == '$') throw IllegalArgumentException("Boru, yönlendirme, değişken ve kaçış karakterleri desteklenmez: '$ch'")
            else sb.append(ch)
        }
        require(q == null) { "Kapanmamış tırnak" }
        if (has || sb.isNotEmpty()) out += sb.toString()
        return out
    }

    suspend fun run(cmd: String): Result<String> {
        val t = try { tokenize(cmd) } catch (e: IllegalArgumentException) { return Result.failure(e) }
        if (t.isEmpty()) return Result.success("")
        val name = t[0]
        val args = t.drop(1)
        val cap: Capability? = when (name) {
            "pwd", "echo", "help" -> null
            "ls", "cat", "head", "tail", "wc", "grep", "find" -> Capability.FILE_READ
            "mkdir", "touch", "cp", "mv" -> Capability.FILE_WRITE
            "rm" -> Capability.FILE_DELETE
            else -> return Result.failure(IllegalArgumentException("Desteklenmeyen komut: $name. Desteklenenler: $HELP"))
        }
        if (cap != null && !check(cap, "Güvenli terminal: $cmd")) return Result.failure(SecurityException("İzin verilmedi: ${cap.label}"))
        return withContext(Dispatchers.IO) { runCatching { exec(name, args) } }
    }

    private fun p(a: String): File {
        val f = guard.resolve(a)
        require(!guard.isReserved(f)) { "Bu yol Zyren'e ayrılmıştır" }
        return f
    }

    private fun text(f: File): String {
        require(f.isFile) { "Dosya bulunamadı: ${guard.relOf(f)}" }
        require(f.length() <= 1_000_000) { "Dosya 1 MB'tan büyük" }
        return f.readText()
    }

    private fun nArg(a: List<String>): Pair<Int, List<String>> {
        var n = 10
        val files = mutableListOf<String>()
        var i = 0
        while (i < a.size) {
            if (a[i] == "-n" && i + 1 < a.size) { n = a[i + 1].toIntOrNull()?.coerceIn(1, 10_000) ?: throw IllegalArgumentException("-n sayı olmalı"); i += 2 }
            else { files += a[i]; i++ }
        }
        require(files.isNotEmpty()) { "dosya gerekli" }
        return n to files
    }

    private fun glob(g: String) = Regex(g.map { when (it) { '*' -> ".*"; '?' -> "."; else -> Regex.escape(it.toString()) } }.joinToString(""))

    private fun exec(name: String, a: List<String>): String = when (name) {
        "help" -> HELP
        "pwd" -> "/"
        "echo" -> a.joinToString(" ")
        "ls" -> {
            val long = a.contains("-l")
            val paths = a.filter { !it.startsWith("-") }.ifEmpty { listOf("") }
            paths.joinToString("\n") { lsOne(p(it), long) }
        }
        "cat" -> { require(a.isNotEmpty()) { "dosya gerekli" }; a.joinToString("\n") { text(p(it)) }.take(60_000) }
        "head" -> { val (n, fs) = nArg(a); fs.joinToString("\n") { text(p(it)).lines().take(n).joinToString("\n") } }
        "tail" -> { val (n, fs) = nArg(a); fs.joinToString("\n") { text(p(it)).lines().takeLast(n).joinToString("\n") } }
        "wc" -> { require(a.isNotEmpty()) { "dosya gerekli" }; a.joinToString("\n") { val s = text(p(it)); "${s.lines().size} satır ${s.split(Regex("\\s+")).count { w -> w.isNotEmpty() }} kelime ${s.toByteArray().size} bayt $it" } }
        "grep" -> grep(a)
        "find" -> find(a)
        "mkdir" -> { require(a.isNotEmpty()) { "yol gerekli" }; a.forEach { val f = p(it); require(f.mkdirs() || f.isDirectory) { "Oluşturulamadı: $it" } }; "tamam" }
        "touch" -> { require(a.isNotEmpty()) { "yol gerekli" }; a.forEach { val f = p(it); f.parentFile?.mkdirs(); if (!f.exists()) f.createNewFile() }; "tamam" }
        "cp" -> {
            require(a.size == 2) { "kullanım: cp kaynak hedef" }
            val s = p(a[0]); val d = p(a[1])
            require(s.isFile) { "Kaynak dosya değil" }; require(!d.exists()) { "Hedef zaten var (üzerine yazılmaz)" }
            d.parentFile?.mkdirs(); s.copyTo(d); "tamam"
        }
        "mv" -> {
            require(a.size == 2) { "kullanım: mv kaynak hedef" }
            val s = p(a[0]); val d = p(a[1])
            require(s.exists()) { "Kaynak yok" }; require(!d.exists()) { "Hedef zaten var (üzerine yazılmaz)" }
            d.parentFile?.mkdirs()
            if (!s.renameTo(d)) { s.copyRecursively(d); s.deleteRecursively() }
            "tamam"
        }
        "rm" -> {
            val targets = a.filter { !it.startsWith("-") }
            require(targets.isNotEmpty()) { "yol gerekli" }
            targets.forEach { val f = p(it); require(f.path != guard.root.path) { "Kök silinemez" }; require(f.exists()) { "Bulunamadı: $it" }; trash(f) }
            "tamam (çöp kutusuna taşındı)"
        }
        else -> throw IllegalArgumentException("Desteklenmeyen komut: $name")
    }

    private fun lsOne(f: File, long: Boolean): String {
        if (f.isFile) return if (long) "f ${f.length()} ${guard.relOf(f)}" else guard.relOf(f)
        return (f.listFiles() ?: emptyArray()).filter { !guard.isReserved(it) }.sortedBy { it.name.lowercase() }.joinToString("\n") {
            (if (long) (if (it.isDirectory) "d - " else "f ${it.length()} ") else "") + it.name + (if (it.isDirectory) "/" else "")
        }
    }

    private fun grep(a: List<String>): String {
        var ic = false; var rec = false
        val rest = mutableListOf<String>()
        for (x in a) if (x.startsWith("-") && x.length > 1 && !x.startsWith("--")) x.drop(1).forEach { c -> if (c == 'i') ic = true else if (c == 'r') rec = true } else rest += x
        require(rest.isNotEmpty()) { "kalıp gerekli" }
        val pat = rest[0]
        val paths = rest.drop(1).ifEmpty { if (rec) listOf("") else throw IllegalArgumentException("dosya gerekli") }
        val out = mutableListOf<String>()
        for (pa in paths) {
            val base = p(pa)
            val files = if (base.isDirectory) { require(rec) { "$pa bir klasör (-r kullanın)" }; guard.walk(base).filter { it.isFile }.toList() } else listOf(base)
            for (f in files) {
                if (guard.isReserved(f) || f.length() > 512_000) continue
                val lines = runCatching { f.readLines() }.getOrNull() ?: continue
                lines.forEachIndexed { i, l -> if (l.contains(pat, ignoreCase = ic) && out.size < 200) out += "${guard.relOf(f)}:${i + 1}:$l" }
            }
        }
        return if (out.isEmpty()) "(eşleşme yok)" else out.joinToString("\n")
    }

    private fun find(a: List<String>): String {
        var path = ""; var name: Regex? = null; var type: String? = null
        var i = 0
        while (i < a.size) {
            when {
                a[i] == "-name" && i + 1 < a.size -> { name = glob(a[i + 1]); i += 2 }
                a[i] == "-type" && i + 1 < a.size -> { type = a[i + 1]; i += 2 }
                a[i].startsWith("-") -> throw IllegalArgumentException("Desteklenmeyen seçenek: ${a[i]}")
                else -> { path = a[i]; i++ }
            }
        }
        val base = p(path)
        return guard.walk(base).filter { !guard.isReserved(it) && it != base }
            .filter { type == null || (type == "f" && it.isFile) || (type == "d" && it.isDirectory) }
            .filter { name == null || name.matches(it.name) }
            .take(300).joinToString("\n") { guard.relOf(it) + if (it.isDirectory) "/" else "" }.ifEmpty { "(sonuç yok)" }
    }
}
