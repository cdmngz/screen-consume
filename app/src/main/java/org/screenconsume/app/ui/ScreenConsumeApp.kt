package org.screenconsume.app.ui

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import org.screenconsume.app.domain.model.DayUsage
import org.screenconsume.app.domain.model.DailyAppUsage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.concurrent.TimeUnit

private enum class ExportScope(val labelRes: Int) { ALL(R.string.all_data), CUSTOM(R.string.custom_range) }
private enum class UiIcon { SETTINGS, EXPAND, COLLAPSE, BACK, FORWARD, PREVIOUS, NEXT }

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
                    appDetail != null -> AppDetailScreen(
                        appDetail!!,
                        state.lastSuccessfulAggregationMillis,
                        viewModel::selectAppHistoryPreset,
                        viewModel::moveAppHistoryPeriod,
                        viewModel::closeApp,
                    )
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
    val buckets = usageBuckets(state.preset, state.range, state.dailyApps)
    var selectedIndex by remember(state.preset, state.range, buckets) { mutableIntStateOf(-1) }
    var chartBounds by remember { mutableStateOf(Rect.Zero) }
    var dashboardOrigin by remember { mutableStateOf(Offset.Zero) }
    val usedApps = remember(state.stats.apps) { state.stats.apps.filter { it.usageSeconds >= 60 } }
    LazyColumn(
        Modifier.fillMaxSize()
            .onGloballyPositioned { dashboardOrigin = it.positionInRoot() }
            .pointerInput(state.preset, state.range, buckets) {
                // Observe without consuming: buttons and scroll gestures still handle their input.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startedOutside = !chartBounds.contains(down.position + dashboardOrigin)
                    var isTap = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size != 1 || event.changes.any {
                                (it.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                                    it.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
                            }) isTap = false
                        val released = event.changes.all { !it.pressed }
                        if (released && isTap && startedOutside) selectedIndex = -1
                    } while (!released)
                }
            }
            .pointerInput(state.preset, state.range) {
            detectHorizontalDragGestures(
                onDragStart = { horizontalDrag = 0f },
                onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                onDragEnd = {
                    horizontalPageOffset(horizontalDrag, 80f)?.let { offset ->
                        if (offset > 0 || state.range.endInclusive.isBefore(LocalDate.now())) movePeriod(offset)
                    }
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
        item { UsageSummaryCard(state.headlineStats, state.loading) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PeriodButtons(state.preset, select)
                    AnimatedContent(
                        targetState = state,
                        contentKey = { Triple(it.preset, it.range, it.loading) },
                        transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                        label = "Usage period",
                    ) { chartState ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(formatRange(chartState.range), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (chartState.loading) {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                }
                            } else {
                                val chartBuckets = usageBuckets(chartState.preset, chartState.range, chartState.dailyApps)
                                val rankedApps = chartState.stats.apps.filter { it.usageSeconds > 0 }.sortedByDescending { it.usageSeconds }
                                val otherSeconds = rankedApps.drop(3).sumOf { it.usageSeconds }
                                val fullBucket = UsageBucket(
                                    stringResource(if (chartState.preset == RangePreset.TODAY) R.string.full_day else R.string.full_period),
                                    rankedApps.take(3).map { ChartSegment(it.displayName, it.usageSeconds) } +
                                        listOfNotNull(ChartSegment(stringResource(R.string.other_apps), otherSeconds).takeIf { otherSeconds > 0 }),
                                )
                                StackedUsageChart(
                                    chartBuckets,
                                    onSelect = { selectedIndex = if (selectedIndex == it) -1 else it },
                                    modifier = Modifier.onGloballyPositioned { chartBounds = it.boundsInRoot() },
                                )
                                UsageShareContent(chartBuckets.getOrNull(selectedIndex) ?: fullBucket)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.most_used_apps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    pluralStringResource(R.plurals.showing_all_apps, usedApps.size, usedApps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (usedApps.isEmpty()) item { Text(stringResource(R.string.no_usage_period), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(usedApps, key = { it.packageName }) { AppRow(it, openApp) }
    }

}

@Composable
private fun UiIconButton(icon: UiIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.semantics { contentDescription = description }) {
        UiIconGraphic(icon, Modifier.size(iconSize))
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
        when (icon) {
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
            UiIcon.PREVIOUS -> {
                drawLine(color, point(15f, 5f), point(8f, 12f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(8f, 12f), point(15f, 19f), strokeWidth, StrokeCap.Round)
            }
            UiIcon.NEXT -> {
                drawLine(color, point(9f, 5f), point(16f, 12f), strokeWidth, StrokeCap.Round)
                drawLine(color, point(16f, 12f), point(9f, 19f), strokeWidth, StrokeCap.Round)
            }
            UiIcon.SETTINGS -> Unit
        }
    }
}

@Composable
private fun PeriodButtons(selected: RangePreset, select: (RangePreset) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RangePreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { select(preset) },
                label = { Text(stringResource(preset.labelRes)) },
            )
        }
    }
}

@Composable
private fun UsageSummaryCard(stats: HeadlineStats, loading: Boolean) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MetricLabel(stringResource(R.string.total_day_time))
                MetricValue(if (loading) "—" else duration(stats.todaySeconds))
            }
            VerticalDivider()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MetricLabel(stringResource(R.string.average_in_month, stats.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalConfiguration.current.locales[0]))))
                MetricValue(if (loading) "—" else duration(stats.monthAverageSeconds))
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
        RangePreset.MONTH -> monthWeekRanges(range.start).mapIndexed { week, weekRange ->
            ranked(stringResource(R.string.week_number, week + 1), rows.filter { !it.date.isBefore(weekRange.start) && !it.date.isAfter(weekRange.endInclusive) }.map { it.displayName to it.usageSeconds })
        }
        RangePreset.YEAR -> (1..range.endInclusive.monthValue).map { month ->
            ranked(java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.NARROW, locale), rows.filter { it.date.monthValue == month }.map { it.displayName to it.usageSeconds })
        }
    }
}

@Composable
private fun StackedUsageChart(
    buckets: List<UsageBucket>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val peak = buckets.maxOfOrNull { it.total } ?: 0L
    val tickSeconds = usageAxisStepSeconds(peak, 4)
    val maximum = tickSeconds * 4L
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val labels = (0..4).map { tick ->
        textMeasurer.measure(usageAxisLabel(tickSeconds * tick), TextStyle(fontSize = 10.sp, color = labelColor))
    }
    val axisWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
        labels.maxOf { it.size.width }.toDp() + 8.dp
    }
    Row(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Canvas(Modifier.width(axisWidth).height(160.dp)) {
            labels.forEachIndexed { tick, label ->
                val y = size.height * (1f - tick / 4f)
                drawText(label, topLeft = Offset(size.width - label.size.width - 8.dp.toPx(), y - label.size.height / 2f))
            }
        }
        Box(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                (0..4).forEach { tick ->
                    val y = size.height * tick / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                drawLine(gridColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                buckets.forEachIndexed { index, bucket ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height(160.dp).clickable { onSelect(index) },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (bucket.total > 0) Column(
                                Modifier.fillMaxWidth(.72f)
                                    .fillMaxHeight((bucket.total.toFloat() / maximum).coerceIn(0f, 1f)),
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                bucket.segments.forEachIndexed { segmentIndex, segment ->
                                    Box(Modifier.fillMaxWidth().weight(segment.seconds.toFloat().coerceAtLeast(1f)).background(colors[segmentIndex]))
                                }
                            }
                        }
                        Text(bucket.label, Modifier.padding(top = 8.dp).heightIn(min = 22.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDonutChart(bucket: UsageBucket, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val description = stringResource(R.string.usage_share)
    Canvas(modifier.semantics { contentDescription = description }) {
        val diameter = size.minDimension * .82f
        val left = (size.width - diameter) / 2f
        val topOffset = (size.height - diameter) / 2f
        var start = -90f
        bucket.segments.forEachIndexed { index, segment ->
            val sweep = segment.seconds.toFloat() / bucket.total.coerceAtLeast(1) * 360f
            drawArc(colors[index], start, sweep, false, topLeft = Offset(left, topOffset), size = androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(diameter * .2f, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
private fun UsageShareContent(bucket: UsageBucket) {
    val colors = chartColors()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.usage_share), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${bucket.label} · ${duration(bucket.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (bucket.total == 0L) {
            Text(stringResource(R.string.no_usage_period), style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UsageDonutChart(bucket, Modifier.size(96.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    bucket.segments.forEachIndexed { index, segment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(colors[index % colors.size], MaterialTheme.shapes.small))
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(segment.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text("${segment.seconds * 100 / bucket.total}% · ${duration(segment.seconds)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppRow(app: AppUsage, openApp: (AppUsage) -> Unit) {
    val installedApp = installedApp(app.packageName)
    val displayName = installedApp?.label?.takeIf(String::isNotBlank) ?: app.displayName
    ListItem(
        modifier = Modifier.clickable { openApp(app) },
        leadingContent = installedApp?.icon?.let { icon ->
            { Image(BitmapPainter(icon), contentDescription = null, modifier = Modifier.size(40.dp)) }
        },
        headlineContent = { Text(displayName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(duration(app.usageSeconds)) },
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
                icon = packageManager.getApplicationIcon(info).toBitmap().asImageBitmap(),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

private data class InstalledApp(val label: String, val icon: ImageBitmap)

@Composable
private fun AppDetailScreen(
    detail: AppDetailUiState,
    lastAggregationMillis: Long?,
    select: (AppHistoryPreset) -> Unit,
    movePeriod: (Long) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val installedApp = installedApp(detail.app.packageName)
    val name = installedApp?.label?.takeIf(String::isNotBlank) ?: detail.app.displayName
    val locale = LocalConfiguration.current.locales[0]
    val patterns = remember(detail.days) { usagePatterns(detail.days) }
    val peakDay = detail.days.filter { it.usageSeconds > 0 }.maxByOrNull { it.usageSeconds }
    val total = detail.days.sumOf { it.usageSeconds }
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
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var expanded by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    UiIconButton(UiIcon.PREVIOUS, stringResource(R.string.previous_period), { movePeriod(1) }, iconSize = 18.dp)
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(stringResource(detail.preset.labelRes))
                            UiIconGraphic(UiIcon.EXPAND, Modifier.padding(start = 8.dp).size(18.dp))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            AppHistoryPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(preset.labelRes)) },
                                    onClick = { expanded = false; select(preset) },
                                    trailingIcon = { if (preset == detail.preset) Text("✓") },
                                )
                            }
                        }
                    }
                    UiIconButton(UiIcon.NEXT, stringResource(R.string.next_period), { movePeriod(-1) }, enabled = detail.canMoveToNewerPeriod, iconSize = 18.dp)
                }
                Text(formatRange(detail.range), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                UsageLineChart(detail, Modifier.fillMaxWidth().height(290.dp).padding(14.dp))
            }
        }
        if (total == 0L) item { Text(stringResource(R.string.no_app_usage_period)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(stringResource(R.string.total_all_time), duration(detail.calendarDays.sumOf { it.usageSeconds }), Modifier.weight(1f))
                    MetricCard(stringResource(R.string.total_in_period, stringResource(detail.preset.labelRes)), duration(total), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        stringResource(R.string.most_used_day),
                        peakDay?.let { "${it.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))} · ${duration(it.usageSeconds)}" } ?: "—",
                        Modifier.weight(1f),
                    )
                    MetricCard(stringResource(R.string.active_days), "${patterns.activeDays} / ${detail.range.dayCount}", Modifier.weight(1f))
                }
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
                DetailSectionTitle(stringResource(R.string.top_consecutive_days))
                ConsecutiveUsageDays(detail.days)
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
    val data = remember(days, today) {
        calendarChartData(days, DateRange(LocalDate.of(2010, 1, 1), today), maximumWeeks = Int.MAX_VALUE)
    }
    val columns = remember(data) { data.cells.groupBy { it.column } }
    var selected by remember(data) { mutableStateOf(data.cells.lastOrNull { it.usageSeconds > 0 } ?: data.cells.lastOrNull()) }
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = data.weekCount - 1)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onEmpty = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val textMeasurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val description = stringResource(R.string.calendar_chart_description)
    val previousDay = stringResource(R.string.previous_day)
    val nextDay = stringResource(R.string.next_day)
    fun moveSelection(offset: Long): Boolean {
        val target = selected?.date?.plusDays(offset) ?: return false
        return data.cells.firstOrNull { it.date == target }?.let { selected = it; true } ?: false
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columnWidth = (maxWidth / 14).coerceAtLeast(28.dp)
                LazyRow(
                    state = scroll,
                    modifier = Modifier.fillMaxWidth().height(230.dp).semantics {
                        contentDescription = description
                        stateDescription = selected?.let { "${it.date}, ${duration(it.usageSeconds)}" }.orEmpty()
                        customActions = listOf(
                            CustomAccessibilityAction(previousDay) { moveSelection(-1) },
                            CustomAccessibilityAction(nextDay) { moveSelection(1) },
                        )
                    },
                ) {
                    items(data.weekCount, key = { it }) { column ->
                        val cells = columns[column].orEmpty()
                        Canvas(Modifier.width(columnWidth).fillMaxHeight().pointerInput(cells) {
                            detectTapGestures { position ->
                                val plotTop = 22.dp.toPx()
                                if (position.y >= plotTop) {
                                    val row = ((position.y - plotTop) / ((size.height - plotTop) / 7f)).toInt()
                                    cells.firstOrNull { it.row == row }?.let { selected = it }
                                }
                            }
                        }) {
                            val gap = 2.dp.toPx()
                            val plotTop = 22.dp.toPx()
                            val cellHeight = (size.height - plotTop) / 7f
                            val date = cells.firstOrNull()?.date
                            if (date != null && (column == 0 || cells.any { it.date.dayOfMonth == 1 })) {
                                val monthDate = cells.firstOrNull { it.date.dayOfMonth == 1 }?.date ?: date
                                val month = monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
                                drawText(textMeasurer.measure(month, TextStyle(fontSize = 10.sp, color = onEmpty)), topLeft = Offset(gap, 0f))
                            }
                            // Complete partial weeks without turning future dates into usage data.
                            repeat(7) { row ->
                                if (cells.none { it.row == row }) {
                                    val topLeft = Offset(gap, plotTop + row * cellHeight + gap)
                                    val cellSize = androidx.compose.ui.geometry.Size(size.width - gap * 2, cellHeight - gap * 2)
                                    drawRoundRect(empty.copy(alpha = .45f), topLeft, cellSize, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                                    val placeholderDate = data.firstWeekStart.plusWeeks(column.toLong()).plusDays(row.toLong())
                                    val measured = textMeasurer.measure(placeholderDate.dayOfMonth.toString(), TextStyle(fontSize = 10.sp, color = onEmpty.copy(alpha = .45f), fontWeight = FontWeight.Medium))
                                    drawText(measured, topLeft = Offset(topLeft.x + (cellSize.width - measured.size.width) / 2f, topLeft.y + (cellSize.height - measured.size.height) / 2f))
                                }
                            }
                            cells.forEach { cell ->
                                val topLeft = Offset(gap, plotTop + cell.row * cellHeight + gap)
                                val cellSize = androidx.compose.ui.geometry.Size(size.width - gap * 2, cellHeight - gap * 2)
                                val color = if (cell.usageSeconds == 0L) empty else primary.copy(alpha = .28f + .72f * cell.intensity)
                                drawRoundRect(color, topLeft, cellSize, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                                if (cell == selected) drawRoundRect(outline, topLeft, cellSize, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
                                val labelColor = if (cell.usageSeconds > 0 && cell.intensity > .55f) onPrimary else onEmpty
                                val measured = textMeasurer.measure(cell.date.dayOfMonth.toString(), TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Medium))
                                drawText(measured, topLeft = Offset(topLeft.x + (cellSize.width - measured.size.width) / 2f, topLeft.y + (cellSize.height - measured.size.height) / 2f))
                            }
                        }
                    }
                }
            }
            selected?.let {
                Text("${it.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))} · ${duration(it.usageSeconds)}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConsecutiveUsageDays(days: List<DayUsage>) {
    val streaks = remember(days) { bestUsageStreaks(days) }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    if (streaks.isEmpty()) {
        Text(stringResource(R.string.no_streaks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            streaks.take(5).forEach { streak ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            pluralStringResource(R.plurals.streak_days, streak.dayCount.toInt(), streak.dayCount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("${streak.start.format(formatter)} – ${streak.endInclusive.format(formatter)}", style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { streak.dayCount.toFloat() / streaks.first().dayCount },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageLineChart(detail: AppDetailUiState, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val points = detailChartPoints(detail.preset, detail.range, detail.days, detail.hourlySeconds, java.time.LocalDateTime.now())
    val maximum = usageAxisStepSeconds(points.maxOfOrNull { it.seconds ?: 0L } ?: 0L, 3) * 3
    val axisLabels = yAxisLabelValues(maximum).map { value ->
        textMeasurer.measure(usageAxisLabel(requireNotNull(value)), TextStyle(fontSize = 10.sp, color = labelColor))
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val axisGap = with(density) { 8.dp.toPx() }
    val axisWidth = axisLabels.maxOf { it.size.width } + axisGap
    var selectedIndex by remember(detail.preset, detail.range, points) {
        mutableIntStateOf(points.indexOfLast { (it.seconds ?: 0L) > 0 }.takeIf { it >= 0 }
            ?: points.indexOfLast { it.seconds != null }.coerceAtLeast(0))
    }
    val selected = points.getOrNull(selectedIndex)
    val unavailable = stringResource(R.string.hourly_usage_unavailable)
    val noUsage = stringResource(R.string.no_app_usage_period)
    val noValue = "—"
    val selectedDescription = selected?.let { point ->
        val label = when (detail.preset) {
            AppHistoryPreset.TODAY -> "${point.date.format(DateTimeFormatter.ofPattern("d MMM", locale))} · ${point.hour}:00–${point.hour!! + 1}:00"
            AppHistoryPreset.YEAR -> point.date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
            else -> point.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
        }
        val value = point.seconds?.let(::duration) ?: noValue
        "$label · $value"
    }.orEmpty()
    val previous = stringResource(R.string.previous_chart_point)
    val next = stringResource(R.string.next_chart_point)
    val description = stringResource(R.string.usage_trend_chart_description)
    fun moveSelection(offset: Int): Boolean {
        val target = selectedIndex + offset
        if (target !in points.indices) return false
        selectedIndex = target
        return true
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.width(with(density) { axisWidth.toDp() }).fillMaxHeight()) {
                val top = 8.dp.toPx()
                val bottom = size.height - 28.dp.toPx()
                axisLabels.forEachIndexed { index, label ->
                    val y = top + (bottom - top) * index / 3f
                    drawText(label, topLeft = Offset(size.width - axisGap - label.size.width, (y - label.size.height / 2f).coerceAtLeast(0f)))
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                // Every hour/day/month keeps a readable label; the Y axis stays fixed when scrolling.
                val plotWidth = maxOf(maxWidth, (points.size * 28).dp)
                key(detail.preset, detail.range) {
                    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Canvas(
                            Modifier.width(plotWidth).fillMaxHeight().semantics {
                                contentDescription = description
                                stateDescription = selectedDescription
                                customActions = listOf(
                                    CustomAccessibilityAction(previous) { moveSelection(-1) },
                                    CustomAccessibilityAction(next) { moveSelection(1) },
                                )
                            }.pointerInput(points) {
                                // Horizontal dragging belongs to the scroll container; taps select a bucket.
                                detectTapGestures { position ->
                                    val slot = size.width.toFloat() / points.size.coerceAtLeast(1)
                                    selectedIndex = (position.x / slot).toInt().coerceIn(0, points.lastIndex)
                                }
                            },
                        ) {
                            val top = 8.dp.toPx()
                            val bottom = size.height - 28.dp.toPx()
                            val height = (bottom - top).coerceAtLeast(1f)
                            repeat(4) { index ->
                                val y = top + height * index / 3f
                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            }
                            drawLine(gridColor, Offset(0f, top), Offset(0f, bottom), 1.dp.toPx())
                            val slot = size.width / points.size.coerceAtLeast(1)
                            val positions = points.mapIndexed { index, point ->
                                point.seconds?.let { seconds -> Offset(slot * (index + .5f), bottom - seconds.toFloat() / maximum * height) }
                            }
                            positions.zipWithNext().forEach { (start, end) ->
                                if (start != null && end != null) drawLine(lineColor, start, end, 2.dp.toPx())
                            }
                            positions.filterNotNull().forEach { drawCircle(lineColor, 2.5.dp.toPx(), it) }
                            positions.getOrNull(selectedIndex)?.let { position ->
                                drawLine(labelColor.copy(alpha = .5f), Offset(position.x, top), Offset(position.x, bottom), 1.dp.toPx())
                                drawCircle(lineColor, 4.dp.toPx(), position)
                            }
                            points.forEachIndexed { index, point ->
                                val x = slot * (index + .5f)
                                drawLine(gridColor, Offset(x, bottom), Offset(x, bottom + 3.dp.toPx()), 1.dp.toPx())
                                val label = when (detail.preset) {
                                    AppHistoryPreset.TODAY -> point.hour.toString().padStart(2, '0')
                                    AppHistoryPreset.WEEK -> point.date.format(DateTimeFormatter.ofPattern("EEEEE", locale))
                                    AppHistoryPreset.MONTH -> point.date.dayOfMonth.toString()
                                    AppHistoryPreset.YEAR -> point.date.format(DateTimeFormatter.ofPattern("MMM", locale))
                                }
                                val measured = textMeasurer.measure(label, TextStyle(fontSize = 10.sp, color = labelColor))
                                drawText(measured, topLeft = Offset(x - measured.size.width / 2f, bottom + 6.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
        Text(
            if (detail.preset == AppHistoryPreset.TODAY && detail.hourlySeconds == null) {
                if (detail.days.any { it.usageSeconds > 0 }) unavailable else noUsage
            } else selectedDescription,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
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
        item {
            OutlinedButton(onClick = { uriHandler.openUri("https://github.com/cdmngz/screen-consume/blob/main/PRIVACY.md") }) {
                Text(stringResource(R.string.open_privacy_policy))
            }
        }
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
