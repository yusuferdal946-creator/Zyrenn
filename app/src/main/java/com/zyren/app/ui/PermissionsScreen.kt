package com.zyren.app.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyren.app.core.PermissionBridge
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.Risk
import com.zyren.app.phone.ZyrenAccessibilityService

@Composable
fun PermissionsScreen(vm: ZyrenViewModel) {
    val ctx = LocalContext.current
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val pluginOn by vm.pluginState.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    val used = usage.associate { it.capability to it.n }
    val androidPerms = listOf(
        "Mikrofon" to Manifest.permission.RECORD_AUDIO, "Kişiler" to Manifest.permission.READ_CONTACTS,
        "SMS gönder" to Manifest.permission.SEND_SMS, "Arama yap" to Manifest.permission.CALL_PHONE,
        "Arama geçmişi" to Manifest.permission.READ_CALL_LOG, "Takvim" to Manifest.permission.READ_CALENDAR
    )
    val notifOn = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true
    val a11yOn = ZyrenAccessibilityService.instance != null

    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        item { SectionTitle("Verilen izinler (Zyren içi)") }
        items(Capability.entries.toList()) { cap ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cap.label)
                    Text(cap.risk.label + " · ${used[cap.name] ?: 0} kez kullanıldı", style = MaterialTheme.typography.bodySmall,
                        color = if (cap.risk == Risk.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled[cap] == true, onCheckedChange = { vm.setPermission(cap, it) })
            }
        }
        item { SectionTitle("Telefon erişimleri (Android izinleri)") }
        items(androidPerms) { (label, perm) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Text(if (PermissionBridge.has(ctx, perm)) "✅ verildi" else "❌ verilmedi")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bildirim erişimi: " + if (notifOn) "✅ açık" else "❌ kapalı", Modifier.weight(1f))
                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Aç") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Erişilebilirlik: " + if (a11yOn) "✅ açık" else "❌ kapalı", Modifier.weight(1f))
                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Aç") }
            }
            Text("Android izinlerini istendiğinde verirsiniz; durum ekranı yenilemek için sekmeyi değiştirin. " +
                "Sideload (APK) kurulumda Android 13+ bazı izinleri engeller: Uygulama bilgisi > ⋮ > 'Kısıtlı ayarlara izin ver'.",
                style = MaterialTheme.typography.bodySmall)
        }
        item { SectionTitle("Erişebildiği dosyalar") }
        item { Text("Yalnızca uygulama içi çalışma alanı:\n${vm.workspacePath}\nGaleri/diğer dosyalara yalnızca sizin seçtiğiniz dosyalar kopyalanır.") }
        item { SectionTitle("Bağlı servisler") }
        item { Text("Seçili model sağlayıcı (Ayarlar), web araştırma (DuckDuckGo/Vikipedi), görsel üretme (Pollinations). Başkası yok.") }
        item { SectionTitle("Eklentiler (kapatırsanız araçları devre dışı kalır)") }
        items(vm.plugins) { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (p.trusted) " (yerleşik)" else " (güvenilmeyen)")
                    Text(p.description, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = pluginOn[p.id] ?: true, onCheckedChange = { vm.setPlugin(p.id, it) })
            }
        }
        item { SectionTitle("Son işlemler (kritikler kırmızı)") }
        if (audit.isEmpty()) item { Text("Henüz kayıt yok.") }
        items(audit, key = { it.id }) { a ->
            val crit = a.risk == Risk.CRITICAL.name
            Column(Modifier.padding(vertical = 3.dp)) {
                Text("${fmtTime(a.createdAt)} · ${a.outcome}", style = MaterialTheme.typography.labelSmall,
                    color = if (crit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(a.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
