package com.zyren.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyren.app.core.Personality
import com.zyren.app.core.model.ProviderPresets

@Composable
fun SettingsScreen(vm: ZyrenViewModel) {
    val selected by vm.providerId.collectAsStateWithLifecycle()
    val fallback by vm.autoFallback.collectAsStateWithLifecycle()
    val models by vm.modelFiles.collectAsStateWithLifecycle()
    val res by vm.resourceInfo.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val humor by vm.humorLevel.collectAsStateWithLifecycle()
    val style by vm.chatStyle.collectAsStateWithLifecycle()
    val preset = ProviderPresets.byId(selected)
    var model by remember(selected) { mutableStateOf(vm.modelFor(preset)) }
    var url by remember(selected) { mutableStateOf(vm.urlFor(preset)) }
    var key by remember(selected) { mutableStateOf("") }
    var hasKey by remember(selected) { mutableStateOf(vm.hasKey(preset)) }
    var confirmChat by remember { mutableStateOf(false) }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importModel(uri) }
    LaunchedEffect(Unit) { vm.refreshResources(); vm.refreshModels() }
    LaunchedEffect(models) { if (selected == ProviderPresets.MEDIAPIPE) model = vm.modelFor(preset) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        SectionTitle("Model sağlayıcı")
        ProviderPresets.all.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { vm.selectProvider(p.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = p.id == selected, onClick = { vm.selectProvider(p.id) })
                Column { Text(p.name); if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall) }
            }
        }
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text(if (preset.onDevice) "Model dosyası adı" else "Model adı") }, singleLine = true)
        if (!preset.onDevice) OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Uç nokta (base URL)") }, singleLine = true)
        if (preset.needsKey) OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text(if (hasKey) "API anahtarı (kayıtlı · değiştirmek için yaz)" else "API anahtarı") },
            singleLine = true, visualTransformation = PasswordVisualTransformation())
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveProvider(preset, model, url, key); key = ""; hasKey = vm.hasKey(preset) }) { Text("Kaydet") }
            if (hasKey) OutlinedButton(onClick = { vm.removeKey(preset); hasKey = false }) { Text("Anahtarı sil") }
        }
        Text("Varsayılan model adları ve ücretsiz kota koşulları değişebilir; sağlayıcıdan doğrulayın. Anahtarlar şifreli saklanır.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("İnternet yoksa yerel modele geç")
                Text("Bulut modele ulaşılamazsa ve yerel model yüklüyse otomatik kullanılır.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = fallback, onCheckedChange = { vm.setAutoFallback(it) })
        }

        SectionTitle("Yerel model (cihaz içi)")
        Text("MediaPipe uyumlu .task dosyası (ör. Gemma 2B/3 küçük sürümleri) seçin. Dosya birkaç GB olabilir; RAM yetmezse Zyren reddeder.",
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickModel.launch(arrayOf("*/*")) }) { Text("Model dosyası seç") }
            OutlinedButton(onClick = { vm.releaseLocalModel() }) { Text("Bellekten bırak") }
        }
        if (models.isEmpty()) Text("Yüklü yerel model yok.", Modifier.padding(top = 4.dp))
        models.forEach { m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m, Modifier.weight(1f))
                TextButton(onClick = { vm.deleteModel(m) }) { Text("Sil") }
            }
        }

        SectionTitle("Kişilik ve mizah")
        Text("Mizah yoğunluğu", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Personality.humorNames.forEachIndexed { i, n -> FilterChip(selected = humor == i, onClick = { vm.setHumor(i) }, label = { Text(n) }) }
        }
        Text("Konuşma tarzı", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Personality.styleNames.forEachIndexed { i, n -> FilterChip(selected = style == i, onClick = { vm.setStyle(i) }, label = { Text(n) }) }
        }
        Text("Ciddi konularda mizah otomatik geri çekilir. Bu ayar yalnızca konuşma üslubunu değiştirir; izin ve güvenlik kurallarına dokunmaz.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))

        SectionTitle("Cihaz kaynakları")
        Text(res?.summary() ?: "Ölçülüyor…")
        OutlinedButton(onClick = { vm.refreshResources() }) { Text("Yenile") }

        SectionTitle("Hata raporu (son 30)")
        if (errors.isEmpty()) Text("Kayıtlı hata yok.")
        errors.forEach { e ->
            Column(Modifier.padding(vertical = 3.dp)) {
                Text("${fmtTime(e.createdAt)} · ${e.capability}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Text(e.outcome + " — " + e.detail, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionTitle("Sohbet")
        OutlinedButton(onClick = { confirmChat = true }) { Text("Sohbet geçmişini temizle") }
        SectionTitle("Hakkında")
        Text("Zyren 0.4.0 · Derleme ve cihaz testi bekliyor; durum için PROJECT_STATUS.md")
    }
    if (confirmChat) ConfirmDialog("Sohbeti temizle", "Sohbet geçmişi silinecek (hafıza etkilenmez).",
        { confirmChat = false }) { vm.clearChat(); confirmChat = false }
}
