package com.zyren.app.atelier

import java.util.Locale

/**
 * Atölye güvenlik kapısı (saf mantık, Android'e bağımlı değil → birim test edilebilir).
 * Üretilen mini uygulamalar tek dosyalık HTML'dir ve kum havuzunda çalışır: ağ, kamera, mikrofon, konum, telefon erişimi YOKTUR.
 * Bu sınıf (1) model çıktısından HTML'i ayıklar, (2) yasak kalıpları reddeder, (3) çalışma anında sıkı CSP enjekte eder.
 */
object MiniAppGuard {
    const val MAX_BYTES = 400_000
    const val ERR_PREFIX = "ZYERR"

    const val CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; " +
        "font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"

    private val fence = Regex("```(?:html|HTML)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

    /** Model yanıtından HTML'i çıkarır: önce ```html bloğu, yoksa <!doctype/<html ile başlayan kısım. Bulamazsa null. */
    fun extractHtml(reply: String): String? {
        fence.find(reply)?.groupValues?.get(1)?.trim()?.takeIf { it.contains("<") }?.let { return it }
        val lower = reply.lowercase(Locale.ROOT)
        val start = listOf("<!doctype", "<html").map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
        val end = lower.lastIndexOf("</html>").let { if (it >= 0) it + 7 else reply.length }
        return reply.substring(start, end).trim().takeIf { it.length > 20 }
    }

    private class Rule(val re: Regex, val msg: String)
    private fun r(p: String, msg: String) = Rule(Regex(p, setOf(RegexOption.IGNORE_CASE)), msg)

    private val rules = listOf(
        r("\\b(src|href|action|poster|data|srcset|formaction)\\s*=\\s*[\"']?\\s*(https?:|//|ftp:|file:|javascript:)", "dış kaynak/bağlantı (src/href=http…) yasak; her şey tek dosyada olmalı"),
        r("url\\(\\s*[\"']?\\s*(https?:|//)", "CSS'te dış url() yasak"),
        r("@import", "@import yasak"),
        r("\\bfetch\\s*\\(", "fetch() yasak (ağ yok)"),
        r("XMLHttpRequest", "XMLHttpRequest yasak (ağ yok)"),
        r("\\bWebSocket\\b|\\bEventSource\\b|sendBeacon|importScripts|new\\s+Worker|serviceWorker", "ağ/worker API'leri yasak"),
        r("window\\.open\\s*\\(", "window.open yasak"),
        r("\\b(location\\s*\\.\\s*(href|assign|replace)\\s*=|location\\s*=)|location\\.(assign|replace)\\s*\\(", "sayfa yönlendirmesi yasak"),
        r("<\\s*(iframe|object|embed|base)\\b", "<iframe>/<object>/<embed>/<base> yasak"),
        r("http-equiv\\s*=\\s*[\"']?refresh", "meta refresh yasak"),
        r("\\beval\\s*\\(|new\\s+Function\\s*\\(", "eval/new Function yasak"),
        r("getUserMedia|mediaDevices|geolocation|navigator\\.clipboard|Notification\\.requestPermission", "kamera/mikrofon/konum/pano/bildirim erişimi yok"),
        r("<\\s*form\\b[^>]*\\baction\\s*=", "form action yasak (form gönderimi yok; JS ile işle)")
    )

    /** Boşsa uygun. Dolu ise her madde modele geri bildirim olarak verilir. */
    fun validate(html: String): List<String> {
        val p = mutableListOf<String>()
        if (html.isBlank()) return listOf("boş çıktı")
        if (html.toByteArray(Charsets.UTF_8).size > MAX_BYTES) p += "dosya ${MAX_BYTES / 1000} KB'dan büyük"
        if (!html.contains("<", ignoreCase = true) || !Regex("<\\s*(body|html|div|main|canvas|script)", RegexOption.IGNORE_CASE).containsMatchIn(html)) p += "geçerli bir HTML sayfası değil"
        rules.forEach { if (it.re.containsMatchIn(html)) p += it.msg }
        return p.distinct()
    }

    private val headOpen = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
    private val htmlOpen = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)

    private val errorHook = "<script>window.addEventListener('error',function(e){console.error('$ERR_PREFIX '+(e.message||'hata')+' @'+(e.lineno||0))});" +
        "window.addEventListener('unhandledrejection',function(e){console.error('$ERR_PREFIX '+String(e.reason))});</script>"

    /** Çalıştırmadan önce: sıkı CSP + hata yakalayıcı <head>'in en başına eklenir (kaydedilen dosya değişmez). Tekrar çağrılsa çift eklemez. */
    fun prepare(html: String): String {
        if (html.contains("Content-Security-Policy", ignoreCase = true) && html.contains(ERR_PREFIX)) return html
        val inject = "<meta http-equiv=\"Content-Security-Policy\" content=\"$CSP\">$errorHook"
        headOpen.find(html)?.let { return html.substring(0, it.range.last + 1) + inject + html.substring(it.range.last + 1) }
        htmlOpen.find(html)?.let { return html.substring(0, it.range.last + 1) + "<head>$inject</head>" + html.substring(it.range.last + 1) }
        return "<!doctype html><html><head>$inject</head><body>$html</body></html>"
    }

    /** Ad → dosya/kimlik güvenli kısa ad. */
    fun slug(name: String): String {
        val map = mapOf('ç' to 'c', 'ğ' to 'g', 'ı' to 'i', 'ö' to 'o', 'ş' to 's', 'ü' to 'u')
        val s = name.lowercase(Locale("tr", "TR")).map { map[it] ?: it }.joinToString("")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).trim('-')
        return s.ifBlank { "uygulama" }
    }
}
