package com.zyren.app.core.tools

import android.net.Uri
import com.zyren.app.core.net.NetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Ücretsiz, anahtarsız web araçları. DuckDuckGo HTML sayfası kazınır (kırılgan olabilir); olmazsa Vikipedi API'si.
 * Dönen içerik güvenilmez veridir.
 */
class WebTools {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val ua = "Mozilla/5.0 (Android) Zyren/0.2"

    private fun get(url: String, maxBytes: Long = 1_500_000): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val src = r.body?.source() ?: return ""
            src.request(maxBytes)
            return src.buffer.clone().readUtf8(minOf(src.buffer.size, maxBytes))
        }
    }

    private fun clean(html: String) = html.replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        runCatching {
            val html = get("https://html.duckduckgo.com/html/?q=" + Uri.encode(query))
            val links = Regex("class=\"result__a\" href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
            val snippets = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
            links.take(6).forEachIndexed { i, m ->
                var href = m.groupValues[1].replace("&amp;", "&")
                if (href.contains("uddg=")) href = Uri.decode(href.substringAfter("uddg=").substringBefore("&"))
                if (href.startsWith("//")) href = "https:$href"
                results += "${i + 1}. ${clean(m.groupValues[2])}\n   $href\n   ${snippets.getOrNull(i)?.let { clean(it.groupValues[1]) } ?: ""}"
            }
        }
        if (results.isEmpty()) runCatching {
            val json = get("https://tr.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=5&srsearch=" + Uri.encode(query))
            Json.parseToJsonElement(json).jsonObject["query"]!!.jsonObject["search"]!!.jsonArray.forEachIndexed { i, e ->
                val o = e.jsonObject
                val title = o["title"]!!.jsonPrimitive.content
                results += "${i + 1}. [Vikipedi] $title\n   https://tr.wikipedia.org/wiki/" + Uri.encode(title.replace(' ', '_')) +
                    "\n   " + clean(o["snippet"]!!.jsonPrimitive.content)
            }
        }
        if (results.isEmpty()) "HATA: arama sonucu alınamadı (internet yok veya servis yanıt vermedi)"
        else "<web_icerik>\n" + results.joinToString("\n") + "\n</web_icerik>"
    }

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val u = url.toHttpUrl()
        require(u.scheme == "https") { "Yalnızca https adresleri okunur" }
        require(!NetUtil.isPrivateHost(u.host) && InetAddress.getAllByName(u.host).none { it.isSiteLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }) {
            "Güvenlik: yerel ağ adresleri okunamaz"
        }
        var html = get(url)
        html = html.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
        "<web_icerik>\n" + clean(html).take(6000) + "\n</web_icerik>"
    }

    /** Yalnızca api.github.com (anahtarsız, saatte ~60 istek). Yol doğrulanır; başka ana makineye gidemez. */
    suspend fun githubJson(path: String): String = withContext(Dispatchers.IO) {
        require(Regex("^[A-Za-z0-9_./?&=%:+,-]{1,300}$").matches(path) && !path.contains("..")) { "geçersiz GitHub yolu" }
        val req = Request.Builder().url("https://api.github.com/" + path.trimStart('/'))
            .header("User-Agent", ua).header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { r ->
            if (r.code == 403 || r.code == 429) error("GitHub istek sınırı doldu; biraz sonra tekrar dene")
            if (!r.isSuccessful) error("GitHub HTTP ${r.code}")
            r.body?.string()?.take(400_000) ?: ""
        }
    }

    suspend fun downloadImage(prompt: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "https://image.pollinations.ai/prompt/" + Uri.encode(prompt) + "?width=768&height=768&nologo=true"
        val req = Request.Builder().url(url).header("User-Agent", ua).build()
        OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.bytes() ?: error("Boş yanıt")
        }
    }
}
