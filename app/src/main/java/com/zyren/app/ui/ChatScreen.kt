package com.zyren.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val sending by vm.sending.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val voiceState by vm.voiceState.collectAsStateWithLifecycle()
    val img by vm.pendingImage.collectAsStateWithLifecycle()
    val speak by vm.speakReplies.collectAsStateWithLifecycle()
    val conv by vm.conversationMode.collectAsStateWithLifecycle()
    val sim by vm.simulation.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.attachImage(uri) }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex) }

    Column(Modifier.fillMaxSize()) {
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
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Mesaj yaz…") }, maxLines = 4)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { picker.launch("image/*") }) { Text("📷") }
            TextButton(onClick = { vm.listen() }) { Text("🎤") }
            Button(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !sending) { Text("Gönder") }
        }
    }
}

@Composable
private fun Bubble(m: ChatEntity) {
    val cs = MaterialTheme.colorScheme
    val user = m.role == "user"
    val bg = when (m.role) { "user" -> cs.primaryContainer; "error" -> cs.errorContainer; "tool" -> cs.tertiaryContainer; else -> cs.surfaceVariant }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            SelectionContainer {
                Text(m.content, Modifier.padding(12.dp),
                    style = if (m.role == "tool") MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
