
package com.zyren.app.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zyren.app.ZyrenApp
import com.zyren.app.core.security.Capability
import java.time.ZoneId

class AutomationWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ZyrenApp
        val c = app.container

        runCatching { c.perfSample() }

        // Güvenlik koşulları sağlanmıyorsa bu turu atla.
        // Periyodik görev devam etsin diye failure döndürme.
        if (
            c.settings.automationPaused ||
            c.permissions.lockdown.value ||
            c.permissions.enabled.value[Capability.AUTOMATION_RUN] != true
        ) {
            return Result.success()
        }

        val requestedId = inputData.getString("id")
        val task = inputData.getString("task") ?: "periyodik otomasyon"

        return try {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()

            // ID verilmişse yalnızca o kuralı, verilmemişse
            // zamanı gelen bütün kuralları kontrol et.
            val rule = requestedId?.let { id ->
                c.automation.rules.firstOrNull {
                    it.id == id && it.enabled
                }
            }

            if (requestedId != null && rule == null) {
                return Result.success()
            }

            val outcomes = c.automation.runDue(
                now = now,
                zone = zone,
                interactive = false,
                riskOf = { name -> c.tools.riskOf(name) },
                exec = { name, args -> c.tools.execute(name, args) },
                onlyRuleId = requestedId
            )

            if (outcomes.isEmpty()) {
                Result.success()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            runCatching {
                c.permissions.note(
                    "OTOMASYON",
                    task,
                    "hata: ${e.message ?: "bilinmeyen hata"}"
                )
            }
            Result.retry()
        }
    }
}
