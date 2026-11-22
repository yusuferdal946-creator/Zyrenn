package com.zyren.app.core.plugin

import android.content.Context
import com.zyren.app.core.security.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Eklenti sözleşmesi. Güvenilmeyen (trusted=false) eklentiler yalnızca bildirdiği yetkilere sahip olur;
 * kritik yetkiler yine PermissionManager onayından geçer. Şimdilik yalnızca yerleşik eklentiler var;
 * harici eklenti yükleyici YOK (güvenlik nedeniyle bilinçli olarak eklenmedi).
 */
interface ZyrenPlugin {
    val id: String
    val name: String
    val description: String
    val trusted: Boolean
    val capabilities: Set<Capability>
}

class BuiltinPlugin(
    override val id: String,
    override val name: String,
    override val description: String,
    override val capabilities: Set<Capability>
) : ZyrenPlugin { override val trusted = true }

class PluginRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("zyren_plugins", Context.MODE_PRIVATE)
    private val plugins = mutableListOf<ZyrenPlugin>()
    private val _state = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val state: StateFlow<Map<String, Boolean>> = _state

    fun register(p: ZyrenPlugin) {
        plugins.removeAll { it.id == p.id }; plugins.add(p)
        _state.value = _state.value + (p.id to prefs.getBoolean(p.id, true))
    }
    fun all(): List<ZyrenPlugin> = plugins.toList()
    fun isEnabled(id: String) = _state.value[id] ?: true
    fun setEnabled(id: String, on: Boolean) {
        prefs.edit().putBoolean(id, on).apply()
        _state.value = _state.value + (id to on)
    }
}
