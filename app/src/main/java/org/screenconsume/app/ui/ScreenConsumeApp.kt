package org.screenconsume.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.AppUsage
import org.screenconsume.app.domain.model.DashboardStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private enum class Destination(val label: String) { DASHBOARD("Dashboard"), APPS("Apps"), TRENDS("Trends"), SETTINGS("Settings") }

@Composable
fun ScreenConsumeApp(viewModel: MainViewModel, openUsageSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.DASHBOARD) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.operationMessage) {
        state.operationMessage?.let { snackbar.showSnackbar(it); viewModel.clearOperationMessage() }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B5B), secondary = Color(0xFF4C635D))) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Text(item.label.take(1), fontWeight = FontWeight.Bold) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (!state.hasUsageAccess && destination != Destination.SETTINGS) UsageAccessEmptyState(openUsageSettings)
                else when (destination) {
                    Destination.DASHBOARD -> DashboardScreen(state, viewModel::selectPreset, viewModel::selectCustomRange)
                    Destination.APPS -> AppsScreen(state.stats.apps)
                    Destination.TRENDS -> TrendsScreen(state.stats)
                    Destination.SETTINGS -> SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun UsageAccessEmptyState(openSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your screen time stays yours", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("ScreenConsume needs Usage Access to create daily totals. It stores only local aggregates—not individual interactions or exact app-open timestamps.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = openSettings) { Text("Grant Usage Access") }
    }
}

@Composable
private fun DashboardScreen(state: MainUiState, select: (RangePreset) -> Unit, selectCustom: (DateRange) -> Unit) {
    var showCustomRange by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Dashboard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangePreset.entries.forEach { preset ->
                    FilterChip(selected = state.preset == preset, onClick = { if (preset == RangePreset.CUSTOM) showCustomRange = true else select(preset) }, label = { Text(preset.label) })
                }
            }
        }
        item { Text("${state.range.start} – ${state.range.endInclusive}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Total screen time", style = MaterialTheme.typography.labelLarge)
                    Text(duration(state.stats.totalSeconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(comparison(state.stats), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Daily average", duration(state.stats.averageDailySeconds), Modifier.weight(1f))
                MetricCard("App launches", state.stats.launchCount.toString(), Modifier.weight(1f))
            }
        }
        item { Text("Most used apps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.stats.apps.isEmpty()) item { Text("No usage recorded for this period yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.stats.apps.take(5), key = { it.packageName }) { AppRow(it) }
    }
    if (showCustomRange) CustomRangeDialog(state.range, onDismiss = { showCustomRange = false }) { selectCustom(it); showCustomRange = false }
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier) = ElevatedCard(modifier) { Column(Modifier.padding(16.dp)) { Text(label); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }

@Composable
private fun AppsScreen(apps: List<AppUsage>) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Apps", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        if (apps.isEmpty()) item { Text("No app usage in the selected dashboard period.") }
        items(apps, key = { it.packageName }) { AppRow(it) }
    }
}

@Composable
private fun AppRow(app: AppUsage) {
    ListItem(
        headlineContent = { Text(app.displayName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(listOfNotNull(app.category, "${app.launchCount} launches").joinToString(" • ")) },
        trailingContent = { Text(duration(app.usageSeconds), fontWeight = FontWeight.Bold) },
    )
}

@Composable
private fun TrendsScreen(stats: DashboardStats) {
    Column(Modifier.padding(20.dp)) {
        Text("Trends", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        if (stats.days.isEmpty()) Text("More history is needed before a trend can be shown.") else {
            Text("Daily usage", style = MaterialTheme.typography.titleLarge)
            val max = stats.days.maxOf { it.usageSeconds }.coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 16.dp)) {
                val step = if (stats.days.size <= 1) size.width else size.width / (stats.days.size - 1)
                val points = stats.days.mapIndexed { i, day -> Offset(i * step, size.height - (day.usageSeconds.toFloat() / max) * size.height) }
                points.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFF176B5B), a, b, strokeWidth = 6f) }
                points.forEach { drawCircle(Color(0xFF176B5B), 7f, it) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, viewModel: MainViewModel) {
    var showBackupPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingRestorePassword by remember { mutableStateOf<CharArray?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(viewModel::exportCsv) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(viewModel::exportJson) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { viewModel.exportEncryptedBackup(it, backupPassword.toCharArray()) }; backupPassword = "" }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.restore(it, pendingRestorePassword) }; pendingRestorePassword = null }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("Data & Integrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { ListItem(headlineContent = { Text("Connections") }, supportingContent = { Text("Google Sheets — Not connected\nNo online integration is included in this version.") }) }
        item {
            ListItem(
                headlineContent = { Text("Collection health") },
                supportingContent = { Text(state.lastSuccessfulAggregationMillis?.let(::formatTimestamp) ?: "No successful aggregation yet") },
            )
        }
        item { Text("Export selected range (${state.range.start} – ${state.range.endInclusive})", fontWeight = FontWeight.Medium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !state.operationInProgress, onClick = { csvLauncher.launch("screen-consume-${state.range.start}-${state.range.endInclusive}.csv") }) { Text("CSV") }
                OutlinedButton(enabled = !state.operationInProgress, onClick = { jsonLauncher.launch("screen-consume-${state.range.start}-${state.range.endInclusive}.json") }) { Text("JSON") }
            }
        }
        item { Text("Backup & restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Text("Encrypted backups include all history and use AES-256-GCM with a key derived from your password. The password cannot be recovered.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !state.operationInProgress, onClick = { showBackupPassword = true }) { Text("Create encrypted backup") }
                OutlinedButton(enabled = !state.operationInProgress, onClick = { pendingRestorePassword = null; restoreLauncher.launch(arrayOf("application/json")) }) { Text("Restore JSON") }
                OutlinedButton(enabled = !state.operationInProgress, onClick = { showRestorePassword = true }) { Text("Restore encrypted backup") }
            }
        }
        item { HorizontalDivider() }
        item { Text("Privacy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("Usage information is stored locally. No account, analytics SDK, advertising SDK, or Internet permission is included. Data leaves your device only when you explicitly export it or enable a future connection.") }
    }
    if (showBackupPassword) PasswordDialog("Backup password", backupPassword, { backupPassword = it }, { showBackupPassword = false; backupPassword = "" }) {
        showBackupPassword = false
        backupLauncher.launch("screen-consume-backup.scb")
    }
    if (showRestorePassword) PasswordDialog("Backup password", backupPassword, { backupPassword = it }, { showRestorePassword = false; backupPassword = "" }) {
        pendingRestorePassword = backupPassword.toCharArray(); backupPassword = ""; showRestorePassword = false
        restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }
}

@Composable
private fun PasswordDialog(title: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("Password") }) },
        confirmButton = { Button(enabled = value.length >= 8, onClick = onConfirm) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomRangeDialog(initial: DateRange, onDismiss: () -> Unit, onConfirm: (DateRange) -> Unit) {
    var start by remember { mutableStateOf(initial.start.toString()) }
    var end by remember { mutableStateOf(initial.endInclusive.toString()) }
    val parsed = runCatching { DateRange(LocalDate.parse(start), LocalDate.parse(end)) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom date range") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Use YYYY-MM-DD")
            OutlinedTextField(start, { start = it }, label = { Text("Start") }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text("End") }, singleLine = true)
        } },
        confirmButton = { Button(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun duration(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun comparison(stats: DashboardStats): String = when (val percent = stats.comparisonPercent) {
    null -> "No previous-period baseline"
    0 -> "Same as the previous period"
    else -> "${if (percent > 0) "+" else ""}$percent% vs previous period"
}

private fun formatTimestamp(millis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
