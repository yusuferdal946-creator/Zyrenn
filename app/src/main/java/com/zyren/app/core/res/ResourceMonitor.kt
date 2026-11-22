package com.zyren.app.core.res

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock

data class ResourceInfo(
    val ramTotalMb: Long, val ramAvailMb: Long, val lowMemory: Boolean,
    val batteryPct: Int, val charging: Boolean, val storageFreeGb: Double, val appCpuPct: Int
) {
    fun summary() = "RAM: $ramAvailMb/$ramTotalMb MB boş" + (if (lowMemory) " (DÜŞÜK)" else "") +
        " · Pil: %$batteryPct" + (if (charging) " (şarjda)" else "") +
        " · Depolama: ${"%.1f".format(storageFreeGb)} GB boş · Zyren CPU: %$appCpuPct"
}

class ResourceMonitor(private val ctx: Context) {
    private var lastCpu = Process.getElapsedCpuTime()
    private var lastWall = SystemClock.elapsedRealtime()

    fun snapshot(): ResourceInfo {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val b: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val sf = StatFs(Environment.getDataDirectory().path)
        val cpu = Process.getElapsedCpuTime(); val wall = SystemClock.elapsedRealtime()
        val dWall = (wall - lastWall).coerceAtLeast(1)
        val pct = ((cpu - lastCpu) * 100 / dWall / Runtime.getRuntime().availableProcessors()).toInt().coerceIn(0, 100)
        lastCpu = cpu; lastWall = wall
        return ResourceInfo(
            mi.totalMem / 1048576, mi.availMem / 1048576, mi.lowMemory,
            if (level >= 0) level * 100 / scale else -1,
            st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL,
            sf.availableBytes / 1073741824.0, pct
        )
    }
}
