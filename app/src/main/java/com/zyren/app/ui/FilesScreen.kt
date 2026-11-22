package com.zyren.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyren.app.core.workspace.Workspace

@Composable
fun FilesScreen(vm: ZyrenViewModel) {
    val ed by vm.editor.collectAsStateWithLifecycle()
    val current = ed
    if (current != null) { EditorView(vm, current); return }

    val dir by vm.dir.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val backupInfo by vm.backupInfo.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Workspace.Entry?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importFile(uri) }
    val importPkg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importPackage(uri) }
    var pass by remember { mutableStateOf<String?>(null) }
    var passMode by remember { mutableStateOf("") }
    val encExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = pass; pass = null
        if (uri != null && p != null) vm.exportEncrypted(uri, p)
    }
    val encImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val p = pass; pass = null
        if (uri != null && p != null) vm.importEncrypted(uri, p)
    }
    val shown = results ?: entries

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Çalışma alanı: /$dir", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.up() }, enabled = dir.isNotEmpty() || results != null) { Text("↑ Yukarı") }
            Button(onClick = { dialog = "file" }) { Text("+ Dosya") }
            Button(onClick = { dialog = "dir" }) { Text("+ Klasör") }
            OutlinedButton(onClick = { dialog = "search" }) { Text("Ara") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.backupNow() }) { Text("Yedek al") }
            OutlinedButton(onClick = { export.launch("zyren-yedek.zip") }) { Text("ZIP dışa aktar (ŞİFRESİZ)") }
            OutlinedButton(onClick = { dialog = "restore" }) { Text("Yedekten geri yükle") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importFile.launch(arrayOf("*/*")) }) { Text("Dosya içe aktar") }
            OutlinedButton(onClick = { importPkg.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Paket içe aktar (ZIP)") }
            OutlinedButton(onClick = { passMode = "export" }) { Text("🔒 Şifreli dışa aktar") }
            OutlinedButton(onClick = { passMode = "import" }) { Text("🔒 Şifreli paket içe aktar") }
        }
        Text(backupInfo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        if (results != null) Text("Arama sonuçları (${results?.size})", style = MaterialTheme.typography.labelLarge)
        if (shown.isEmpty()) Text("Burada henüz bir şey yok.")
        LazyColumn {
            items(shown, key = { it.path }) { e ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (e.isDir) vm.openDir(e.path) else vm.openFile(e.path) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (e.isDir) "📁" else "📄", Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (results != null) e.path else e.name)
                        if (!e.isDir) Text(fmtSize(e.size) + " · " + fmtTime(e.modified), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { toDelete = e }) { Text("Sil") }
                }
                HorizontalDivider()
            }
        }
    }

    when (dialog) {
        "file" -> InputDialog("Yeni dosya", "Ad (ör. notlar.md)", onDismiss = { dialog = null }) { vm.newFile(it); dialog = null }
        "dir" -> InputDialog("Yeni klasör", "Ad", onDismiss = { dialog = null }) { vm.makeDir(it); dialog = null }
        "search" -> InputDialog("Dosya ara", "Ad veya içerik", onDismiss = { dialog = null }) { vm.search(it); dialog = null }
        "restore" -> ConfirmDialog("Yedekten geri yükle",
            "Çalışma alanı son yedekle DEĞİŞTİRİLECEK. Öncesinde otomatik güvenlik yedeği alınır.",
            { dialog = null }) { vm.restoreLatestBackup(); dialog = null }
    }
    if (passMode == "export") PasswordDialog("Şifreli dışa aktar (AES-256-GCM)", confirm = true, onDismiss = { passMode = "" }) { p -> pass = p; passMode = ""; encExport.launch("zyren-sifreli.zyrenenc") }
    if (passMode == "import") PasswordDialog("Şifreli paketi aç", confirm = false, onDismiss = { passMode = "" }) { p -> pass = p; passMode = ""; encImport.launch(arrayOf("*/*")) }
    toDelete?.let { e ->
        ConfirmDialog("Sil", "\"${e.name}\" çöp kutusuna taşınacak.", { toDelete = null }) { vm.deleteEntry(e); toDelete = null }
    }
}

@Composable
private fun EditorView(vm: ZyrenViewModel, ed: EditorState) {
    var text by remember(ed.path) { mutableStateOf(ed.text) }
    var showVersions by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("/${ed.path}" + if (ed.isNew) "  (yeni)" else "", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(text, { text = it }, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveFile(text) }) { Text("Kaydet") }
            OutlinedButton(onClick = { vm.closeEditor() }) { Text("Kapat") }
            if (ed.versions.isNotEmpty()) OutlinedButton(onClick = { showVersions = true }) { Text("Sürümler (${ed.versions.size})") }
        }
    }
    if (showVersions) AlertDialog(
        onDismissRequest = { showVersions = false },
        title = { Text("Önceki sürümler") },
        text = {
            LazyColumn {
                items(ed.versions) { v ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(v), Modifier.weight(1f))
                        TextButton(onClick = { vm.restoreVersion(ed.path, v); showVersions = false }) { Text("Geri dön") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showVersions = false }) { Text("Kapat") } }
    )
}
