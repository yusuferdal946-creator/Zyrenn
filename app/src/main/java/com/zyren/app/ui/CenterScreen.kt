package com.zyren.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyren.app.agents.AgentRoles
import com.zyren.app.plan.TaskState
import com.zyren.app.quality.FeatureStatus
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Merkez: tüm modüllerin tek yerden yönetimi. Her bölüm gerçek bileşene bağlıdır (gösterme amaçlı sahte düğme yok). */
@Composable
fun CenterScreen(vm: ZyrenViewModel) {
    var section by rememberSaveable { mutableStateOf("Plan") }
    val names = listOf("Plan", "Bilgi", "Otomasyon", "Ajanlar", "Simülasyon", "İkiz", "Performans", "Kalite", "Araçlar", "Terminal", "3D")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { n -> FilterChip(selected = section == n, onClick = { section = n }, label = { Text(n) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (section) {
                "Plan" -> PlanSection(vm)
                "Bilgi" -> KnowledgeSection(vm)
                "Otomasyon" -> AutomationSection(vm)
                "Ajanlar" -> AgentSection(vm)
                "Simülasyon" -> SimSection(vm)
                "İkiz" -> TextSection(vm, listOf("Güncelle" to { vm.twinUpdate(); Unit }, "Göster" to { vm.twinShow() }, "Karşılaştır" to { vm.twinDiff() }),
                    "Telefon durumunun yerel anlık görüntüsü (uygulama adları, ayarlar, kaynaklar, izinler). Kişisel içerik kopyalanmaz.")
                "Performans" -> TextSection(vm, listOf("Rapor al" to { vm.perfReport(); Unit }), "RAM/CPU/pil/depolama, yavaşlama ve son 24 saat hataları; çözüm önerileriyle.")
                "Kalite" -> QualitySection(vm)
                "Araçlar" -> ToolsSection(vm)
                "Terminal" -> TerminalScreen(vm)
                else -> Nodes3D(vm)
            }
        }
    }
}

@Composable
private fun TextSection(vm: ZyrenViewModel, actions: List<Pair<String, () -> Unit>>, info: String) {
    val text by vm.centerText.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(info, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions.forEach { (l, f) -> Button(onClick = f) { Text(l) } } }
        Text(text.ifBlank { "(henüz çıktı yok)" })
    }
}

@Composable
private fun PlanSection(vm: ZyrenViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val goals = remember(tick) { vm.goals() }
    var add by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { add = true }) { Text("+ Hedef") }
                OutlinedButton(onClick = { vm.reschedule() }) { Text("Yeniden planla") }
            }
            Text("Hedefi bölmesi için sohbette \"şu hedef için plan yap\" deyin. 🤖 = görevi uzman ajanla yürüt (Bitti yapmaz). ✓ = sizin onayınızla Bitti.", style = MaterialTheme.typography.bodySmall)
        }
        items(goals, key = { it.id }) { g ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val done = g.tasks.count { it.state == TaskState.BITTI }
                    Text("${g.title} [${g.horizon}] $done/${g.tasks.size}", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { if (g.tasks.isEmpty()) 0f else done.toFloat() / g.tasks.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    g.tasks.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title)
                                Text("öncelik ${t.priority} · ${t.state.label}" + (if (t.note.isNotBlank()) " · ${t.note}" else ""), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.runTask(t.id) }) { Text("🤖") }
                            TextButton(onClick = { vm.setTask(t.id, TaskState.DEVAM) }) { Text("▶") }
                            TextButton(onClick = { vm.setTask(t.id, TaskState.BITTI) }) { Text("✓") }
                            TextButton(onClick = { vm.setTask(t.id, TaskState.BASARISIZ) }) { Text("✗") }
                        }
                    }
                }
            }
        }
    }
    if (add) {
        var title by remember { mutableStateOf("") }
        var horizon by remember { mutableStateOf("haftalik") }
        var lines by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Yeni hedef") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Hedef") })
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("gunluk", "haftalik", "uzun").forEach { h -> FilterChip(selected = horizon == h, onClick = { horizon = h }, label = { Text(h) }) } }
                    OutlinedTextField(lines, { lines = it }, label = { Text("Görevler: her satır  başlık;öncelik;gün") }, minLines = 3)
                }
            },
            confirmButton = { TextButton(onClick = { vm.addGoal(title, horizon, lines); add = false }) { Text("Ekle") } },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Vazgeç") } })
    }
}

@Composable
private fun KnowledgeSection(vm: ZyrenViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val list = remember(tick, q) { vm.kbItems(q) }
    var add by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, Modifier.weight(1f), placeholder = { Text("Ara…") }, singleLine = true)
                Button(onClick = { add = true }) { Text("+") }
            }
            Text("Araştırmayı sohbette \"şunu araştır ve bilgi merkezine kaydet\" diyerek de ekleyebilirsiniz.", style = MaterialTheme.typography.bodySmall)
        }
        items(list, key = { it.id }) { i ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(vm.kbLabel(i), style = MaterialTheme.typography.bodyMedium)
                    Text(i.content.take(160), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { info = vm.kbCompare(i.id) }) { Text("Karşılaştır") }
                        TextButton(onClick = { vm.kbRemove(i.id) }) { Text("Sil") }
                    }
                }
            }
        }
    }
    info?.let { AlertDialog(onDismissRequest = { info = null }, title = { Text("Sürüm farkı") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { info = null }) { Text("Tamam") } }) }
    if (add) {
        var t by remember { mutableStateOf("") }; var c by remember { mutableStateOf("") }; var u by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Bilgi kaydet") },
            text = { Column { OutlinedTextField(t, { t = it }, label = { Text("Başlık") }); OutlinedTextField(u, { u = it }, label = { Text("Kaynak bağlantısı (varsa)") }); OutlinedTextField(c, { c = it }, label = { Text("İçerik") }, minLines = 3) } },
            confirmButton = { TextButton(onClick = { vm.kbAdd(t, c, u); add = false }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Vazgeç") } })
    }
}

@Composable
private fun AutomationSection(vm: ZyrenViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val paused by vm.automationPaused.collectAsStateWithLifecycle()
    val rules = remember(tick) { vm.rules() }
    val hist = remember(tick) { vm.autoHistory() }
    var add by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("⛔ Tüm otomasyonları duraklat", style = MaterialTheme.typography.titleSmall)
                    Text("Açıkken hiçbir kural çalışmaz.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = paused, onCheckedChange = { vm.setPaused(it) })
            }
            Text("Arka planda yalnızca güvenli araçlar çalışır (kritik olanlar atlanır). Çalışması için İzin sekmesinde 'Arka planda otomasyon' açık olmalı. En sık 15 dk.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { add = true }) { Text("+ Kural") }
                OutlinedButton(onClick = { vm.autoRunNow() }) { Text("Zamanı gelenleri şimdi çalıştır") }
            }
        }
        items(rules, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name); Text("${r.trigger} → ${r.tool} · çalışma ${r.runs} · hata ${r.failures}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = r.enabled, onCheckedChange = { vm.autoToggle(r.id, it) })
                    TextButton(onClick = { vm.autoRemove(r.id) }) { Text("Sil") }
                }
            }
        }
        item { Text("İşlem geçmişi", style = MaterialTheme.typography.titleSmall) }
        items(hist.size) { i -> Text(hist[i].replace('|', ' ').take(160), style = MaterialTheme.typography.bodySmall) }
    }
    if (add) {
        var n by remember { mutableStateOf("") }; var tr by remember { mutableStateOf("gunluk:08:00") }; var tool by remember { mutableStateOf("pil_durumu") }; var a by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Yeni otomasyon kuralı") },
            text = { Column {
                OutlinedTextField(n, { n = it }, label = { Text("Ad") })
                OutlinedTextField(tr, { tr = it }, label = { Text("Tetikleyici: gunluk:HH:MM veya aralik:DAKIKA") })
                OutlinedTextField(tool, { tool = it }, label = { Text("Araç adı") })
                OutlinedTextField(a, { a = it }, label = { Text("Argümanlar (JSON, boş olabilir)") })
            } },
            confirmButton = { TextButton(onClick = { vm.autoAdd(n, tr, tool, a); add = false }) { Text("Ekle (onay ister)") } },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Vazgeç") } })
    }
}

@Composable
private fun AgentSection(vm: ZyrenViewModel) {
    val out by vm.agentOut.collectAsStateWithLifecycle()
    var role by rememberSaveable { mutableStateOf(AgentRoles.all.first().id) }
    var task by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Uzman ajanlar yalnızca kendi rollerinin araçlarını kullanır; izin ve onaylar aynı merkezi sistemden geçer. Bir model/sağlayıcı ve API anahtarı gerekir.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AgentRoles.all.forEach { r -> FilterChip(selected = role == r.id, onClick = { role = r.id }, label = { Text(r.name) }) }
        }
        OutlinedTextField(task, { task = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Görev") }, minLines = 2)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runAgent(role, task) }, enabled = task.isNotBlank()) { Text("Ajanı çalıştır") }
            OutlinedButton(onClick = { vm.stopAgent() }) { Text("⛔ Durdur") }
        }
        Text(out, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SimSection(vm: ZyrenViewModel) {
    val sim by vm.simulation.collectAsStateWithLifecycle()
    var tool by rememberSaveable { mutableStateOf("sms_gonder") }
    var args by rememberSaveable { mutableStateOf("{\"numara\":\"0555\",\"metin\":\"merhaba\"}") }
    var res by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🧪 Simülasyon modu", style = MaterialTheme.typography.titleSmall)
                Text("Açıkken sohbet ve ajanlar araçları GERÇEKTEN çalıştırmaz; izin, onay ve ikiz tahminini raporlar.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = sim, onCheckedChange = { vm.setSimulation(it) })
        }
        Text("Tek araç denemesi", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(tool, { tool = it }, Modifier.fillMaxWidth(), label = { Text("Araç adı") }, singleLine = true)
        OutlinedTextField(args, { args = it }, Modifier.fillMaxWidth(), label = { Text("Argümanlar (JSON)") })
        Button(onClick = { res = vm.simulate(tool, args) }, modifier = Modifier.padding(top = 8.dp)) { Text("Simüle et") }
        Text(res, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun QualitySection(vm: ZyrenViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val text by vm.centerText.collectAsStateWithLifecycle()
    val feats = remember(tick) { vm.features() }
    var edit by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Durum yalnızca kanıtla yükselir. Otomatik testler durumu yükseltmez; 'Cihazda test edildi' ve 'Tamam' yalnızca sizin onayınızla verilir.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.runTests() }, modifier = Modifier.padding(vertical = 6.dp)) { Text("Bu cihazda kendi kendine testleri çalıştır") }
            if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall)
        }
        items(feats, key = { it.id }) { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name)
                    Text(f.status.label + (if (f.lastResult.isNotBlank()) " · ${f.lastResult}" else ""), style = MaterialTheme.typography.bodySmall,
                        color = if (f.lastResult.startsWith("BAŞARISIZ")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { edit = f.id }) { Text("Durum…") }
            }
        }
    }
    edit?.let { id ->
        var st by remember { mutableStateOf(FeatureStatus.DERLENDI) }
        var note by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Durumu doğrula") },
            text = { Column {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FeatureStatus.entries.forEach { s -> FilterChip(selected = st == s, onClick = { st = s }, label = { Text(s.label, fontSize = 11.sp) }) }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Kanıt notu (ne denendi, sonuç)") }, minLines = 2)
            } },
            confirmButton = { TextButton(onClick = { vm.featureSet(id, st, note); edit = null }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Vazgeç") } })
    }
}

@Composable
private fun ToolsSection(vm: ZyrenViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val custom = remember(tick) { vm.customTools() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Özel araçlar (saf JS; dosya/ağ erişimi yok; testi geçmeden ve onayınız olmadan eklenmez)", style = MaterialTheme.typography.titleSmall)
        if (custom.isEmpty()) Text("Henüz yok. Sohbette \"şunu yapan bir araç geliştir\" deyin.", style = MaterialTheme.typography.bodySmall)
        custom.forEach { t -> Row(verticalAlignment = Alignment.CenterVertically) { Text("${t.name} — ${t.description}", Modifier.weight(1f)); TextButton(onClick = { vm.removeCustom(t.name) }) { Text("Sil") } } }
        Text("Etkin araçlar", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
        Text(vm.toolDocs(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Nodes3D(vm: ZyrenViewModel) {
    val tick by vm.tick.collectAsStateWithLifecycle()
    val feats = remember(tick) { vm.features() }
    var yaw by remember { mutableFloatStateOf(0.5f) }
    var pitch by remember { mutableFloatStateOf(0.3f) }
    var sel by remember { mutableStateOf<Int?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val n = feats.size.coerceAtLeast(1)
    fun project(i: Int, w: Float, h: Float): Triple<Float, Float, Float> {
        val y = 1f - 2f * (i + 0.5f) / n
        val r = sqrt(1f - y * y)
        val phi = i * 2.3999632f
        val x = cos(phi) * r; val z = sin(phi) * r
        val x1 = x * cos(yaw) + z * sin(yaw); val z1 = -x * sin(yaw) + z * cos(yaw)
        val y2 = y * cos(pitch) - z1 * sin(pitch); val z2 = y * sin(pitch) + z1 * cos(pitch)
        val s = 1.6f / (2.2f - z2); val rad = minOf(w, h) * 0.38f
        return Triple(w / 2 + x1 * rad * s, h / 2 + y2 * rad * s, z2)
    }
    fun colorOf(s: FeatureStatus) = when (s) {
        FeatureStatus.PLANLANDI -> Color(0xFF9E9E9E); FeatureStatus.KOD_YAZILDI -> Color(0xFF42A5F5); FeatureStatus.DERLENDI -> Color(0xFFFFCA28)
        FeatureStatus.CIHAZDA_TEST_EDILDI -> Color(0xFF66BB6A); FeatureStatus.TAMAM -> Color(0xFF2E7D32)
    }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Sürükleyerek döndürün, düğüme dokunun. Renk = gerçek durum: gri planlandı · mavi kod yazıldı · sarı derlendi · yeşil cihazda test edildi/tamam. (Telefon ekranında 3D görselleştirme; gerçek hologram değildir.)", style = MaterialTheme.typography.bodySmall)
        Box(
            Modifier.fillMaxWidth().weight(1f).onSizeChanged { size = it }
                .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); yaw += d.x / 180f; pitch = (pitch + d.y / 180f).coerceIn(-1.3f, 1.3f) } }
                .pointerInput(feats.size) {
                    detectTapGestures { p ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        sel = feats.indices.minByOrNull { i -> val (px, py, _) = project(i, w, h); (px - p.x) * (px - p.x) + (py - p.y) * (py - p.y) }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width.toFloat(); val h = size.height.toFloat()
                val c = Offset(w / 2, h / 2)
                val order = feats.indices.sortedBy { project(it, w, h).third }
                for (i in order) {
                    val (px, py, z) = project(i, w, h)
                    drawLine(Color.Gray.copy(alpha = 0.15f + 0.2f * (z + 1f) / 2f), c, Offset(px, py), strokeWidth = 1.5f)
                }
                drawCircle(Color(0xFF7E57C2), 16f, c)
                for (i in order) {
                    val (px, py, z) = project(i, w, h)
                    drawCircle(colorOf(feats[i].status), 8f + 5f * z, Offset(px, py))
                    if (sel == i) drawCircle(Color.White, 14f + 5f * z, Offset(px, py), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                }
            }
            feats.indices.forEach { i ->
                val (px, py, z) = project(i, size.width.toFloat(), size.height.toFloat())
                if (z > -0.1f) Text(feats[i].name.take(16), fontSize = 9.sp, modifier = Modifier.offset { IntOffset(px.toInt() + 10, py.toInt() - 8) })
            }
        }
        sel?.let { i -> feats.getOrNull(i)?.let { f -> Text("${f.name}\nDurum: ${f.status.label}\n${f.lastResult.ifBlank { "otomatik test sonucu yok" }}" + if (f.note.isNotBlank()) "\nNot: ${f.note}" else "") } }
    }
}
