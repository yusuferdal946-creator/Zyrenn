package com.zyren.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyren.app.core.memory.ChatEntity

@Composable
fun ChatScreen(vm: ZyrenViewModel) {
    val msgs by vm.messages.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val activeCid by vm.activeConversation.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val voiceState by vm.voiceState.collectAsStateWithLifecycle()
    val img by vm.pendingImage.collectAsStateWithLifecycle()
    val speak by vm.speakReplies.collectAsStateWithLifecycle()
    val conv by vm.conversationMode.collectAsStateWithLifecycle()
    val sim by vm.simulation.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var showConvs by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.attachImage(uri) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importFile(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) vm.attachImageBitmap(bmp) }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        // Çoklu sohbet çubuğu
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sohbet #${activeCid}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { showConvs = !showConvs }) { Text(if (showConvs) "✕ Kapat" else "🕘 Geçmiş") }
            TextButton(onClick = { vm.newConversation() }) { Text("＋ Yeni") }
        }
        if (showConvs) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    (listOf(0L) + conversations.filter { it != 0L }).forEach { cid ->
                        Row(Modifier.fillMaxWidth().clickable { vm.selectConversation(cid); showConvs = false }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (cid == 0L) "Varsayılan sohbet" else "Sohbet #$cid", Modifier.weight(1f))
                            if (cid != 0L) TextButton(onClick = { vm.deleteConversation(cid) }) { Text("Sil") }
                        }
                    }
                }
            }
        }
        if (msgs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Merhaba, ben Zyren.\nÖnce Ayarlar'dan bir model ve API anahtarı ekle.\n" +
                        "Örnekler: \"pil kaç?\", \"YouTube'u aç\", \"Ahmet'e yarın 5'te görüşelim diye mesaj gönder\", \"hatırla: ...\"",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(msgs, key = { it.id }) { Bubble(it) }
            }
        }
        if (sending) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(progress, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall) }
        if (voiceState.isNotBlank()) Text("🎤 $voiceState", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = speak, onClick = { vm.setSpeak(!speak) }, label = { Text("🔊 Sesli yanıt") })
            FilterChip(selected = conv, onClick = { vm.setConversation(!conv) }, label = { Text("🔁 Konuşma modu") })
            FilterChip(selected = sim, onClick = { vm.setSimulation(!sim) }, label = { Text("🧪 Simülasyon") })
            if (img != null) AssistChip(onClick = { vm.dropImage() }, label = { Text("📷 eklendi ✕") })
        }
        // Mesaj giriş alanı: tam genişlik, sağda gönder, boşken ses, dosya+kamera
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Mesaj yaz…") }, maxLines = 4)
            IconButton(onClick = { picker.launch("image/*") }) { Text("📷") }
            IconButton(onClick = { camera.launch(null) }) { Text("📸") }
            IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("📎") }
            if (input.isBlank()) {
                IconButton(onClick = { vm.listen() }) { Text("🎤") }
            } else {
                Button(onClick = { vm.send(input); input = "" }, enabled = !sending) { Text("Gönder") }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatEntity) {
    val cs = MaterialTheme.colorScheme
    val user = m.role == "user"
    val bg = when (m.role) { "user" -> cs.primaryContainer; "error" -> cs.errorContainer; "tool" -> cs.tertiaryContainer; else -> cs.surfaceVariant }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 360.dp)) {
            SelectionContainer {
                Column(Modifier.padding(12.dp)) {
                    if (m.role == "tool") {
                        Text(m.content, style = MaterialTheme.typography.bodySmall)
                    } else {
                        MarkdownText(m.content)
                    }
                }
            }
        }
    }
}

/** Basit markdown renderer: başlıklar, kalın, italik, kod blokları, bağlantılar. */
@Composable
private fun MarkdownText(md: String) {
    val lines = md.split("\n")
    var inCode = false
    val codeBuf = StringBuilder()
    Column(Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    if (inCode) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(codeBuf.toString().trimEnd(), Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        codeBuf.clear()
                        inCode = false
                    } else inCode = true
                }
                inCode -> codeBuf.append(line).append('\n')
                t.startsWith("# ") -> Text(t.removePrefix("# "), style = MaterialTheme.typography.titleMedium)
                t.startsWith("## ") -> Text(t.removePrefix("## "), style = MaterialTheme.typography.titleSmall)
                t.startsWith("### ") -> Text(t.removePrefix("### "), style = MaterialTheme.typography.titleSmall)
                t.startsWith("- ") || t.startsWith("* ") -> Text("• " + t.drop(2), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    // Kalın **...** ve italik *...* (basit)
                    var text = line
                    text = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                    text = text.replace(Regex("__(.+?)__"), "$1")
                    // Bağlantı [metin](url)
                    text = text.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1 ($2)")
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (inCode && codeBuf.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(codeBuf.toString().trimEnd(), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
