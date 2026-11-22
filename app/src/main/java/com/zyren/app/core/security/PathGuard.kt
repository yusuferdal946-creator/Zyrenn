package com.zyren.app.core.security

import java.io.File
import java.nio.file.Files

/** Çalışma alanı dışına çıkışı engeller (../, mutlak yol, sembolik bağ, önek hilesi). Saf JVM: birim testlenebilir. */
class PathGuard(rootDir: File) {
    val root: File = rootDir.canonicalFile
    private val rootPath = root.path

    fun contains(f: File): Boolean {
        val c = f.canonicalFile.path
        return c == rootPath || c.startsWith(rootPath + File.separator)
    }

    fun resolve(rel: String): File {
        require(!rel.contains('\u0000')) { "Geçersiz yol" }
        val f = File(root, rel).canonicalFile
        require(contains(f)) { "Yol çalışma alanı dışında" }
        return f
    }

    fun relOf(f: File): String =
        f.canonicalFile.path.removePrefix(rootPath).trimStart(File.separatorChar).replace(File.separatorChar, '/')

    /** .zyren/ uygulamaya ayrılmıştır (planlar, otomasyon kuralları vb.); araçlar buraya yazamaz. */
    fun isReserved(f: File): Boolean { val r = relOf(f); return r == ".zyren" || r.startsWith(".zyren/") }

    private fun isLink(f: File) = Files.isSymbolicLink(f.toPath())

    /** Sembolik bağları izlemeden, yalnızca çalışma alanı içindeki dosyaları dolaşır. */
    fun walk(dir: File = root): Sequence<File> =
        dir.walkTopDown().onEnter { !isLink(it) && contains(it) }.filter { !isLink(it) && contains(it) }
}
