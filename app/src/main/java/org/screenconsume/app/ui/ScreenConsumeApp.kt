package org.screenconsume.app.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.screenconsume.app.R
import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.AppUsage
import org.screenconsume.app.domain.model.DashboardStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class ExportScope(val labelRes: Int) { ALL(R.string.all_data), CUSTOM(R.string.custom_range) }
private enum class UiIcon { SETTINGS, SEARCH, EXPAND, COLLAPSE, BACK }

@Composable
fun ScreenConsumeApp(viewModel: MainViewModel, openUsageSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appDetail by viewModel.appDetail.collectAsStateWithLifecycle()
    var showingSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.operationMessage) {
        state.operationMessage?.let { snackbar.showSnackbar(it); viewModel.clearOperationMessage() }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B5B), secondary = Color(0xFF4C635D))) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    showingSettings -> SettingsScreen(state, viewModel, onBack = { showingSettings = false })
                    !state.hasUsageAccess -> UsageAccessEmptyState(openUsageSettings, openAppSettings = { showingSettings = true })
                    else -> DashboardScreen(
                        state,
                        viewModel::selectPreset,
                        viewModel::selectCustomRange,
                        viewModel::openApp,
                        openSettings = { showingSettings = true },
                    )
                }
            }
        }
        appDetail?.let { detail ->
            AppDetailDialog(detail, viewModel::selectAppHistoryPreset, viewModel::closeApp)
        }
    }
}

@Composable
private fun UsageAccessEmptyState(openSettings: () -> Unit, openAppSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        UiIconButton(UiIcon.SETTINGS, stringResource(R.string.open_settings), openAppSettings, Modifier.align(Alignment.TopEnd).padding(12.dp))
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.screen_time_stays_yours), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.usage_access_explanation, stringResource(R.string.app_name)))
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoBadge(stringResource(R.string.secure))
            InfoBadge(stringResource(R.string.local_data))
            InfoBadge(stringResource(R.string.no_internet_connection))
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.on_device_assurance), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = openSettings) { Text(stringResource(R.string.grant_usage_access)) }
        }
    }
}

@Composable
private fun InfoBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DashboardScreen(
    state: MainUiState,
    select: (RangePreset) -> Unit,
    selectCustom: (DateRange) -> Unit,
    openApp: (AppUsage) -> Unit,
    openSettings: () -> Unit,
) {
    var showCustomRange by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(false) }
    val filteredApps = remember(state.stats.apps, searchQuery, showAllApps) {
        val matches = if (searchQuery.isBlank()) state.stats.apps else state.stats.apps.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
        if (searchQuery.isNotBlank() || showAllApps) matches else matches.take(5)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                UiIconButton(UiIcon.SETTINGS, stringResource(R.string.open_settings), openSettings)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangePreset.entries.forEach { preset ->
                    FilterChip(selected = state.preset == preset, onClick = { if (preset == RangePreset.CUSTOM) showCustomRange = true else select(preset) }, label = { Text(stringResource(preset.labelRes)) })
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.daily_screen_time), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.stats.days.isEmpty()) {
                        UsageLineChart(emptyList(), Modifier.fillMaxWidth().height(130.dp))
                        Text(stringResource(R.string.daily_totals_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        UsageLineChart(state.stats.days.map { it.usageSeconds }, Modifier.fillMaxWidth().height(150.dp))
                        Text(stringResource(R.string.each_point_selected_period), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.total_screen_time), style = MaterialTheme.typography.labelLarge)
                    Text(duration(state.stats.totalSeconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(comparison(state.stats), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.daily_average), duration(state.stats.averageDailySeconds), Modifier.weight(1f))
                MetricCard(stringResource(R.string.app_launches), state.stats.launchCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.most_used_apps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            searchQuery.isNotBlank() -> stringResource(R.string.search_results, filteredApps.size)
                            showAllApps -> stringResource(R.string.showing_all_apps, state.stats.apps.size)
                            else -> stringResource(R.string.showing_top_apps, minOf(5, state.stats.apps.size))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                UiIconButton(UiIcon.SEARCH, stringResource(if (searchVisible) R.string.hide_app_search else R.string.search_apps), {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                })
                UiIconButton(if (showAllApps) UiIcon.COLLAPSE else UiIcon.EXPAND, stringResource(if (showAllApps) R.string.show_top_apps else R.string.show_all_apps), {
                    showAllApps = !showAllApps
                }, enabled = state.stats.apps.size > 5)
            }
        }
        if (searchVisible) item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_by_app_name)) },
                singleLine = true,
            )
        }
        if (state.stats.apps.isEmpty()) item { Text(stringResource(R.string.no_usage_period), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else if (filteredApps.isEmpty()) item { Text(stringResource(R.string.no_search_matches), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(filteredApps, key = { it.packageName }) { AppRow(it, openApp) }
    }
    if (showCustomRange) CustomRangeDialog(state.range, onDismiss = { showCustomRange = false }) { selectCustom(it); showCustomRange = false }
}

@Composable
private fun UiIconButton(icon: UiIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.semantics { contentDescription = description }) {
        UiIconGraphic(icon, Modifier.size(24.dp))
    }
}

@Composable
private fun UiIconGraphic(icon: UiIcon, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        val strokeWidth = 2f * scale
        val lineStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        when (icon) {
            UiIcon.SEARCH -> {
                drawCircle(color, radius = 6f * scale, center = point(10f, 10f), style = lineStyle)
                drawLine(color, point(14.5f, 14.5f), point(20f, 20f), strokeWidth, StrokeCap.Round)
            }
            UiIcon.EXPAND -> {
                drawLine(color, point(6f, 9f), point(12f, 15f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(12f, 15f), point(18f, 9f), strokeWidth, StrokeCap.Round)
            }
            UiIcon.COLLAPSE -> {
                drawLine(color, point(6f, 15f), point(12f, 9f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(12f, 9f), point(18f, 15f), strokeWidth, StrokeCap.Round)
            }
            UiIcon.BACK -> {
                drawLine(color, point(19f, 12f), point(5f, 12f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(5f, 12f), point(11f, 6f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(5f, 12f), point(11f, 18f), strokeWidth, StrokeCap.Round)
            }
            UiIcon.SETTINGS -> {
                drawCircle(color, radius = 6.5f * scale, center = point(12f, 12f), style = lineStyle)
                drawCircle(color, radius = 2.5f * scale, center = point(12f, 12f), style = lineStyle)
                repeat(8) { index ->
                    val angle = index * PI / 4
                    val start = point((12 + cos(angle) * 8).toFloat(), (12 + sin(angle) * 8).toFloat())
                    val end = point((12 + cos(angle) * 10).toFloat(), (12 + sin(angle) * 10).toFloat())
                    drawLine(color, start, end, strokeWidth, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier) = ElevatedCard(modifier) { Column(Modifier.padding(16.dp)) { Text(label); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }

@Composable
private fun AppRow(app: AppUsage, openApp: (AppUsage) -> Unit) {
    val installedApp = installedApp(app.packageName)
    val displayName = installedApp?.label?.takeIf(String::isNotBlank) ?: app.displayName
    val category = installedApp?.category ?: app.category
    ListItem(
        modifier = Modifier.clickable { openApp(app) },
        leadingContent = installedApp?.icon?.let { icon ->
            { Image(BitmapPainter(icon), contentDescription = null, modifier = Modifier.size(40.dp)) }
        },
        headlineContent = { Text(displayName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(listOfNotNull(category, stringResource(R.string.launch_count, app.launchCount)).joinToString(" • ")) },
        trailingContent = { Text(duration(app.usageSeconds), fontWeight = FontWeight.Bold) },
    )
}

@Composable
private fun installedApp(packageName: String): InstalledApp? {
    val context = LocalContext.current
    return remember(packageName) {
        try {
            val packageManager = context.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            InstalledApp(
                label = packageManager.getApplicationLabel(info).toString(),
                category = appCategory(context, info),
                icon = packageManager.getApplicationIcon(info).toBitmap().asImageBitmap(),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

private data class InstalledApp(val label: String, val category: String?, val icon: ImageBitmap)

private fun appCategory(context: android.content.Context, info: ApplicationInfo): String? =
    ApplicationInfo.getCategoryTitle(context, info.category)?.toString()

@Composable
private fun AppDetailDialog(detail: AppDetailUiState, select: (AppHistoryPreset) -> Unit, onDismiss: () -> Unit) {
    val installedApp = installedApp(detail.app.packageName)
    val name = installedApp?.label?.takeIf(String::isNotBlank) ?: detail.app.displayName
    val total = detail.days.sumOf { it.usageSeconds }
    val average = if (detail.days.isEmpty()) 0 else total / detail.range.dayCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                installedApp?.icon?.let { Image(BitmapPainter(it), contentDescription = null, modifier = Modifier.size(40.dp)) }
                Column { Text(name); Text(stringResource(R.string.usage_over_time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppHistoryPreset.entries.forEach { preset ->
                        FilterChip(selected = detail.preset == preset, onClick = { select(preset) }, label = { Text(stringResource(preset.labelRes)) })
                    }
                }
                Text(formatRange(detail.range), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (detail.days.isEmpty()) {
                    Text(stringResource(R.string.no_app_usage_period))
                } else {
                    UsageLineChart(detail.days.map { it.usageSeconds }, Modifier.fillMaxWidth().height(190.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(stringResource(R.string.total), duration(total), Modifier.weight(1f))
                        MetricCard(stringResource(R.string.daily_average), duration(average), Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.each_point_recorded_usage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun UsageLineChart(values: List<Long>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.padding(vertical = 8.dp)) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
        if (values.isNotEmpty()) {
            val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
            val step = if (values.size <= 1) 0f else size.width / (values.size - 1)
            val points = values.mapIndexed { index, seconds ->
                val x = if (values.size == 1) size.width / 2f else index * step
                Offset(x, size.height - (seconds.toFloat() / maximum) * size.height)
            }
            points.zipWithNext().forEach { (start, end) -> drawLine(lineColor, start, end, strokeWidth = 6f) }
            points.forEach { drawCircle(lineColor, 7f, it) }
        }
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    var showBackupPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingRestorePassword by remember { mutableStateOf<CharArray?>(null) }
    var exportScope by remember { mutableStateOf(ExportScope.ALL) }
    var customExportRange by remember { mutableStateOf(DateRange.endingToday(30)) }
    var showExportRange by remember { mutableStateOf(false) }
    val selectedExportRange = customExportRange.takeIf { exportScope == ExportScope.CUSTOM }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { viewModel.exportCsv(it, selectedExportRange) } }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.exportJson(it, selectedExportRange) } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { viewModel.exportEncryptedBackup(it, backupPassword.toCharArray()) }; backupPassword = "" }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.restore(it, pendingRestorePassword) }; pendingRestorePassword = null }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { UiIconButton(UiIcon.BACK, stringResource(R.string.back_to_dashboard), onBack) }
        item { SectionTitle(stringResource(R.string.data_collection)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.collection_health)) },
                supportingContent = { Text(state.lastSuccessfulAggregationMillis?.let(::formatTimestamp) ?: stringResource(R.string.no_successful_aggregation)) },
            )
        }
        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.export)) }
        item { Text(stringResource(R.string.export_description), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportScope.entries.forEach { scope ->
                    FilterChip(selected = exportScope == scope, onClick = { exportScope = scope }, label = { Text(stringResource(scope.labelRes)) })
                }
            }
        }
        item {
            if (exportScope == ExportScope.ALL) {
                Text(stringResource(R.string.all_export_description), style = MaterialTheme.typography.bodyMedium)
            } else {
                ListItem(
                    headlineContent = { Text("${customExportRange.start} – ${customExportRange.endInclusive}") },
                    supportingContent = { Text(stringResource(R.string.custom_export_description)) },
                    trailingContent = { TextButton(onClick = { showExportRange = true }) { Text(stringResource(R.string.change)) } },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !state.operationInProgress, onClick = {
                    csvLauncher.launch(exportFileName("csv", selectedExportRange))
                }) { Text(stringResource(R.string.export_csv)) }
                OutlinedButton(enabled = !state.operationInProgress, onClick = {
                    jsonLauncher.launch(exportFileName("json", selectedExportRange))
                }) { Text(stringResource(R.string.export_json)) }
            }
        }
        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.backup_restore)) }
        item { Text(stringResource(R.string.backup_description), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !state.operationInProgress, onClick = { showBackupPassword = true }) { Text(stringResource(R.string.create_encrypted_backup)) }
                OutlinedButton(enabled = !state.operationInProgress, onClick = { pendingRestorePassword = null; restoreLauncher.launch(arrayOf("application/json")) }) { Text(stringResource(R.string.restore_json)) }
                OutlinedButton(enabled = !state.operationInProgress, onClick = { showRestorePassword = true }) { Text(stringResource(R.string.restore_encrypted_backup)) }
            }
        }
        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.privacy)) }
        item { Text(stringResource(R.string.privacy_description)) }
    }
    if (showBackupPassword) PasswordDialog(stringResource(R.string.backup_password), backupPassword, { backupPassword = it }, { showBackupPassword = false; backupPassword = "" }) {
        showBackupPassword = false
        backupLauncher.launch("screen-consume-backup.scb")
    }
    if (showRestorePassword) PasswordDialog(stringResource(R.string.backup_password), backupPassword, { backupPassword = it }, { showRestorePassword = false; backupPassword = "" }) {
        pendingRestorePassword = backupPassword.toCharArray(); backupPassword = ""; showRestorePassword = false
        restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }
    if (showExportRange) CustomRangeDialog(customExportRange, onDismiss = { showExportRange = false }) {
        customExportRange = it
        showExportRange = false
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun PasswordDialog(title: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.password)) }) },
        confirmButton = { Button(enabled = value.length >= 8, onClick = onConfirm) { Text(stringResource(R.string.continue_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CustomRangeDialog(initial: DateRange, onDismiss: () -> Unit, onConfirm: (DateRange) -> Unit) {
    var start by remember { mutableStateOf(initial.start.toString()) }
    var end by remember { mutableStateOf(initial.endInclusive.toString()) }
    val parsed = runCatching { DateRange(LocalDate.parse(start), LocalDate.parse(end)) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_date_range)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.date_format_hint))
            OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.start)) }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.end)) }, singleLine = true)
        } },
        confirmButton = { Button(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun duration(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun comparison(stats: DashboardStats): String = when (val percent = stats.comparisonPercent) {
    null -> stringResource(R.string.no_previous_baseline)
    0 -> stringResource(R.string.same_previous_period)
    else -> stringResource(R.string.percent_previous_period, if (percent > 0) "+" else "", percent)
}

private fun formatTimestamp(millis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun formatRange(range: DateRange): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return if (range.start == range.endInclusive) range.start.format(formatter)
    else "${range.start.format(formatter)} – ${range.endInclusive.format(formatter)}"
}

private fun exportFileName(extension: String, range: DateRange?): String =
    if (range == null) "screen-consume-all.$extension"
    else "screen-consume-${range.start}-${range.endInclusive}.$extension"
