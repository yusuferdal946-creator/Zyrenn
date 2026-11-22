package com.zyren.app.core.security

/** Şüpheli işlem algılama (saf JVM): kısa sürede aşırı kritik istek / ret / toplam işlem. */
class AnomalyDetector(
    private val windowMs: Long = 60_000,
    private val maxCritical: Int = 8,
    private val maxDenied: Int = 12,
    private val maxTotal: Int = 60
) {
    private class Ev(val t: Long, val critical: Boolean, val denied: Boolean)
    private val evs = ArrayDeque<Ev>()

    /** Şüphe varsa nedenini döner. */
    @Synchronized
    fun record(now: Long, critical: Boolean, denied: Boolean): String? {
        evs.addLast(Ev(now, critical, denied))
        while (evs.isNotEmpty() && now - evs.first().t > windowMs) evs.removeFirst()
        return when {
            evs.count { it.critical } > maxCritical -> "1 dakikada çok fazla kritik işlem isteği"
            evs.count { it.denied } > maxDenied -> "1 dakikada çok fazla reddedilen işlem (olası kötüye kullanım/döngü)"
            evs.size > maxTotal -> "1 dakikada aşırı işlem sıklığı (olası kontrolsüz döngü)"
            else -> null
        }
    }

    @Synchronized fun reset() = evs.clear()
}
