package com.zyren.app.core.tools

import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.PermissionManager
import com.zyren.app.core.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * HAM KABUK — İZOLE DEĞİLDİR. Android'de uygulama içi gerçek izolasyon yoktur: komut Zyren'in kendi Linux kullanıcısıyla
 * çalışır, çalışma klasörü yalnızca başlangıç dizinidir; uygulamanın özel verilerine (veritabanı vb.) ve ağa erişebilir.
 * Bu yüzden: varsayılan KAPALI, her çalıştırmada kullanıcı onayı (SHELL_ANY = kritik), ortam temizlenir, 30 sn sınır.
 * Güvenli kullanım için SafeShell tercih edilir.
 */
class ShellRunner(private val ws: Workspace, private val perms: PermissionManager) {
    suspend fun run(cmd: String): Result<String> {
        if (!perms.check(Capability.SHELL_ANY, "HAM KABUK (izole değil): $cmd"))
            return Result.failure(SecurityException("İzin verilmedi: ${Capability.SHELL_ANY.label}"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val pb = ProcessBuilder("/system/bin/sh", "-c", cmd).directory(ws.root).redirectErrorStream(true)
                pb.environment().clear()
                pb.environment()["PATH"] = "/system/bin"
                pb.environment()["HOME"] = ws.root.path
                val p = pb.start()
                val sb = StringBuilder()
                val reader = Thread {
                    p.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(1024)
                        while (true) { val n = r.read(buf); if (n < 0) break; if (sb.length < 8000) sb.append(buf, 0, n) }
                    }
                }
                reader.start()
                val done = p.waitFor(30, TimeUnit.SECONDS)
                if (!done) p.destroyForcibly()
                reader.join(1000)
                (if (done) "" else "[zaman aşımı: 30 sn]\n") + sb.toString().take(8000) + "\n[çıkış kodu: ${if (done) p.exitValue() else -1}]"
            }
        }
    }
}
