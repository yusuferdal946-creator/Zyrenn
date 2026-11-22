package com.zyren.app.core.workspace

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/** ZIP çıkarma: yol kaçışı (zip-slip), girdi sayısı ve boyut (zip bombası) sınırları. */
object ZipSafe {
    class Limits(val maxEntries: Int = 20_000, val maxTotal: Long = 800L * 1024 * 1024, val maxEntry: Long = 300L * 1024 * 1024)

    fun isSafeName(n: String): Boolean =
        n.isNotEmpty() && !n.startsWith("/") && !n.contains('\\') && !n.contains(':') &&
            !n.contains('\u0000') && n.split('/').none { it == ".." }

    fun extract(zip: File, dest: File, skipName: String? = null, limits: Limits = Limits()) {
        dest.mkdirs()
        val destCanon = dest.canonicalFile
        var total = 0L
        var count = 0
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                count++
                require(count <= limits.maxEntries) { "ZIP çok fazla girdi içeriyor" }
                require(isSafeName(e.name)) { "Güvenli olmayan ZIP girdisi: ${e.name}" }
                if (!e.isDirectory && e.name != skipName) {
                    val out = File(destCanon, e.name).canonicalFile
                    require(out.path.startsWith(destCanon.path + File.separator)) { "ZIP yol kaçışı engellendi" }
                    out.parentFile?.mkdirs()
                    var entryBytes = 0L
                    out.outputStream().use { os ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = zin.read(buf)
                            if (n < 0) break
                            entryBytes += n; total += n
                            require(entryBytes <= limits.maxEntry && total <= limits.maxTotal) { "ZIP boyut sınırı aşıldı (zip bombası olabilir)" }
                            os.write(buf, 0, n)
                        }
                    }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
    }

    fun copyLimited(input: InputStream, out: OutputStream, max: Long): Long {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            require(total <= max) { "Dosya boyut sınırını aştı" }
            out.write(buf, 0, n)
        }
        return total
    }
}
