package com.zyren.app.core.tools

import com.zyren.app.core.security.Capability
import com.zyren.app.store.JsonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class CustomTool(val name: String, val description: String, val code: String)

/**
 * Kendi araçlarını geliştirme: yalnızca SAF-JS (dosya/ağ/telefon/Java erişimi yok) araçlar. Etkinleşmeden önce
 * (1) kullanıcı onayı (TOOL_CREATE kritik), (2) kendi testini geçmesi şarttır. Yerleşik araçların üzerine yazamaz.
 */
class CustomTools(private val store: JsonStore?, private val js: JsSandbox) {
    private val nameRe = Regex("^ozel_[a-z0-9_]{3,30}$")
    val tools = mutableListOf<CustomTool>()

    init { reload() }

    fun validate(name: String, code: String, existsInRegistry: Boolean): String? = when {
        !nameRe.matches(name) -> "Ad 'ozel_' ile başlamalı, küçük harf/rakam/_ (3-30)"
        existsInRegistry || tools.any { it.name == name } -> "Bu adda araç zaten var"
        code.length > 5_000 -> "Kod 5000 karakterden uzun"
        !code.contains("function run") -> "Kod 'function run(args){...}' tanımlamalı"
        else -> null
    }

    /** Testi geçerse kaydeder; geçmezse hata mesajı döner (null = başarılı). */
    fun addIfTestPasses(t: CustomTool, testArgsJson: String, expected: String): String? {
        val o = js.call(t.code, testArgsJson)
        if (o.error != null) return "Test hata verdi: ${o.error}"
        if (o.value != expected) return "Test geçmedi: beklenen=$expected, gelen=${o.value}"
        tools += t; save(); return null
    }

    fun remove(name: String) { tools.removeAll { it.name == name }; save() }

    fun register(reg: ToolRegistry, t: CustomTool) {
        reg.add(Tool(t.name, "selftools", t.description + " (kullanıcı onaylı saf-JS araç; args JSON nesnesi)", Capability.CODE_RUN, { "Özel araç: ${t.name}" }) { a ->
            val o = withContext(Dispatchers.Default) { js.call(t.code, a.o.toString()) }
            o.error ?: o.value
        })
    }

    fun reload() {
        tools.clear()
        store?.read()?.forEach { e -> runCatching { val o = e.jsonObject
            tools += CustomTool(o["name"]!!.jsonPrimitive.content, o["description"]!!.jsonPrimitive.content, o["code"]!!.jsonPrimitive.content) } }
    }

    private fun save() {
        store?.write(buildJsonArray { tools.forEach { t -> add(buildJsonObject { put("name", t.name); put("description", t.description); put("code", t.code) }) } })
    }
}
