package com.zyren.app

import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.PathGuard
import com.zyren.app.core.tools.SafeShell
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafeShellTest {
    private val caps = mutableListOf<Capability>()
    private var trashed = 0
    private fun fx(allow: Boolean = true): Pair<SafeShell, File> {
        val b = Files.createTempDirectory("sh").toFile(); val ws = File(b, "ws").apply { mkdirs() }
        return SafeShell(PathGuard(ws), { c, _ -> caps += c; allow }, { f -> trashed++; f.deleteRecursively() }) to b
    }
    private fun SafeShell.ok(c: String) = runBlocking { run(c) }.getOrThrow()
    private fun SafeShell.fails(c: String) = runBlocking { run(c) }.isFailure

    @Test fun basics() {
        val (s, _) = fx()
        assertEquals("selam dunya", s.ok("echo selam dunya")); assertEquals("/", s.ok("pwd"))
        s.ok("mkdir d"); s.ok("touch d/a.txt")
        assertTrue(s.ok("ls d").contains("a.txt")); assertTrue(s.ok("find . -name '*.txt'").contains("d/a.txt"))
    }
    @Test fun catHeadTailWcGrep() {
        val (s, b) = fx(); File(b, "ws/f.txt").writeText("bir\niki\nüç\nDÖRT")
        assertEquals("bir\niki", s.ok("head -n 2 f.txt")); assertEquals("üç\nDÖRT", s.ok("tail -n 2 f.txt"))
        assertTrue(s.ok("wc f.txt").startsWith("4 satır")); assertTrue(s.ok("grep -i dört f.txt").contains("f.txt:4:DÖRT"))
        assertTrue(s.ok("grep -r iki .").contains("f.txt:2:iki"))
    }
    @Test fun traversalAndAbsoluteBlocked() {
        val (s, b) = fx(); File(b, "secret.txt").writeText("gizli")
        assertTrue(s.fails("cat ../secret.txt")); assertTrue(s.fails("ls .."))
        assertTrue(s.fails("cat /etc/passwd")); assertTrue(s.fails("cp ../secret.txt x"))
    }
    @Test fun symlinkEscapeBlocked() {
        val (s, b) = fx(); File(b, "out").mkdirs(); File(b, "out/s.txt").writeText("gizli")
        assumeTrue(runCatching { Files.createSymbolicLink(File(b, "ws/lnk").toPath(), File(b, "out").toPath()) }.isSuccess)
        assertTrue(s.fails("cat lnk/s.txt")); assertFalse(s.ok("find .").contains("s.txt"))
    }
    @Test fun operatorsAndUnknownCommandsRejected() {
        val (s, _) = fx()
        for (c in listOf("ls | cat", "echo a > b", "echo \$HOME", "echo `id`", "ls; id", "ls && id", "sh -c id", "rm -rf /", "curl x", "cat a\\b")) assertTrue(c, s.fails(c))
    }
    @Test fun noOverwrite() {
        val (s, b) = fx(); File(b, "ws/a").writeText("1"); File(b, "ws/b").writeText("2")
        assertTrue(s.fails("cp a b")); assertTrue(s.fails("mv a b")); assertEquals("2", File(b, "ws/b").readText())
    }
    @Test fun rmGoesThroughTrashAndNeedsCriticalPermission() {
        val (s, b) = fx(); File(b, "ws/a").writeText("1"); caps.clear()
        s.ok("rm a"); assertEquals(1, trashed); assertTrue(caps.contains(Capability.FILE_DELETE))
    }
    @Test fun deniedPermissionBlocks() {
        val (s, b) = fx(allow = false); File(b, "ws/a").writeText("1")
        assertTrue(s.fails("cat a")); assertTrue(s.fails("rm a")); assertTrue(File(b, "ws/a").exists())
    }
    @Test fun reservedHidden() {
        val (s, b) = fx(); File(b, "ws/.zyren").mkdirs(); File(b, "ws/.zyren/x.json").writeText("{}")
        assertTrue(s.fails("cat .zyren/x.json")); assertFalse(s.ok("ls").contains(".zyren")); assertTrue(s.fails("rm .zyren"))
    }
    @Test fun cannotDeleteRoot() { val (s, _) = fx(); assertTrue(s.fails("rm .")) }
}
