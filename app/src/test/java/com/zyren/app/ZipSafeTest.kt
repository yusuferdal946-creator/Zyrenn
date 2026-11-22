package com.zyren.app

import com.zyren.app.core.workspace.ZipSafe
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipSafeTest {
    private fun zip(vararg e: Pair<String, ByteArray>): File {
        val f = File.createTempFile("zyren", ".zip")
        ZipOutputStream(f.outputStream()).use { z -> e.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return f
    }
    private fun dest() = Files.createTempDirectory("zd").toFile()

    @Test fun normalExtract() {
        val d = dest(); ZipSafe.extract(zip("a/b.txt" to "hi".toByteArray()), d)
        assertEquals("hi", File(d, "a/b.txt").readText())
    }
    @Test fun zipSlipRejected() {
        val d = dest()
        assertThrows(IllegalArgumentException::class.java) { ZipSafe.extract(zip("../evil.txt" to "x".toByteArray()), d) }
        assertFalse(File(d.parentFile, "evil.txt").exists())
    }
    @Test fun absoluteAndBackslashRejected() {
        assertThrows(IllegalArgumentException::class.java) { ZipSafe.extract(zip("/abs.txt" to ByteArray(1)), dest()) }
        assertThrows(IllegalArgumentException::class.java) { ZipSafe.extract(zip("a\\b.txt" to ByteArray(1)), dest()) }
        assertFalse(ZipSafe.isSafeName("C:/x")); assertTrue(ZipSafe.isSafeName("ok/dir/file.txt"))
    }
    @Test fun sizeLimit() {
        assertThrows(IllegalArgumentException::class.java) { ZipSafe.extract(zip("a" to ByteArray(5000)), dest(), limits = ZipSafe.Limits(maxTotal = 1000)) }
        assertThrows(IllegalArgumentException::class.java) { ZipSafe.extract(zip("a" to ByteArray(5000)), dest(), limits = ZipSafe.Limits(maxEntry = 1000)) }
    }
    @Test fun entryCountLimit() {
        val es = (1..5).map { "f$it" to ByteArray(1) }.toTypedArray()
        assertThrows(IllegalArgumentException::class.java) { ZipSafe.extract(zip(*es), dest(), limits = ZipSafe.Limits(maxEntries = 3)) }
    }
    @Test fun skipManifest() {
        val d = dest(); ZipSafe.extract(zip(".zyren-yedek" to ByteArray(1), "a.txt" to ByteArray(1)), d, skipName = ".zyren-yedek")
        assertFalse(File(d, ".zyren-yedek").exists()); assertTrue(File(d, "a.txt").exists())
    }
    @Test fun copyLimited() {
        assertThrows(IllegalArgumentException::class.java) { ZipSafe.copyLimited(ByteArray(100).inputStream(), java.io.ByteArrayOutputStream(), 10) }
    }
}
