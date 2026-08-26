package org.screenconsume.app.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.screenconsume.app.R
import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.AppUsage
import org.screenconsume.app.domain.model.DashboardStats
import org.screenconsume.app.domain.model.DayUsage
import org.screenconsume.app.domain.model.DailyAppUsage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private enum class ExportScope(val labelRes: Int) { ALL(R.string.all_data), CUSTOM(R.string.custom_range) }
private enum class UiIcon { SETTINGS, SEARCH, EXPAND, COLLAPSE, BACK, FORWARD }

@Composable
fun ScreenConsumeApp(viewModel: MainViewModel, openUsageSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appDetail by viewModel.appDetail.collectAsStateWithLifecycle()
    var showingSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.operationMessage) {
        state.operationMessage?.let { snackbar.showSnackbar(it); viewModel.clearOperationMessage() }
    }
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF72DDB8),
            secondary = Color(0xFFAFCCC3),
            tertiary = Color(0xFFFFC66D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF176B5B),
            secondary = Color(0xFF4C635D),
            tertiary = Color(0xFF8A5200),
        )
    }
    MaterialTheme(colorScheme = colors) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    showingSettings -> SettingsScreen(state, viewModel, onBack = { showingSettings = false })
                    appDetail != null -> AppDetailScreen(appDetail!!, state.lastSuccessfulAggregationMillis, viewModel::selectAppHistoryPreset, viewModel::closeApp)
                    !state.hasUsageAccess -> UsageAccessEmptyState(openUsageSettings, openAppSettings = { showingSettings = true })
                    else -> DashboardScreen(
                        state,
                        viewModel::selectPreset,
                        viewModel::movePeriod,
                        viewModel::openApp,
                        openSettings = { showingSettings = true },
                    )
                }
            }
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
    movePeriod: (Long) -> Unit,
    openApp: (AppUsage) -> Unit,
    openSettings: () -> Unit,
) {
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllApps by remember { mutableStateOf(false) }
    val filteredApps = remember(state.stats.apps, searchQuery, showAllApps) {
        val matches = if (searchQuery.isBlank()) state.stats.apps else state.stats.apps.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
        }
        if (searchQuery.isNotBlank() || showAllApps) matches else matches.take(5)
    }
    LazyColumn(
        Modifier.fillMaxSize().pointerInput(state.preset, state.range) {
            detectHorizontalDragGestures(
                onDragStart = { horizontalDrag = 0f },
                onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                onDragEnd = {
                    if (horizontalDrag > 80f) movePeriod(1)
                    else if (horizontalDrag < -80f && state.range.endInclusive.isBefore(LocalDate.now())) movePeriod(-1)
                },
            )
        },
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                UiIconButton(UiIcon.SETTINGS, stringResource(R.string.open_settings), openSettings)
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.usage_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(formatRange(state.range), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PeriodDropdown(state.preset, select)
                    }
                    StackedUsageChart(usageBuckets(state.preset, state.range, state.dailyApps))
                    Text(stringResource(R.string.swipe_period_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { UsageSummaryCard(state.headlineStats) }
        if (state.stats.apps.isNotEmpty()) item { UsageShareCard(state.stats.apps) }
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
}

@Composable
private fun UiIconButton(icon: UiIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.semantics { contentDescription = description }) {
        UiIconGraphic(icon, Modifier.size(24.dp))
    }
}

@Composable
private fun UiIconGraphic(icon: UiIcon, modifier: Modifier = Modifier) {
    if (icon == UiIcon.SETTINGS) {
        Icon(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = modifier)
        return
    }
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
            UiIcon.FORWARD -> {
                drawLine(color, point(5f, 12f), point(19f, 12f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(19f, 12f), point(13f, 6f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(19f, 12f), point(13f, 18f), strokeWidth, StrokeCap.Round)
            }
            UiIcon.SETTINGS -> Unit
        }
    }
}

@Composable
private fun PeriodDropdown(selected: RangePreset, select: (RangePreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier.minimumInteractiveComponentSize().clickable { expanded = true },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.height(32.dp),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = Color.Transparent,
        ) {
            Row(Modifier.padding(start = 11.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(selected.labelRes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(2.dp))
                UiIconGraphic(UiIcon.EXPAND, Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RangePreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(stringResource(preset.labelRes)) },
                    onClick = { expanded = false; select(preset) },
                    trailingIcon = { if (preset == selected) Text("✓") },
                )
            }
        }
    }
}

@Composable
private fun UsageSummaryCard(stats: HeadlineStats) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MetricLabel(stringResource(R.string.total_screen_time))
                MetricValue(duration(stats.todaySeconds))
                Text(stringResource(R.string.time_during_this_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(monthComparison(stats.todaySeconds, stats.previousMonthAverageSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            VerticalDivider()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MetricLabel(stringResource(R.string.daily_average))
                MetricValue(duration(stats.monthAverageSeconds))
                Text(stringResource(R.string.average_during_this_month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(monthComparison(stats.monthAverageSeconds, stats.previousMonthAverageSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MetricLabel(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun MetricValue(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

private data class ChartSegment(val name: String, val seconds: Long)
private data class UsageBucket(val label: String, val segments: List<ChartSegment>) {
    val total: Long = segments.sumOf { it.seconds }
}

@Composable
private fun usageBuckets(preset: RangePreset, range: DateRange, rows: List<DailyAppUsage>): List<UsageBucket> {
    val locale = LocalConfiguration.current.locales[0]
    val otherApps = stringResource(R.string.other_apps)
    fun ranked(label: String, values: List<Pair<String, Long>>): UsageBucket {
        val ranked = values.groupBy({ it.first }, { it.second }).map { (name, times) -> ChartSegment(name, times.sum()) }
            .filter { it.seconds > 0 }.sortedByDescending { it.seconds }
        val top = ranked.take(3)
        val other = ranked.drop(3).sumOf { it.seconds }
        return UsageBucket(label, top + listOfNotNull(ChartSegment(otherApps, other).takeIf { other > 0 }))
    }
    return when (preset) {
        RangePreset.TODAY -> listOf(
            stringResource(R.string.morning) to { row: DailyAppUsage -> row.morningUsageSeconds },
            stringResource(R.string.afternoon) to { row: DailyAppUsage -> row.afternoonUsageSeconds },
            stringResource(R.string.evening) to { row: DailyAppUsage -> row.eveningUsageSeconds },
            stringResource(R.string.night) to { row: DailyAppUsage -> row.nightUsageSeconds },
        ).map { (label, value) -> ranked(label, rows.map { it.displayName to value(it) }) }
        RangePreset.WEEK -> generateSequence(range.start) { it.plusDays(1) }.takeWhile { !it.isAfter(range.endInclusive) }.map { date ->
            ranked(date.format(DateTimeFormatter.ofPattern("EEEEE", locale)), rows.filter { it.date == date }.map { it.displayName to it.usageSeconds })
        }.toList()
        RangePreset.MONTH -> (0..ChronoUnit.WEEKS.between(range.start, range.endInclusive).toInt()).map { week ->
            val start = range.start.plusWeeks(week.toLong())
            val end = minOf(start.plusDays(6), range.endInclusive)
            ranked(stringResource(R.string.week_number, week + 1), rows.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.map { it.displayName to it.usageSeconds })
        }
        RangePreset.YEAR -> (1..range.endInclusive.monthValue).map { month ->
            ranked(java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.NARROW, locale), rows.filter { it.date.monthValue == month }.map { it.displayName to it.usageSeconds })
        }
    }
}

@Composable
private fun StackedUsageChart(buckets: List<UsageBucket>) {
    val colors = chartColors()
    var selectedIndex by remember(buckets) { mutableIntStateOf(buckets.indexOfLast { it.total > 0 }.coerceAtLeast(0)) }
    val maximum = buckets.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        buckets.forEachIndexed { index, bucket ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(160.dp).clickable { selectedIndex = index },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth(.72f)
                            .fillMaxHeight((bucket.total.toFloat() / maximum).coerceIn(.025f, 1f)),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        bucket.segments.forEachIndexed { segmentIndex, segment ->
                            Box(Modifier.fillMaxWidth().weight(segment.seconds.toFloat().coerceAtLeast(1f)).background(colors[segmentIndex]))
                        }
                    }
                }
                Text(bucket.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
    buckets.getOrNull(selectedIndex)?.let { bucket ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${bucket.label} · ${duration(bucket.total)}", fontWeight = FontWeight.Bold)
                if (bucket.segments.isEmpty()) Text(stringResource(R.string.no_usage_period), style = MaterialTheme.typography.bodySmall)
                bucket.segments.forEachIndexed { index, segment ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(colors[index], MaterialTheme.shapes.small))
                        Spacer(Modifier.width(7.dp))
                        Text(segment.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(duration(segment.seconds), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDonutChart(apps: List<AppUsage>, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val total = apps.sumOf { it.usageSeconds }.coerceAtLeast(1)
    val segments = apps.take(3).map { it.usageSeconds } + (total - apps.take(3).sumOf { it.usageSeconds })
    Canvas(modifier.semantics { contentDescription = "Usage share by app" }) {
        val diameter = size.minDimension * .82f
        val left = (size.width - diameter) / 2f
        val topOffset = (size.height - diameter) / 2f
        var start = -90f
        segments.forEachIndexed { index, seconds ->
            val sweep = seconds.toFloat() / total * 360f
            drawArc(colors[index], start, sweep, false, topLeft = Offset(left, topOffset), size = androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(diameter * .2f, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
private fun UsageShareCard(apps: List<AppUsage>) {
    val colors = chartColors()
    val total = apps.sumOf { it.usageSeconds }.coerceAtLeast(1)
    val other = total - apps.take(3).sumOf { it.usageSeconds }
    val displayed = apps.take(3).map { it.displayName to it.usageSeconds } +
        listOfNotNull((stringResource(R.string.other_apps) to other).takeIf { other > 0 })
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.usage_share), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UsageDonutChart(apps, Modifier.size(105.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    displayed.forEachIndexed { index, (name, seconds) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(colors[index], MaterialTheme.shapes.small))
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text("${seconds * 100 / total}% · ${duration(seconds)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun chartColors(): List<Color> = if (isSystemInDarkTheme()) {
    listOf(Color(0xFF72DDB8), Color(0xFF45A9D5), Color(0xFFFFC66D), Color(0xFF9DA8B5))
} else {
    listOf(Color(0xFF176B5B), Color(0xFF4783B5), Color(0xFFD07700), Color(0xFF89938F))
}

@Composable private fun MetricCard(label: String, value: String, description: String, modifier: Modifier) = ElevatedCard(modifier) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        MetricLabel(label)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(duration(app.usageSeconds), fontWeight = FontWeight.Bold)
                UiIconGraphic(UiIcon.FORWARD, Modifier.size(18.dp))
            }
        },
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
private fun AppDetailScreen(detail: AppDetailUiState, lastAggregationMillis: Long?, select: (AppHistoryPreset) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val installedApp = installedApp(detail.app.packageName)
    val name = installedApp?.label?.takeIf(String::isNotBlank) ?: detail.app.displayName
    val total = detail.days.sumOf { it.usageSeconds }
    val average = total / detail.range.dayCount
    val lastAggregationDate = lastAggregationMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val collectionMayBeIncomplete = lastAggregationDate == null || lastAggregationDate.isBefore(minOf(detail.range.endInclusive, LocalDate.now()))
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UiIconButton(UiIcon.BACK, stringResource(R.string.back_to_dashboard), onBack)
                installedApp?.icon?.let { Image(BitmapPainter(it), contentDescription = null, modifier = Modifier.size(40.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.usage_over_time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppHistoryPreset.entries.forEach { preset ->
                        FilterChip(selected = detail.preset == preset, onClick = { select(preset) }, label = { Text(stringResource(preset.labelRes)) })
                    }
                }
                Text(formatRange(detail.range), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(stringResource(R.string.total), duration(total), stringResource(R.string.total_selected_period_description), Modifier.weight(1f))
                MetricCard(stringResource(R.string.daily_average), duration(average), stringResource(R.string.average_selected_period_description), Modifier.weight(1f))
            }
        }
        if (detail.days.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.no_app_usage_period))
                if (collectionMayBeIncomplete) Text(stringResource(R.string.collection_may_be_incomplete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        else item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageLineChart(detail.days, detail.range, Modifier.fillMaxWidth().height(205.dp))
                Text(stringResource(R.string.each_point_recorded_usage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailSectionTitle(stringResource(R.string.calendar))
                UsageCalendar(detail.calendarDays)
                if (collectionMayBeIncomplete) Text(stringResource(R.string.collection_may_be_incomplete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailSectionTitle(stringResource(R.string.best_streaks))
                Text(stringResource(R.string.streaks_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BestStreaks(detail.days)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailSectionTitle(stringResource(R.string.usage_patterns))
                UsagePatternsCard(detail.days, detail.range)
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
)

@Composable
private fun UsageCalendar(days: List<DayUsage>) {
    val today = LocalDate.now()
    val earliest = LocalDate.of(2010, 1, 1)
    var pageEnd by remember { mutableStateOf(today) }
    var calendarDrag by remember { mutableFloatStateOf(0f) }
    val pageStart = maxOf(earliest, pageEnd.minusWeeks(13).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)))
    val range = DateRange(pageStart, pageEnd)
    val data = remember(days, range) { calendarChartData(days, range) }
    var selected by remember(data) { mutableStateOf(data.cells.lastOrNull { it.usageSeconds > 0 } ?: data.cells.lastOrNull()) }
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onEmpty = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val textMeasurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val description = stringResource(R.string.calendar_chart_description)
    val selectedDescription = selected?.let { "${it.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))}, ${duration(it.usageSeconds)}" }.orEmpty()
    fun moveSelection(offset: Long): Boolean {
        val current = selected ?: return false
        val target = current.date.plusDays(offset)
        return data.cells.firstOrNull { it.date == target }?.let { selected = it; true } ?: false
    }
    val previousDay = stringResource(R.string.previous_day)
    val nextDay = stringResource(R.string.next_day)
    val chartModifier = Modifier.fillMaxWidth().height(230.dp).semantics {
        contentDescription = description
        stateDescription = selectedDescription
        customActions = listOf(
            CustomAccessibilityAction(previousDay) { moveSelection(-1) },
            CustomAccessibilityAction(nextDay) { moveSelection(1) },
        )
    }.pointerInput(data) {
        fun selectAt(position: Offset) {
            val plotLeft = 25.dp.toPx()
            val plotTop = 22.dp.toPx()
            val column = ((position.x - plotLeft) / ((size.width - plotLeft) / data.weekCount)).toInt().coerceIn(0, data.weekCount - 1)
            val row = ((position.y - plotTop) / ((size.height - plotTop) / 7f)).toInt().coerceIn(0, 6)
            data.cells.firstOrNull { it.column == column && it.row == row }?.let { selected = it }
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            selectAt(down.position)
            do {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let { selectAt(it.position) }
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().pointerInput(pageEnd) {
                    detectHorizontalDragGestures(
                        onDragStart = { calendarDrag = 0f },
                        onHorizontalDrag = { _, amount -> calendarDrag += amount },
                        onDragEnd = {
                            if (calendarDrag < -60f && pageStart > earliest) pageEnd = maxOf(earliest, pageEnd.minusWeeks(14))
                            else if (calendarDrag > 60f && pageEnd < today) pageEnd = minOf(today, pageEnd.plusWeeks(14))
                        },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiIconButton(
                    UiIcon.BACK,
                    stringResource(R.string.older_calendar_period),
                    { pageEnd = maxOf(earliest, pageEnd.minusWeeks(14)) },
                    enabled = pageStart > earliest,
                )
                Text(formatRange(range), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                UiIconButton(
                    UiIcon.FORWARD,
                    stringResource(R.string.newer_calendar_period),
                    { pageEnd = minOf(today, pageEnd.plusWeeks(14)) },
                    enabled = pageEnd < today,
                )
            }
            Canvas(chartModifier) {
                val gap = 2.dp.toPx()
                val plotLeft = 25.dp.toPx()
                val plotTop = 22.dp.toPx()
                val cellWidth = (size.width - plotLeft) / data.weekCount
                val cellHeight = (size.height - plotTop) / 7f
                repeat(7) { row ->
                    val day = java.time.DayOfWeek.of(row + 1).getDisplayName(DateTextStyle.NARROW, locale)
                    val measured = textMeasurer.measure(day, TextStyle(fontSize = 10.sp, color = onEmpty))
                    drawText(measured, topLeft = Offset(0f, plotTop + row * cellHeight + (cellHeight - measured.size.height) / 2f))
                }
                repeat(data.weekCount) { column ->
                    val date = data.cells.firstOrNull { it.column == column }?.date ?: data.firstWeekStart.plusWeeks(column.toLong())
                    val previousMonth = data.cells.firstOrNull { it.column == column - 1 }?.date?.month
                    if (column == 0 || date.month != previousMonth) {
                        val month = date.month.getDisplayName(DateTextStyle.SHORT, locale)
                        val measured = textMeasurer.measure(month, TextStyle(fontSize = 10.sp, color = onEmpty))
                        drawText(measured, topLeft = Offset(plotLeft + column * cellWidth + 2.dp.toPx(), 0f))
                    }
                }
                data.cells.forEach { cell ->
                    val topLeft = Offset(plotLeft + cell.column * cellWidth + gap, plotTop + cell.row * cellHeight + gap)
                    val cellSize = androidx.compose.ui.geometry.Size(cellWidth - gap * 2, cellHeight - gap * 2)
                    val color = if (cell.usageSeconds == 0L) empty else primary.copy(alpha = .28f + .72f * cell.intensity)
                    drawRoundRect(color, topLeft, cellSize, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    if (cell == selected) drawRoundRect(outline, topLeft, cellSize, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
                    val labelColor = if (cell.usageSeconds > 0 && cell.intensity > .55f) onPrimary else onEmpty
                    val measured = textMeasurer.measure(cell.date.dayOfMonth.toString(), TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Medium))
                    drawText(measured, topLeft = Offset(topLeft.x + (cellSize.width - measured.size.width) / 2f, topLeft.y + (cellSize.height - measured.size.height) / 2f))
                }
            }
            selected?.let {
                Text("${it.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))} · ${duration(it.usageSeconds)}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BestStreaks(days: List<DayUsage>) {
    val streaks = remember(days) { bestUsageStreaks(days) }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    if (streaks.isEmpty()) {
        Text(stringResource(R.string.no_streaks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maximum = streaks.maxOf { it.dayCount }.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        streaks.take(5).forEachIndexed { index, streak ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.streak_days, streak.dayCount), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${streak.start.format(formatter)} – ${streak.endInclusive.format(formatter)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(
                        progress = { streak.dayCount / maximum },
                        modifier = Modifier.fillMaxWidth().height(if (index == 0) 9.dp else 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UsagePatternsCard(days: List<DayUsage>, range: DateRange) {
    val locale = LocalConfiguration.current.locales[0]
    val patterns = remember(days) { usagePatterns(days) }
    val total = patterns.weekdaySeconds + patterns.weekendSeconds
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PatternRow(
                stringResource(R.string.most_used_day),
                patterns.mostUsedDay?.getDisplayName(DateTextStyle.FULL, locale) ?: stringResource(R.string.no_usage_period),
            )
            HorizontalDivider()
            PatternRow(stringResource(R.string.active_days), stringResource(R.string.active_days_value, patterns.activeDays, range.dayCount))
            HorizontalDivider()
            PatternRow(
                stringResource(R.string.weekday_weekend),
                if (total == 0L) stringResource(R.string.no_usage_period) else stringResource(
                    R.string.weekday_weekend_value,
                    patterns.weekdaySeconds * 100 / total,
                    patterns.weekendSeconds * 100 / total,
                ),
                stringResource(R.string.weekday_weekend_description),
            )
        }
    }
}

@Composable
private fun PatternRow(label: String, value: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
        description?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun UsageLineChart(days: List<DayUsage>, range: DateRange, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val usageByDate = remember(days) { days.associate { it.date to it.usageSeconds } }
    val chartDays = remember(days, range) {
        generateSequence(range.start) { it.plusDays(1) }.takeWhile { !it.isAfter(range.endInclusive) }
            .map { DayUsage(it, usageByDate[it] ?: 0) }.toList()
    }
    var selected by remember(chartDays) { mutableStateOf(chartDays.lastOrNull { it.usageSeconds > 0 } ?: chartDays.lastOrNull()) }
    val labelFormatter = remember(range, locale) {
        DateTimeFormatter.ofPattern(if (range.dayCount <= 7) "EEEEE" else "d/M", locale)
    }
    fun moveSelection(offset: Long): Boolean {
        val current = selected ?: return false
        val target = current.date.plusDays(offset)
        return chartDays.firstOrNull { it.date == target }?.let { selected = it; true } ?: false
    }
    val previousDay = stringResource(R.string.previous_day)
    val nextDay = stringResource(R.string.next_day)
    val chartDescription = stringResource(R.string.usage_trend_chart_description)
    val selectedDescription = selected?.let { "${it.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))}, ${duration(it.usageSeconds)}" }.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().weight(1f).semantics {
                contentDescription = chartDescription
                stateDescription = selectedDescription
                customActions = listOf(
                    CustomAccessibilityAction(previousDay) { moveSelection(-1) },
                    CustomAccessibilityAction(nextDay) { moveSelection(1) },
                )
            }.pointerInput(chartDays) {
                fun selectAt(x: Float) {
                    val index = if (chartDays.size <= 1) 0 else ((x / size.width) * chartDays.lastIndex).roundToInt().coerceIn(0, chartDays.lastIndex)
                    selected = chartDays.getOrNull(index)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    selectAt(down.position.x)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { selectAt(it.position.x) }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        ) {
            val chartTop = 6.dp.toPx()
            val chartBottom = size.height - 24.dp.toPx()
            val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
            repeat(4) { index ->
                val y = chartTop + chartHeight * index / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }
            if (chartDays.isNotEmpty()) {
                val maximum = chartDays.maxOf { it.usageSeconds }.coerceAtLeast(1)
                val denominator = chartDays.lastIndex.coerceAtLeast(1)
                val points = chartDays.mapIndexed { index, day ->
                    val x = if (chartDays.size <= 1) size.width / 2f else size.width * index / denominator
                    val y = chartBottom - (day.usageSeconds.toFloat() / maximum) * chartHeight
                    Offset(x.coerceIn(0f, size.width), y)
                }
                points.zipWithNext().forEach { (start, end) -> drawLine(lineColor, start, end, strokeWidth = 4f) }
                if (chartDays.size <= 31) points.forEach { drawCircle(lineColor, 5f, it) }
                selected?.let { day ->
                    val index = chartDays.indexOfFirst { it.date == day.date }.coerceAtLeast(0)
                    drawLine(labelColor.copy(alpha = .5f), Offset(points[index].x, chartTop), Offset(points[index].x, chartBottom), strokeWidth = 2f)
                    drawCircle(lineColor, 8f, points[index])
                }
                val labelIndices = if (chartDays.size <= 7) chartDays.indices.toList() else {
                    (0..4).map { ((chartDays.lastIndex * it) / 4f).roundToInt() }.distinct()
                }
                labelIndices.forEach { index ->
                    val label = chartDays[index].date.format(labelFormatter).uppercase(locale)
                    val measured = textMeasurer.measure(label, TextStyle(fontSize = 11.sp, color = labelColor))
                    val left = (points[index].x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width)
                    drawText(measured, topLeft = Offset(left, chartBottom + 6.dp.toPx()))
                }
            }
        }
        selected?.let {
            Text("${it.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))} · ${duration(it.usageSeconds)}", fontWeight = FontWeight.SemiBold)
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
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UiIconButton(UiIcon.BACK, stringResource(R.string.back_to_dashboard), onBack)
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
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

@Composable
private fun monthComparison(value: Long, previousMonthAverage: Long): String = when {
    previousMonthAverage == 0L && value == 0L -> stringResource(R.string.same_as_last_month_average, duration(previousMonthAverage))
    previousMonthAverage == 0L -> stringResource(R.string.no_last_month_average)
    else -> {
        val percent = (((value - previousMonthAverage) * 100.0) / previousMonthAverage).toInt()
        stringResource(R.string.percent_vs_last_month_average, if (percent > 0) "+" else "", percent, duration(previousMonthAverage))
    }
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
