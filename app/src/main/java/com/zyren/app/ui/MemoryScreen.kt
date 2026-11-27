package com.zyren.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyren.app.core.memory.MemoryEntity
import com.zyren.app.core.memory.MemoryRepository

@Composable
fun MemoryScreen(vm: ZyrenViewModel) {
    val list by vm.memories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = if (query.isBlank()) list else list.filter {
        it.content.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Ara…") }, singleLine = true)
            Button(onClick = { adding = true }) { Text("+ Ekle") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = list.isNotEmpty()) { Text("Tümünü sil") }
        }
        Text("${filtered.size}/${list.size} kayıt · sabitlenenler otomatik silinmez",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        if (list.isEmpty()) Text("Hafıza boş. Sohbette \"hatırla: ...\" yaz veya + Ekle'ye bas.")
        else if (filtered.isEmpty()) Text("Arama sonucu yok.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text((if (m.pinned) "📌 " else "") + m.category, style = MaterialTheme.typography.labelMedium)
                        Text(m.content, Modifier.padding(vertical = 4.dp))
                        Row {
                            TextButton(onClick = { editing = m }) { Text("Düzenle") }
                            TextButton(onClick = { vm.pinMemory(m) }) { Text(if (m.pinned) "Sabitlemeyi kaldır" else "Sabitle") }
                            TextButton(onClick = { vm.deleteMemory(m) }) { Text("Sil") }
                        }
                    }
                }
            }
        }
    }

    if (adding) MemoryDialog(null, { adding = false }) { cat, text -> vm.addMemory(cat, text); adding = false }
    editing?.let { m -> MemoryDialog(m, { editing = null }) { cat, text -> vm.editMemory(m, cat, text); editing = null } }
    if (confirmClear) ConfirmDialog("Hafızayı temizle", "Tüm hafıza kayıtları (sabitlenenler dahil) silinecek.",
        { confirmClear = false }) { vm.clearMemory(); confirmClear = false }
}

@Composable
private fun MemoryDialog(m: MemoryEntity?, onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var cat by remember { mutableStateOf(m?.category ?: MemoryRepository.CATEGORIES.first()) }
    var text by remember { mutableStateOf(m?.content ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (m == null) "Hafızaya ekle" else "Kaydı düzenle") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemoryRepository.CATEGORIES.take(3).forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c, maxLines = 1) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemoryRepository.CATEGORIES.drop(3).forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c, maxLines = 1) })
                    }
                }
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("İçerik") })
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(cat, text) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
