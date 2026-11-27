package com.zyren.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zyren.app.core.model.ProviderPreset
import com.zyren.app.core.model.ProviderPresets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API anahtarları: AES-256-GCM, anahtar Android Keystore'da (dışarı çıkarılamaz). Şifreli metin sağlayıcı kimliğine bağlıdır (AAD).
 * Çözme başarısız olursa çökmez, anahtar yok sayılır (kullanıcı yeniden girer). Anahtarlar asla günlüğe/dosyaya yazılmaz.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("zyren_keys", Context.MODE_PRIVATE)
    private val alias = "zyren_api_key_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return gen.generateKey()
    }

    fun apiKey(providerId: String): String? {
        val s = prefs.getString("k_$providerId", null) ?: return null
        return runCatching {
            val raw = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            c.updateAAD(providerId.toByteArray())
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    fun hasKey(providerId: String) = !apiKey(providerId).isNullOrBlank()

    fun setApiKey(providerId: String, value: String?) {
        val e = prefs.edit()
        if (value.isNullOrBlank()) e.remove("k_$providerId")
        else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            c.updateAAD(providerId.toByteArray())
            val ct = c.doFinal(value.trim().toByteArray(Charsets.UTF_8))
            e.putString("k_$providerId", Base64.encodeToString(c.iv + ct, Base64.NO_WRAP))
        }
        e.apply()
    }
}

class Settings(context: Context) {
    private val p = context.getSharedPreferences("zyren_settings", Context.MODE_PRIVATE)
    var providerId: String
        get() = p.getString("provider", ProviderPresets.all.first().id)!!
        set(v) { p.edit().putString("provider", v).apply() }
    var autoFallback: Boolean
        get() = p.getBoolean("fallback", true)
        set(v) { p.edit().putBoolean("fallback", v).apply() }
    var humorLevel: Int
        get() = p.getInt("humor", 1).coerceIn(0, 3)
        set(v) { p.edit().putInt("humor", v.coerceIn(0, 3)).apply() }
    var chatStyle: Int
        get() = p.getInt("style", 0).coerceIn(0, 3)
        set(v) { p.edit().putInt("style", v.coerceIn(0, 3)).apply() }
    var speakReplies: Boolean
        get() = p.getBoolean("speak", false)
        set(v) { p.edit().putBoolean("speak", v).apply() }
    var conversationMode: Boolean
        get() = p.getBoolean("conversation", false)
        set(v) { p.edit().putBoolean("conversation", v).apply() }
    var automationPaused: Boolean
        get() = p.getBoolean("autopaused", false)
        set(v) { p.edit().putBoolean("autopaused", v).apply() }
    var simulationMode: Boolean
        get() = p.getBoolean("simulation", false)
        set(v) { p.edit().putBoolean("simulation", v).apply() }
    /** Tema: 0 = sistem, 1 = aydınlık, 2 = karanlık */
    var themeMode: Int
        get() = p.getInt("theme", 0).coerceIn(0, 2)
        set(v) { p.edit().putInt("theme", v.coerceIn(0, 2)).apply() }
    /** Konuşma hızı (0.5 … 2.0) ve seçili ses adı. */
    var speechRate: Float
        get() = p.getFloat("speech_rate", 1.0f).coerceIn(0.5f, 2.0f)
        set(v) { p.edit().putFloat("speech_rate", v.coerceIn(0.5f, 2.0f)).apply() }
    var voiceName: String?
        get() = p.getString("voice_name", null)
        set(v) { p.edit().putString("voice_name", v).apply() }
    fun model(pr: ProviderPreset) = p.getString("model_${pr.id}", null) ?: pr.model
    fun baseUrl(pr: ProviderPreset) = p.getString("url_${pr.id}", null) ?: pr.baseUrl
    fun save(pr: ProviderPreset, model: String, url: String) {
        p.edit().putString("model_${pr.id}", model.trim()).putString("url_${pr.id}", url.trim()).apply()
    }
}
