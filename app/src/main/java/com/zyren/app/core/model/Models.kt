package com.zyren.app.core.model

import android.content.Context
import com.zyren.app.core.net.NetUtil
import com.zyren.app.core.res.ResourceMonitor
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String, val images: List<String> = emptyList())
data class ProviderConfig(val baseUrl: String, val model: String, val apiKey: String?)

data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val needsKey: Boolean,
    val isLocal: Boolean,
    val onDevice: Boolean = false,
    val note: String = ""
)

/** Model adları ve adresler varsayılandır; Ayarlar'dan değiştirilebilir (güncel olduklarını doğrulayın). */
object ProviderPresets {
    const val MEDIAPIPE = "local_mediapipe"
    val all = listOf(
        ProviderPreset("groq", "Groq (bulut, ücretsiz katman)", "https://api.groq.com/openai/v1",
            "llama-3.3-70b-versatile", needsKey = true, isLocal = false),
        ProviderPreset("openrouter", "OpenRouter (bulut, :free modeller)", "https://openrouter.ai/api/v1",
            "meta-llama/llama-3.3-70b-instruct:free", needsKey = true, isLocal = false),
        ProviderPreset("gemini", "Google Gemini (bulut, ücretsiz katman, görsel destekli)",
            "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash",
            needsKey = true, isLocal = false),
        ProviderPreset(MEDIAPIPE, "Yerel model (cihaz içi, MediaPipe .task)", "", "",
            needsKey = false, isLocal = true, onDevice = true,
            note = "Model dosyasını Ayarlar'dan içe aktarın. Araç kullanımı yok, yalnızca sohbet."),
        ProviderPreset("local_server", "Yerel sunucu (llama.cpp/Ollama; telefon veya PC)",
            "http://127.0.0.1:8080/v1", "default", needsKey = false, isLocal = true,
            note = "Aynı telefondaki sunucu (127.0.0.1) http ile çalışır. PC/LAN sunucusu için https gerekir (ör. ters vekil, Tailscale)." )
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

interface ModelProvider {
    suspend fun chat(messages: List<ChatMessage>, config: ProviderConfig): Result<String>
}

/** OpenAI uyumlu /chat/completions uç noktası. */
class OpenAiCompatProvider : ModelProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(messages: List<ChatMessage>, config: ProviderConfig): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("model", config.model)
                    put("messages", buildJsonArray {
                        messages.forEach { m ->
                            add(buildJsonObject {
                                put("role", m.role)
                                if (m.images.isEmpty()) put("content", m.content)
                                else put("content", buildJsonArray {
                                    add(buildJsonObject { put("type", "text"); put("text", m.content) })
                                    m.images.forEach { b64 ->
                                        add(buildJsonObject {
                                            put("type", "image_url")
                                            put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$b64") })
                                        })
                                    }
                                })
                            })
                        }
                    })
                }.toString()
                val url = (config.baseUrl.trimEnd('/') + "/chat/completions").toHttpUrl()
                if (!url.isHttps && !NetUtil.isCleartextAllowed(url.host)) error("Güvenlik: http yalnızca bu telefondaki yerel sunucuda (127.0.0.1/localhost) izinli; ağdaki sunucular için https kullanın")
                val req = Request.Builder().url(url)
                    .apply { if (!config.apiKey.isNullOrBlank()) header("Authorization", "Bearer ${config.apiKey}") }
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
                    Json.parseToJsonElement(text).jsonObject["choices"]!!.jsonArray[0]
                        .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
                }
            }
        }
}

/** Cihaz içi model (MediaPipe LLM Inference). Dosya: filesDir/models/<ad>. RAM yetersizse reddeder. */
class LocalLlmProvider(private val context: Context, private val resources: ResourceMonitor) : ModelProvider {
    private val lock = Any()
    private var llm: LlmInference? = null
    private var loadedPath: String? = null

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }
    fun modelFiles(): List<String> = modelsDir().listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()

    override suspend fun chat(messages: List<ChatMessage>, config: ProviderConfig): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                val file = File(modelsDir(), config.model)
                require(file.isFile) { "Yerel model dosyası yok. Ayarlar > Yerel model bölümünden .task dosyası içe aktarın." }
                val availBytes = resources.snapshot().ramAvailMb * 1048576
                require(availBytes > file.length() * 11 / 10) {
                    "Bu model şu an boş RAM'e sığmıyor (model ${file.length() / 1048576} MB, boş ${availBytes / 1048576} MB). Bulut modele geçin veya arka plan uygulamalarını kapatın."
                }
                val engine = synchronized(lock) {
                    if (llm == null || loadedPath != file.path) {
                        llm?.close()
                        llm = LlmInference.createFromOptions(
                            context,
                            LlmInference.LlmInferenceOptions.builder().setModelPath(file.path).setMaxTokens(2048).build()
                        )
                        loadedPath = file.path
                    }
                    llm!!
                }
                engine.generateResponse(gemmaPrompt(messages))
            }
        }

    fun release() = synchronized(lock) { llm?.close(); llm = null; loadedPath = null }

    private fun gemmaPrompt(messages: List<ChatMessage>): String {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val turns = messages.filter { it.role != "system" }.takeLast(6)
        val sb = StringBuilder()
        var first = true
        for (m in turns) {
            val role = if (m.role == "assistant") "model" else "user"
            val text = if (first && role == "user" && system.isNotBlank()) system + "\n\n" + m.content else m.content
            first = false
            sb.append("<start_of_turn>").append(role).append('\n').append(text).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}

class ProviderRegistry(context: Context, resources: ResourceMonitor) {
    private val compat = OpenAiCompatProvider()
    val local = LocalLlmProvider(context, resources)
    fun get(p: ProviderPreset): ModelProvider = if (p.id == ProviderPresets.MEDIAPIPE) local else compat
}
