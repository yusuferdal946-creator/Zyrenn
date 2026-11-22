package com.zyren.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TerminalScreen(vm: ZyrenViewModel) {
    val lines by vm.terminal.collectAsStateWithLifecycle()
    var cmd by rememberSaveable { mutableStateOf("") }
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.animateScrollToItem(lines.lastIndex) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("GÜVENLİ terminal: yalnızca çalışma klasöründe yerleşik komutlar (help yazın) + JS kum havuzu. Ham kabuk izole DEĞİLDİR, varsayılan kapalıdır.",
            style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), state = state) {
            items(lines.size) { i -> Text(lines[i], fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
        OutlinedTextField(cmd, { cmd = it }, Modifier.fillMaxWidth(), placeholder = { Text("komut veya JavaScript") })
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.runShell(cmd); cmd = "" }, enabled = cmd.isNotBlank()) { Text("Çalıştır") }
            Button(onClick = { vm.runJs(cmd); cmd = "" }, enabled = cmd.isNotBlank()) { Text("JS") }
            OutlinedButton(onClick = { vm.runRawShell(cmd); cmd = "" }, enabled = cmd.isNotBlank()) { Text("Ham ⚠") }
            OutlinedButton(onClick = { vm.clearTerminal() }) { Text("Temizle") }
        }
    }
}
