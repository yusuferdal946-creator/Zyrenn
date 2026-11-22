package com.zyren.app.core.net

object NetUtil {
    /** Manifest'teki ağ güvenlik yapılandırmasıyla AYNI liste: yalnızca bu adreslere açık metin (http) izinli. */
    fun isCleartextAllowed(host: String): Boolean { val h = host.lowercase(); return h == "localhost" || h == "127.0.0.1" }

    /** Yerel/özel ağ adresi mi? (http yalnızca bunlara izinli; web okuma bunlara kapalı) */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local") || h == "::1" || h == "[::1]") return true
        val p = h.split(".")
        if (p.size == 4 && p.all { it.toIntOrNull() != null }) {
            val a = p[0].toInt(); val b = p[1].toInt()
            return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 169 && b == 254)
        }
        return false
    }
}
