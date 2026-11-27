package com.zyren.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ZyrenTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> sysDark }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun fmtTime(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(ms))

fun fmtSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "${b / (1024 * 1024)} MB"
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun InputDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onOk(v.trim()) }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
fun PasswordDialog(title: String, confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val err = when {
        p1.length < 10 -> "En az 10 karakter"
        p1.all { it.isDigit() } -> "Yalnızca rakam olmasın"
        confirm && p1 != p2 -> "Parolalar eşleşmiyor"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(p1, { p1 = it }, label = { Text("Parola") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                if (confirm) androidx.compose.material3.OutlinedTextField(p2, { p2 = it }, label = { Text("Parola (tekrar)") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text(err ?: "Parola hiçbir yerde saklanmaz. Kaybederseniz paket KURTARILAMAZ.", style = MaterialTheme.typography.bodySmall,
                    color = if (err != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { if (err == null) onOk(p1) }, enabled = err == null) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onOk) { Text("Evet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
