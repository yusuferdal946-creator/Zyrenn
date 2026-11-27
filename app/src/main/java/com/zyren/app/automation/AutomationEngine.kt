package com.zyren.app.automation

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zyren.app.core.security.Risk
import com.zyren.app.core.tools.Args
import com.zyren.app.store.JsonStore
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class Rule(
    val id: String,
    var name: String,
    val trigger: String,
    val tool: String,
    val args: JsonObject,
    var enabled: Boolean,
    var lastRun: Long,
    var runs: Int,
    var failures: Int,
    val maxPerDay: Int,
    var dayKey: String,
    var todayRuns: Int
)

/**
 * Kullanıcı kurallarına göre zamanlı görevler.
 * Arka planda yalnızca izinli ve kritik olmayan araçlar çalışır.
 */
class AutomationEngine(
    private val store: JsonStore? = null,
    private val history: JsonStore? = null,
    private val ctx: Context? = null,
    private val idGen: () -> String = {
        UUID.randomUUID().toString().take(8)
    }
) {
    companion object {
        const val MIN_INTERVAL_MIN = 15L
        const val MAX_FAILURES = 5

        val BACKGROUND_ALLOWED = setOf(
            "pil_durumu",
            "depolama_durumu",
            "sistem_raporu",
            "ikiz_guncelle",
            "hafiza_bakim",
            "plan_goster",
            "yeniden_planla",
            "bilgi_ara",
            "dosya_listele"
        )
    }

    val rules = mutableListOf<Rule>()
    private val hist = ArrayDeque<String>()

    init {
        reload()
    }

    fun validateTrigger(t: String): String? = when {
        t.startsWith("aralik:") -> {
            val m = t.removePrefix("aralik:").toLongOrNull()
            when {
                m == null -> "aralık sayı olmalı"
                m < MIN_INTERVAL_MIN -> "aralık en az $MIN_INTERVAL_MIN dk"
                else -> null
            }
        }

        t.startsWith("gunluk:") -> {
            val p = t.removePrefix("gunluk:").split(":")
            if (
                p.size == 2 &&
                (p[0].toIntOrNull() ?: -1) in 0..23 &&
                (p[1].toIntOrNull() ?: -1) in 0..59
            ) null else "gunluk:HH:MM biçimi"
        }

        else -> "tetikleyici 'gunluk:HH:MM' veya 'aralik:DAKIKA' olmalı"
    }

    fun add(
        name: String,
        trigger: String,
        tool: String,
        args: JsonObject,
        maxPerDay: Int = 24
    ): Rule {
        validateTrigger(trigger)?.let {
            throw IllegalArgumentException(it)
        }

        require(rules.size < 30) {
            "En fazla 30 kural"
        }

        val r = Rule(
            idGen(),
            name.take(80),
            trigger,
            tool,
            args,
            true,
            0L,
            0,
            0,
            maxPerDay.coerceIn(1, 96),
            "",
            0
        )

        rules += r
        save()
        return r
    }

    fun remove(id: String) {
        rules.removeAll { it.id == id }
        save()
    }

    fun setEnabled(id: String, on: Boolean) {
        rules.firstOrNull { it.id == id }?.let {
            it.enabled = on
            if (on) it.failures = 0
        }
        save()
    }

    fun pause(id: String) = setEnabled(id, false)

    fun resume(id: String) = setEnabled(id, true)

    fun cancel(id: String) = remove(id)

    fun statusSummary(): String {
        if (rules.isEmpty()) return "Tanımlı görev/kural yok."

        val sb = StringBuilder()

        rules.forEach { r ->
            val state = when {
                !r.enabled -> "duraklatıldı"
                r.failures >= MAX_FAILURES -> "hata nedeniyle durduruldu"
                else -> "etkin"
            }

            sb.append("- [")
                .append(state)
                .append("] ")
                .append(r.name)
                .append(" (")
                .append(r.tool)
                .append(") · bugün ")
                .append(r.todayRuns)
                .append("/")
                .append(r.maxPerDay)
                .append(" çalıştı\n")
        }

        return sb.toString().trimEnd()
    }

    suspend fun baslat(id: String, task: String) {
        val context = ctx ?: throw IllegalStateException(
            "Context cannot be null for WorkManager operations"
        )

        val workName = "zyren_task_$id"

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutomationWorker>()
                .setInputData(workDataOf("task" to task, "id" to id))
                .build()
        )
    }

    fun duraklat(id: String) {
        rules.firstOrNull { it.id == id }?.let {
            it.enabled = false
            save()
        }
    }

    fun devamEttir(id: String) {
        rules.firstOrNull { it.id == id }?.let {
            it.enabled = true
            it.failures = 0
            save()
        }
    }

    fun iptalEt(id: String) {
        rules.removeAll { it.id == id }
        save()
    }

    fun uzunGorevDurumu(id: String): String {
        val r = rules.firstOrNull { it.id == id }

        return when {
            r == null -> "Görev bulunamadı."
            !r.enabled -> "duraklatıldı"
            r.failures >= MAX_FAILURES -> "hata nedeniyle durduruldu"
            else -> "etkin (${r.todayRuns}/${r.maxPerDay} çalıştı)"
        }
    }

    fun isDue(r: Rule, now: Long, zone: ZoneId): Boolean {
        if (!r.enabled || r.failures >= MAX_FAILURES) return false

        val day = Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()

        if (r.dayKey == day.toString() && r.todayRuns >= r.maxPerDay) {
            return false
        }

        return when {
            r.trigger.startsWith("aralik:") -> {
                val minutes = r.trigger
                    .removePrefix("aralik:")
                    .toLongOrNull() ?: return false

                minutes >= MIN_INTERVAL_MIN &&
                    now - r.lastRun >= minutes * 60_000
            }

            r.trigger.startsWith("gunluk:") -> {
                val p = r.trigger.removePrefix("gunluk:").split(":")
                val sched = day.atTime(
                    p[0].toInt(),
                    p[1].toInt()
                ).atZone(zone).toInstant().toEpochMilli()

                now >= sched && r.lastRun < sched
            }

            else -> false
        }
    }

    suspend fun runDue(
        now: Long,
        zone: ZoneId,
        interactive: Boolean,
        riskOf: (String) -> Risk?,
        exec: suspend (String, Args) -> String,
        onlyRuleId: String? = null
    ): List<String> {
        val out = mutableListOf<String>()

        val day = Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .toString()

        for (r in rules.toList()) {
            if (onlyRuleId != null && r.id != onlyRuleId) continue
            if (!isDue(r, now, zone)) continue

            try {
                val risk = riskOf(r.tool)

                if (risk == null) {
                    r.lastRun = now
                    r.failures++
                    log(now, r, "hata", "araç yok/kapalı")
                    out += "${r.name}: araç yok"
                    continue
                }

                if (
                    !interactive &&
                    (risk == Risk.CRITICAL || r.tool !in BACKGROUND_ALLOWED)
                ) {
                    log(
                        now,
                        r,
                        "atlandı",
                        "arka planda çalıştırılamaz (onay/izin gerekir)"
                    )
                    out += "${r.name}: atlandı"
                    continue
                }

                r.lastRun = now

                if (r.dayKey != day) {
                    r.dayKey = day
                    r.todayRuns = 0
                }

                val res = exec(r.tool, Args(r.args))

                r.runs++
                r.todayRuns++

                if (res.startsWith("HATA")) {
                    r.failures++
                } else {
                    r.failures = 0
                }

                log(
                    now,
                    r,
                    if (res.startsWith("HATA")) "hata" else "tamam",
                    res.take(160)
                )

                out += "${r.name}: ${res.take(80)}"
            } catch (e: Exception) {
                r.lastRun = now
                r.failures++

                log(
                    now,
                    r,
                    "hata",
                    (e.message ?: "bilinmeyen hata").take(160)
                )

                out += "${r.name}: hata"
            }
        }

        save()
        return out
    }

    fun history(): List<String> = hist.toList().asReversed()

    private fun log(
        now: Long,
        r: Rule,
        outcome: String,
        detail: String
    ) {
        hist.addLast("$now|${r.name}|$outcome|$detail")

        while (hist.size > 200) {
            hist.removeFirst()
        }

        history?.write(buildJsonArray {
            hist.forEach { add(it) }
        })
    }

    private fun save() {
        store?.write(buildJsonArray {
            rules.forEach { r ->
                add(buildJsonObject {
                    put("id", r.id)
                    put("name", r.name)
                    put("trigger", r.trigger)
                    put("tool", r.tool)
                    put("args", r.args)
                    put("enabled", r.enabled)
                    put("lastRun", r.lastRun)
                    put("runs", r.runs)
                    put("failures", r.failures)
                    put("maxPerDay", r.maxPerDay)
                    put("dayKey", r.dayKey)
                    put("todayRuns", r.todayRuns)
                })
            }
        })
    }

    fun reload() {
        rules.clear()
        hist.clear()

        store?.read()?.forEach { e ->
            runCatching {
                val o = e.jsonObject

                rules += Rule(
                    o["id"]!!.jsonPrimitive.content,
                    o["name"]!!.jsonPrimitive.content,
                    o["trigger"]!!.jsonPrimitive.content,
                    o["tool"]!!.jsonPrimitive.content,
                    o["args"]!!.jsonObject,
                    o["enabled"]!!.jsonPrimitive.boolean,
                    o["lastRun"]!!.jsonPrimitive.long,
                    o["runs"]!!.jsonPrimitive.int,
                    o["failures"]!!.jsonPrimitive.int,
                    o["maxPerDay"]!!.jsonPrimitive.int,
                    o["dayKey"]!!.jsonPrimitive.content,
                    o["todayRuns"]!!.jsonPrimitive.int
                )
            }
        }

        history?.read()?.forEach { item ->
            runCatching {
                hist.addLast(item.jsonPrimitive.content)
            }
        }
    }
}
