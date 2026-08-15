package org.screenconsume.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.screenconsume.app.domain.model.AppUsage
import org.screenconsume.app.domain.model.DashboardStats
import java.util.concurrent.TimeUnit

private enum class Destination(val label: String) { DASHBOARD("Dashboard"), APPS("Apps"), TRENDS("Trends"), SETTINGS("Settings") }

@Composable
fun ScreenConsumeApp(viewModel: MainViewModel, openUsageSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.DASHBOARD) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B5B), secondary = Color(0xFF4C635D))) {
        Scaffold(
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
                if (!state.hasUsageAccess) UsageAccessEmptyState(openUsageSettings)
                else when (destination) {
                    Destination.DASHBOARD -> DashboardScreen(state, viewModel::selectPeriod)
                    Destination.APPS -> AppsScreen(state.stats.apps)
                    Destination.TRENDS -> TrendsScreen(state.stats)
                    Destination.SETTINGS -> SettingsScreen()
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
private fun DashboardScreen(state: MainUiState, select: (Period) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Dashboard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { index, period ->
                    SegmentedButton(selected = state.period == period, onClick = { select(period) }, shape = SegmentedButtonDefaults.itemShape(index, Period.entries.size)) { Text(period.label) }
                }
            }
        }
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
private fun SettingsScreen() {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("Data & Integrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { ListItem(headlineContent = { Text("Connections") }, supportingContent = { Text("Google Sheets — Not connected\nNo online integration is included in this version.") }) }
        item { ListItem(headlineContent = { Text("Export") }, supportingContent = { Text("CSV and JSON date-range export is planned for a future milestone.") }) }
        item { HorizontalDivider() }
        item { Text("Privacy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("Usage information is stored locally. No account, analytics SDK, advertising SDK, or Internet permission is included. Data leaves your device only when you explicitly export it or enable a future connection.") }
    }
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

