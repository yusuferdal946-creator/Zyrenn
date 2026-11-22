package com.zyren.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyren.app.core.PermissionBridge
import com.zyren.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingReq: PermissionBridge.Req? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        pendingReq?.result?.complete(res.values.all { it }); pendingReq = null
    }

    private val vm: ZyrenViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMiniAppIntent(intent)
    }

    private fun handleMiniAppIntent(i: Intent?) {
        i?.getStringExtra(MINIAPP_EXTRA)?.takeIf { it.isNotBlank() }?.let { vm.openMiniApp(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PermissionBridge.requests.collect { r -> pendingReq = r; permLauncher.launch(r.perms) }
            }
        }
        setContent { ZyrenTheme { ZyrenRoot(vm) } }
        if (savedInstanceState == null) handleMiniAppIntent(intent)
    }
}

private val TABS = listOf("💬" to "Sohbet", "📁" to "Dosya", "🧠" to "Hafıza", "🎛️" to "Merkez", "🧩" to "Atölye", "🛡️" to "İzin", "⚙️" to "Ayar")

@Composable
fun ZyrenRoot(vm: ZyrenViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val notice by vm.notice.collectAsStateWithLifecycle()
    val approval by vm.approval.collectAsStateWithLifecycle()
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    val lockdown by vm.lockdown.collectAsStateWithLifecycle()
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val openApp by vm.atelierOpen.collectAsStateWithLifecycle()
    LaunchedEffect(openApp) { if (openApp != null) tab = 4 }
    LaunchedEffect(notice) { notice?.let { snack.showSnackbar(it); vm.notice.value = null } }

    if (!onboarded) {
        Surface(Modifier.fillMaxSize().systemBarsPadding()) { OnboardingScreen(vm) }
        return
    }

    approval?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.resolveApproval(false) },
            title = { Text("Onay gerekiyor") },
            text = { Text("${p.req.capability.label}\n\n${p.req.detail}") },
            confirmButton = { TextButton(onClick = { vm.resolveApproval(true) }) { Text("Onayla") } },
            dismissButton = { TextButton(onClick = { vm.resolveApproval(false) }) { Text("Reddet") } }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (!imeOpen) NavigationBar {
                TABS.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon) }, label = { Text(label, maxLines = 1, fontSize = 10.sp) })
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.panic() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("⛔ Acil durdur", fontSize = 12.sp) }
            }
            if (lockdown) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒 Kilit modu: şüpheli işlem algılandı, riskli işlemler durduruldu.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.unlock() }) { Text("Kilidi aç") }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> ChatScreen(vm)
                    1 -> FilesScreen(vm)
                    2 -> MemoryScreen(vm)
                    3 -> CenterScreen(vm)
                    4 -> AtelierScreen(vm)
                    5 -> PermissionsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
