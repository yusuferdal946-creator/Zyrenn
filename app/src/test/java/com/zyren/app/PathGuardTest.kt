package com.zyren.app

import com.zyren.app.core.security.PathGuard
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PathGuardTest {
    private fun base(): File = Files.createTempDirectory("zy").toFile()

    @Test fun dotdotBlocked() {
        val g = PathGuard(File(base(), "ws").apply { mkdirs() })
        assertThrows(IllegalArgumentException::class.java) { g.resolve("../x") }
        assertThrows(IllegalArgumentException::class.java) { g.resolve("a/../../x") }
    }

    @Test fun absoluteStaysInside() {
        val g = PathGuard(File(base(), "ws").apply { mkdirs() })
        assertTrue(g.contains(g.resolve("/etc/passwd")))
    }

    @Test fun prefixTrickBlocked() {
        val b = base(); val ws = File(b, "ws").apply { mkdirs() }; File(b, "ws-evil").mkdirs()
        assertThrows(IllegalArgumentException::class.java) { PathGuard(ws).resolve("../ws-evil/a") }
    }

    @Test fun symlinkEscapeBlocked() {
        val b = base(); val ws = File(b, "ws").apply { mkdirs() }; val out = File(b, "out").apply { mkdirs() }
        File(out, "secret.txt").writeText("s")
        val ok = runCatching { Files.createSymbolicLink(File(ws, "link").toPath(), out.toPath()) }.isSuccess
        assumeTrue(ok)
        assertThrows(IllegalArgumentException::class.java) { PathGuard(ws).resolve("link/secret.txt") }
    }

    @Test fun walkSkipsSymlinks() {
        val b = base(); val ws = File(b, "ws").apply { mkdirs() }; val out = File(b, "out").apply { mkdirs() }
        File(out, "secret.txt").writeText("s"); File(ws, "ok.txt").writeText("o")
        runCatching { Files.createSymbolicLink(File(ws, "link").toPath(), out.toPath()) }
        val names = PathGuard(ws).walk().map { it.name }.toList()
        assertTrue(names.contains("ok.txt")); assertFalse(names.contains("secret.txt"))
    }

    @Test fun reservedDir() {
        val g = PathGuard(File(base(), "ws").apply { mkdirs() })
        assertTrue(g.isReserved(g.resolve(".zyren/plans.json"))); assertFalse(g.isReserved(g.resolve("a.txt")))
    }
}
