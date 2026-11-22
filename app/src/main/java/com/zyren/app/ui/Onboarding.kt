package com.zyren.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.Risk
import com.zyren.app.core.security.defaultEnabled

/** İlk kurulum: her yetki açıkça gösterilir; riskli ve hassas olanlar KAPALI başlar. Android ayrıca kendi izin pencerelerini gösterir. */
@Composable
fun OnboardingScreen(vm: ZyrenViewModel) {
    val state = remember { mutableStateMapOf<Capability, Boolean>().apply { Capability.entries.forEach { put(it, defaultEnabled(it)) } } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Zyren'e hangi yetkileri vermek istiyorsunuz?", style = MaterialTheme.typography.headlineSmall)
            Text("Hassas ve riskli yetkiler KAPALI başlar. Açtığınız yetkiler için Android ayrıca kendi izin penceresini gösterir. " +
                "Kritik işlemler (SMS, arama, silme, üzerine yazma…) her seferinde onayınızı ister. İstediğiniz zaman İzin sekmesinden değiştirebilirsiniz.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        items(Capability.entries.sortedBy { it.risk.ordinal }) { cap ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cap.label, fontWeight = FontWeight.SemiBold)
                    Text(cap.explain, style = MaterialTheme.typography.bodySmall)
                    Text(cap.risk.label, style = MaterialTheme.typography.labelSmall,
                        color = if (cap.risk == Risk.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state[cap] == true, onCheckedChange = { state[cap] = it })
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.finishOnboarding(state.toMap()) }, modifier = Modifier.fillMaxWidth()) { Text("Seçimlerimle başla") }
            OutlinedButton(onClick = { Capability.entries.forEach { state[it] = false } }, modifier = Modifier.fillMaxWidth()) { Text("Hepsini kapat (sonra tek tek açarım)") }
        }
    }
}
