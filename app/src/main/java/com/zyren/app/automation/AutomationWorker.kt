package com.zyren.app.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zyren.app.ZyrenApp
import com.zyren.app.core.security.Capability
import java.time.ZoneId

/**
 * Arka plan görevi (WorkManager, ≥15 dk, yeniden başlatmada kalıcı). Duraklatılmışsa, kilit modundaysa veya
 * AUTOMATION_RUN kapalıysa HİÇBİR ŞEY çalıştırmaz. Kritik araçlar arka planda asla çalışmaz (motor atlar).
 */
class AutomationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as ZyrenApp).container
        runCatching { c.perfSample() }
        if (c.settings.automationPaused || c.permissions.lockdown.value || c.permissions.enabled.value[Capability.AUTOMATION_RUN] != true) return Result.success()
        runCatching {
            c.automation.runDue(System.currentTimeMillis(), ZoneId.systemDefault(), false, { c.tools.riskOf(it) }, { n, a -> c.tools.execute(n, a) })
        }.onFailure { c.permissions.note("OTOMASYON", "arka plan", "hata: ${it.message}") }
        return Result.success()
    }
}
