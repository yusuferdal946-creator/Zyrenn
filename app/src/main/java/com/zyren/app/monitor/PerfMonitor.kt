package com.zyren.app.monitor

import com.zyren.app.store.JsonStore
import kotlinx.serialization.json.*

class Sample(val t: Long, val ramAvailMb: Long, val ramTotalMb: Long, val battery: Int, val charging: Boolean, val storageFreeGb: Double, val cpuPct: Int)

/** RAM/CPU/pil/depolama zaman serisi (en çok 288 örnek) + model gecikmesi; eşik tabanlı sorun tespiti ve çözüm önerisi. */
class PerfMonitor(private val store: JsonStore? = null) {
    private val samples = ArrayDeque<Sample>()
    private val latencies = ArrayDeque<Long>()

    init { reload() }

    fun reload() {
        samples.clear()
        store?.read()?.forEach { e -> runCatching { val o = e.jsonObject
            samples.addLast(Sample(o["t"]!!.jsonPrimitive.long, o["ram"]!!.jsonPrimitive.long, o["ramT"]!!.jsonPrimitive.long, o["bat"]!!.jsonPrimitive.int,
                o["chg"]!!.jsonPrimitive.boolean, o["sto"]!!.jsonPrimitive.double, o["cpu"]!!.jsonPrimitive.int)) } }
    }

    @Synchronized fun add(s: Sample) {
        samples.addLast(s); while (samples.size > 288) samples.removeFirst()
        store?.write(buildJsonArray { samples.forEach { x -> add(buildJsonObject { put("t", x.t); put("ram", x.ramAvailMb); put("ramT", x.ramTotalMb); put("bat", x.battery); put("chg", x.charging); put("sto", x.storageFreeGb); put("cpu", x.cpuPct) }) } })
    }

    @Synchronized fun recordLatency(ms: Long) { latencies.addLast(ms); while (latencies.size > 30) latencies.removeFirst() }
    @Synchronized fun last(): Sample? = samples.lastOrNull()
    @Synchronized fun all(): List<Sample> = samples.toList()

    /** Son 5 gecikmenin medyanı 20 sn üstü veya önceki medyanın 2.5 katı ise yavaşlama. */
    @Synchronized fun slowdown(): String? {
        if (latencies.size < 6) return null
        val recent = latencies.toList().takeLast(5).sorted()[2]
        val earlier = latencies.toList().dropLast(5).sorted().let { it[it.size / 2] }
        return when { recent > 20_000 -> "Model yanıtları yavaş (medyan ${recent / 1000} sn)"; recent > earlier * 5 / 2 && recent > 4_000 -> "Model yanıtları normalin ${recent / earlier} katı yavaşladı"; else -> null }
    }

    fun issues(s: Sample? = last()): List<String> {
        val out = mutableListOf<String>()
        if (s != null) {
            if (s.ramAvailMb < 400) out += "Boş RAM düşük (${s.ramAvailMb} MB) → arka plan uygulamalarını kapatın; Ayar'dan yerel modeli bellekten bırakın; bulut modele geçin."
            if (s.battery in 0..14 && !s.charging) out += "Pil %${s.battery} → otomasyonları duraklatın, konuşma modunu ve yerel modeli kapatın."
            if (s.storageFreeGb < 2.0) out += "Boş depolama az (${"%.1f".format(s.storageFreeGb)} GB) → eski yedekleri/çöp kutusunu temizleyin, kullanılmayan yerel model dosyasını silin."
            if (s.cpuPct > 70) out += "Zyren CPU kullanımı yüksek (%${s.cpuPct}) → konuşma modunu ve arka plan otomasyonlarını kapatın."
        }
        slowdown()?.let { out += "$it → ağ bağlantısını kontrol edin; daha hızlı sağlayıcı/model seçin; yerel modele geçin." }
        return out
    }

    /** errors: "etiket: ayrıntı" biçiminde son hata kayıtları. */
    fun report(errors: List<String>): String = buildString {
        val s = this@PerfMonitor.last()
        append(if (s == null) "Henüz ölçüm yok." else "RAM ${s.ramAvailMb}/${s.ramTotalMb} MB boş · Pil %${s.battery}${if (s.charging) " (şarjda)" else ""} · Depolama ${"%.1f".format(s.storageFreeGb)} GB boş · CPU %${s.cpuPct}")
        val iss = issues()
        append("\n\nTespit edilen sorunlar: ").append(if (iss.isEmpty()) "yok" else "\n- " + iss.joinToString("\n- "))
        append("\n\nSon hatalar (${errors.size}): ")
        val top = errors.groupingBy { it.substringBefore(':') }.eachCount().entries.sortedByDescending { it.value }.take(5)
        append(if (top.isEmpty()) "yok" else top.joinToString(", ") { "${it.key}×${it.value}" })
    }
}
