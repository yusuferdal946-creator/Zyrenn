package com.zyren.app.core.tools

import com.zyren.app.core.plugin.PluginRegistry
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.Risk
import com.zyren.app.core.security.PermissionManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class Args(val o: JsonObject) {
    fun str(k: String): String? = (o[k] as? JsonPrimitive)?.contentOrNull
    fun req(k: String): String = str(k)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("'$k' parametresi gerekli")
    fun int(k: String): Int? = str(k)?.toDoubleOrNull()?.toInt()
    fun arr(k: String): JsonArray? = o[k] as? JsonArray
}

/** capability == null ise araç izin kontrolünü kendi içinde (Workspace/ShellRunner) yapar. */
class Tool(
    val name: String,
    val plugin: String,
    val doc: String,
    val capability: Capability?,
    val detail: (Args) -> String,
    val run: suspend (Args) -> String
)

class ToolRegistry(private val perms: PermissionManager, private val plugins: PluginRegistry) {
    private val tools = mutableListOf<Tool>()
    fun add(t: Tool) { tools += t }
    fun enabled(): List<Tool> = tools.filter { plugins.isEnabled(it.plugin) }
    fun doc(): String = enabled().joinToString("\n") { "- ${it.name}: ${it.doc}" }
    fun docFor(plugins: Set<String>): String = enabled().filter { it.plugin in plugins }.joinToString("\n") { "- ${it.name}: ${it.doc}" }
    fun remove(name: String) { tools.removeAll { it.name == name } }
    fun has(name: String) = tools.any { it.name == name }
    fun riskOf(name: String): Risk? = enabled().firstOrNull { it.name == name }?.let { perms.riskOf(it.capability) }

    /** Ajan rolü: yalnızca rolün eklentilerindeki araçlar çağrılabilir. */
    suspend fun executeIn(plugins: Set<String>, name: String, args: Args): String =
        if (enabled().none { it.name == name && it.plugin in plugins }) "HATA: '$name' bu rolün aracı değil." else execute(name, args)

    /** Simülasyon: gerçek işlem YAPILMAZ; yetki/izin/onay durumu ve (varsa) ikiz tahmini raporlanır. */
    fun simulate(name: String, args: Args, twinHint: String? = null): String {
        val t = enabled().firstOrNull { it.name == name } ?: return "[SİMÜLASYON] '$name' adlı araç yok/kapalı → gerçekte HATA verirdi."
        val cap = t.capability
        val capLine = if (cap == null) "yetki: araç kendi içinde denetler" else
            "yetki: ${cap.label} · risk: ${cap.risk.label} · izin ${if (perms.enabled.value[cap] == true) "AÇIK" else "KAPALI → reddedilirdi"}"
        val approval = if (cap?.risk == Risk.CRITICAL) "kullanıcı onayı istenirdi" else "onay gerekmezdi"
        val detail = runCatching { t.detail(args) }.getOrDefault("")
        return "[SİMÜLASYON — gerçek işlem YAPILMADI] ${t.name}\n$capLine\n$approval" + (if (detail.isNotBlank()) "\nayrıntı: $detail" else "") + (twinHint?.let { "\nikiz tahmini: $it" } ?: "")
    }

    suspend fun execute(name: String, args: Args): String {
        val t = enabled().firstOrNull { it.name == name } ?: return "HATA: '$name' adlı araç yok veya eklentisi kapalı."
        val cap = t.capability
        if (cap != null && !perms.check(cap, t.detail(args))) return "HATA: izin verilmedi (${cap.label})."
        return try { t.run(args) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            "HATA: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}

data class ToolCall(val name: String, val args: Args)

object ToolProtocol {
    const val INVALID = "__gecersiz__"
    private val re = Regex("<arac>(.*?)</arac>", RegexOption.DOT_MATCHES_ALL)

    fun parse(reply: String): ToolCall? {
        val m = re.find(reply) ?: return null
        val raw = m.groupValues[1].trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching {
            val o = Json.parseToJsonElement(raw).jsonObject
            ToolCall(o["ad"]!!.jsonPrimitive.content, Args((o["args"] as? JsonObject) ?: JsonObject(emptyMap())))
        }.getOrElse { ToolCall(INVALID, Args(JsonObject(emptyMap()))) }
    }
}
