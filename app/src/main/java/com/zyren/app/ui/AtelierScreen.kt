package com.zyren.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyren.app.MainActivity
import com.zyren.app.atelier.MiniApp
import com.zyren.app.atelier.MiniAppGuard
import java.io.ByteArrayInputStream

const val MINIAPP_EXTRA = "zyren_miniapp"
private const val BASE = "https://zyren-miniapp.invalid/"

/** Atölye: istediğiniz uygulamayı yazın, Zyren üretsin; kum havuzunda çalışsın, sürümlensin, ana ekrana eklenebilsin. */
@Composable
fun AtelierScreen(vm: ZyrenViewModel) {
    val apps by vm.atelierApps.collectAsStateWithLifecycle()
    val busy by vm.atelierBusy.collectAsStateWithLifecycle()
    val msg by vm.atelierMsg.collectAsStateWithLifecycle()
    val open by vm.atelierOpen.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshApps() }
    val current = open?.let { id -> apps.firstOrNull { it.id == id } }

    if (current != null) {
        Viewer(vm, current, busy, msg)
        return
    }

    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var del by remember { mutableStateOf<MiniApp?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionTitle("Yeni uygulama")
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Uygulama adı (ör. Hesap Defteri)") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Ne yapmalı? Ne kadar ayrıntılı, o kadar iyi") }, minLines = 3)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && name.isNotBlank() && desc.isNotBlank(), onClick = { vm.createApp(name, desc); name = ""; desc = "" }) { Text("Zyren yapsın") }
                if (busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            }
            if (msg.isNotBlank()) Text(msg, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("HATA")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("Uygulamalar tek dosyalık ve kum havuzunda çalışır: internet, kamera, mikrofon, konum ve telefon erişimi YOKTUR. " +
                "Bunlar gerekiyorsa Zyren en yakın çalışan sürümü yapar ve sınırlamayı yazar. Her sürüm saklanır, geri alınabilir.",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            SectionTitle("Uygulamalarım")
            if (apps.isEmpty()) Text("Henüz uygulama yok.")
        }
        items(apps, key = { it.id }) { a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall)
                    Text(a.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text("sürüm ${a.current} · ${a.versions.size} kayıtlı", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.openMiniApp(a.id) }) { Text("Aç") }
                        OutlinedButton(onClick = { del = a }) { Text("Sil") }
                    }
                }
            }
        }
    }
    del?.let { a ->
        ConfirmDialog("Uygulamayı sil", "\"${a.name}\" çöp klasörüne taşınacak.", { del = null }) { vm.deleteApp(a.id); del = null }
    }
}

@Composable
private fun Viewer(vm: ZyrenViewModel, app: MiniApp, busy: Boolean, msg: String) {
    val ctx = LocalContext.current
    var error by remember(app.id, app.current) { mutableStateOf<String?>(null) }
    var revise by remember { mutableStateOf(false) }
    var versions by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    val html = remember(app.id, app.current) { vm.miniAppHtml(app.id) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.atelierOpen.value = null }) { Text("← Atölye") }
            Text("${app.name} · v${app.current}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = !busy, onClick = { revise = true }) { Text("Değiştir") }
            OutlinedButton(onClick = { versions = true }) { Text("Sürümler") }
            OutlinedButton(onClick = { pinShortcut(ctx, app) }) { Text("Ana ekrana") }
            OutlinedButton(onClick = { del = true }) { Text("Sil") }
        }
        if (msg.isNotBlank()) Text(msg, Modifier.padding(horizontal = 12.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
        error?.let { e ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Uygulama hata verdi: ${e.take(120)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = { vm.fixApp(app.id, e); error = null }) { Text("Zyren düzelt") }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (html == null) Text("Sürüm dosyası bulunamadı.", Modifier.padding(16.dp))
            else MiniAppView(app.id, app.current, html, { error = it }, Modifier.fillMaxSize())
        }
    }
    if (revise) {
        var req by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { revise = false }, title = { Text("Ne değişsin?") },
            text = { OutlinedTextField(req, { req = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Değişiklik isteği") }) },
            confirmButton = { TextButton(enabled = req.isNotBlank(), onClick = { vm.reviseApp(app.id, req); revise = false }) { Text("Uygula") } },
            dismissButton = { TextButton(onClick = { revise = false }) { Text("Vazgeç") } })
    }
    if (versions) {
        AlertDialog(onDismissRequest = { versions = false }, title = { Text("Sürümler") },
            text = {
                Column {
                    app.versions.sortedDescending().forEach { v ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sürüm $v" + if (v == app.current) " (şu an)" else "", Modifier.weight(1f))
                            if (v != app.current) TextButton(onClick = { vm.rollbackApp(app.id, v); versions = false }) { Text("Buna dön") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { versions = false }) { Text("Kapat") } })
    }
    if (del) ConfirmDialog("Uygulamayı sil", "\"${app.name}\" çöp klasörüne taşınacak.", { del = false }) {
        vm.deleteApp(app.id); vm.atelierOpen.value = null; del = false
    }
}

/**
 * Kilitli WebView: ağ yükleri kapalı, tüm istekler engelli (yalnızca ana belge), gezinme yok, izin istekleri reddedilir,
 * dosya/içerik erişimi kapalı, CSP enjekte edilir, JS hataları üst katmana bildirilir.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniAppView(id: String, version: Int, html: String, onError: (String) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val view = remember(id, version) {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.setGeolocationEnabled(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?) = true
                override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse =
                    WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.message().startsWith(MiniAppGuard.ERR_PREFIX)) onError(m.message().removePrefix(MiniAppGuard.ERR_PREFIX).trim())
                    return true
                }
                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                override fun onJsAlert(v: WebView?, url: String?, message: String?, result: JsResult): Boolean { result.confirm(); return true }
                override fun onJsConfirm(v: WebView?, url: String?, message: String?, result: JsResult): Boolean { result.confirm(); return true }
                override fun onJsPrompt(v: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean { result.cancel(); return true }
            }
            loadDataWithBaseURL(BASE + id + "/", MiniAppGuard.prepare(html), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(view) { onDispose { view.stopLoading(); view.destroy() } }
    AndroidView(factory = { view }, modifier = modifier)
}

/** Ana ekrana kısayol: dokununca Zyren açılır ve uygulama doğrudan Atölye'de çalışır. Launcher desteklemiyorsa söylenir. */
private fun pinShortcut(ctx: Context, app: MiniApp) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
        android.widget.Toast.makeText(ctx, "Bu başlatıcı ana ekrana kısayol eklemeyi desteklemiyor", android.widget.Toast.LENGTH_LONG).show(); return
    }
    val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(0xFF4A3FD0.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 110f; textAlign = Paint.Align.CENTER }
    cv.drawText(app.name.firstOrNull()?.uppercase() ?: "Z", 96f, 132f, p)
    val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MINIAPP_EXTRA, app.id)
    val info = ShortcutInfoCompat.Builder(ctx, "miniapp-" + app.id).setShortLabel(app.name.take(20)).setIcon(IconCompat.createWithBitmap(bmp)).setIntent(intent).build()
    ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
}
