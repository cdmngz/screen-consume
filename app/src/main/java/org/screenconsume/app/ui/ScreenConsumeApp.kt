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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
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
                    Text(stringResource(R.string.tap_bar_for_details), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StackedUsageChart(usageBuckets(state.preset, state.range, state.dailyApps))
                    Text(stringResource(R.string.swipe_period_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { UsageSummaryCard(state.stats) }
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
private fun UsageSummaryCard(stats: DashboardStats) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MetricLabel(stringResource(R.string.total_screen_time))
                MetricValue(duration(stats.totalSeconds))
                Text(comparison(stats), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VerticalDivider()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MetricLabel(stringResource(R.string.daily_average))
                MetricValue(duration(stats.averageDailySeconds))
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
                        Modifier.fillMaxWidth(if (index == selectedIndex) .9f else .72f)
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

@Composable private fun MetricCard(label: String, value: String, modifier: Modifier) = ElevatedCard(modifier) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        MetricLabel(label)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                    UsageLineChart(detail.days, detail.range, Modifier.fillMaxWidth().height(205.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(stringResource(R.string.total), duration(total), Modifier.weight(1f))
                        MetricCard(stringResource(R.string.daily_average), duration(average), Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.each_point_recorded_usage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    UsageHistoryBarChart(detail.days, detail.range, Modifier.fillMaxWidth().height(165.dp))
                    Text(stringResource(R.string.frequency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    UsageFrequencyChart(detail.days, detail.range, Modifier.fillMaxWidth().height(190.dp))
                    Text(stringResource(R.string.frequency_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun UsageHistoryBarChart(days: List<DayUsage>, range: DateRange, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val buckets = remember(days, range, locale) { historyBuckets(days, range, locale) }
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) {
        val bottom = size.height - 23.dp.toPx()
        val top = 5.dp.toPx()
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        repeat(3) { index ->
            val y = top + chartHeight * index / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
        if (buckets.isEmpty()) return@Canvas
        val maximum = buckets.maxOf { it.usageSeconds }.coerceAtLeast(1)
        val slot = size.width / buckets.size
        val barWidth = (slot * .58f).coerceAtMost(22.dp.toPx())
        buckets.forEachIndexed { index, bucket ->
            val left = slot * index + (slot - barWidth) / 2f
            val height = chartHeight * bucket.usageSeconds.toFloat() / maximum
            drawRoundRect(barColor, Offset(left, bottom - height), androidx.compose.ui.geometry.Size(barWidth, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            if (buckets.size <= 14 || index % ((buckets.size + 6) / 7) == 0) {
                val measured = textMeasurer.measure(bucket.label, TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(measured, topLeft = Offset((slot * (index + .5f) - measured.size.width / 2f).coerceAtLeast(0f), bottom + 5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun UsageFrequencyChart(days: List<DayUsage>, range: DateRange, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val primary = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val description = stringResource(R.string.frequency_chart_description)
    val chartData = remember(days, range) { frequencyChartData(days, range) }
    Canvas(modifier.semantics { contentDescription = description }) {
        val labelWidth = 30.dp.toPx()
        val plotWidth = (size.width - labelWidth).coerceAtLeast(1f)
        val rowHeight = size.height / 7f
        val columnWidth = plotWidth / chartData.weekCount
        repeat(7) { row ->
            val y = rowHeight * (row + .5f)
            if (row > 0) drawLine(gridColor, Offset(0f, rowHeight * row), Offset(size.width, rowHeight * row), strokeWidth = 1f)
            val day = java.time.DayOfWeek.of(row + 1).getDisplayName(DateTextStyle.NARROW, locale)
            val measured = textMeasurer.measure(day, TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(measured, topLeft = Offset(size.width - measured.size.width, y - measured.size.height / 2f))
        }
        chartData.cells.forEach { cell ->
            val radius = 2.dp.toPx() + cell.intensity * minOf(6.dp.toPx(), columnWidth * .32f)
            drawCircle(primary.copy(alpha = .22f + .78f * cell.intensity), radius, Offset(columnWidth * (cell.column + .5f), rowHeight * (cell.row + .5f)))
        }
    }
}

@Composable
private fun UsageLineChart(days: List<DayUsage>, range: DateRange, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val labelFormatter = remember(range, locale) {
        DateTimeFormatter.ofPattern(if (range.dayCount <= 7) "EEEEE" else "d/M", locale)
    }
    Canvas(modifier.padding(top = 8.dp)) {
        val chartTop = 6.dp.toPx()
        val chartBottom = size.height - 24.dp.toPx()
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        repeat(4) { index ->
            val y = chartTop + chartHeight * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
        if (days.isNotEmpty()) {
            val maximum = days.maxOf { it.usageSeconds }.coerceAtLeast(1)
            val rangeSpan = (range.dayCount - 1).coerceAtLeast(1)
            val points = days.map { day ->
                val x = if (range.dayCount <= 1) size.width / 2f else {
                    val offset = day.date.toEpochDay() - range.start.toEpochDay()
                    size.width * (offset.toFloat() / rangeSpan)
                }
                val y = chartBottom - (day.usageSeconds.toFloat() / maximum) * chartHeight
                Offset(x.coerceIn(0f, size.width), y)
            }
            points.zipWithNext().forEach { (start, end) -> drawLine(lineColor, start, end, strokeWidth = 6f) }
            points.forEach { drawCircle(lineColor, 7f, it) }

            val labelIndices = if (days.size <= 7) days.indices.toList() else {
                (0..4).map { ((days.lastIndex * it) / 4f).roundToInt() }.distinct()
            }
            labelIndices.forEach { index ->
                val label = days[index].date.format(labelFormatter).uppercase(locale)
                val measured = textMeasurer.measure(label, TextStyle(fontSize = 11.sp, color = labelColor))
                val left = (points[index].x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width)
                drawText(measured, topLeft = Offset(left, chartBottom + 6.dp.toPx()))
            }
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
