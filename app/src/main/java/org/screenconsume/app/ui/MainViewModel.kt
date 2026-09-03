package org.screenconsume.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.screenconsume.app.R
import org.screenconsume.app.data.repository.UsageRepository
import org.screenconsume.app.domain.model.DashboardStats
import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.AppUsage
import org.screenconsume.app.domain.model.DayUsage
import org.screenconsume.app.domain.model.DailyAppUsage
import java.time.LocalDate
import java.time.YearMonth

enum class RangePreset(val labelRes: Int) {
    TODAY(R.string.day), WEEK(R.string.week), MONTH(R.string.month), YEAR(R.string.year)
}

enum class AppHistoryPreset(val labelRes: Int) {
    TODAY(R.string.today), WEEK(R.string.week), MONTH(R.string.month), YEAR(R.string.year)
}

data class AppDetailUiState(
    val app: AppUsage,
    val preset: AppHistoryPreset,
    val range: DateRange,
    val days: List<DayUsage> = emptyList(),
    val calendarDays: List<DayUsage> = emptyList(),
    val canMoveToNewerPeriod: Boolean = false,
    val hourlySeconds: List<Long>? = null,
)

data class HeadlineStats(
    val todaySeconds: Long = 0,
    val monthAverageSeconds: Long = 0,
    val month: LocalDate = LocalDate.now(),
)

data class MainUiState(
    val hasUsageAccess: Boolean = false,
    val preset: RangePreset = RangePreset.TODAY,
    val range: DateRange = DateRange.endingToday(1),
    val stats: DashboardStats = DashboardStats(),
    val headlineStats: HeadlineStats = HeadlineStats(),
    val dailyApps: List<DailyAppUsage> = emptyList(),
    val lastSuccessfulAggregationMillis: Long? = null,
    val operationMessage: String? = null,
    val operationInProgress: Boolean = false,
    val loading: Boolean = true,
)

internal data class RangeDashboard(
    val preset: RangePreset,
    val range: DateRange,
    val stats: DashboardStats = DashboardStats(),
    val dailyApps: List<DailyAppUsage> = emptyList(),
    val headlines: HeadlineStats = HeadlineStats(),
    val loading: Boolean = true,
)
private data class OperationState(val inProgress: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: UsageRepository) : ViewModel() {
    private val selection = MutableStateFlow(RangePreset.TODAY to 0L)
    private val access = MutableStateFlow(repository.hasUsageAccess)
    private val operation = MutableStateFlow(OperationState())
    private val selectedApp = MutableStateFlow<AppUsage?>(null)
    private val appHistoryPreset = MutableStateFlow(AppHistoryPreset.WEEK)
    private val appHistoryPeriodOffset = MutableStateFlow(0L)

    private val range = selection.map { (selected, offset) ->
        val today = LocalDate.now()
        selected to when (selected) {
            RangePreset.TODAY -> DateRange.endingToday(1, today.minusDays(offset))
            RangePreset.WEEK -> DateRange.endingToday(7, today.minusWeeks(offset))
            RangePreset.MONTH -> YearMonth.from(today).minusMonths(offset).let { month ->
                DateRange(month.atDay(1), minOf(month.atEndOfMonth(), today))
            }
            RangePreset.YEAR -> today.year.minus(offset.toInt()).let { year ->
                DateRange(LocalDate.of(year, 1, 1), minOf(LocalDate.of(year, 12, 31), today))
            }
        }
    }.distinctUntilChanged()

    private val rangeDashboard = observeRangeDashboard(range, repository::dashboard, repository::dailyAppUsage)

    val state: StateFlow<MainUiState> = combine(access, rangeDashboard, repository.lastSuccessfulAggregationMillis, operation) { granted, dashboard, lastRun, task ->
        MainUiState(granted, dashboard.preset, dashboard.range, dashboard.stats, dashboard.headlines, dashboard.dailyApps, lastRun, task.message, task.inProgress, dashboard.loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val appDetail: StateFlow<AppDetailUiState?> = combine(selectedApp, appHistoryPreset, appHistoryPeriodOffset) { app, selected, offset ->
        if (app == null) null else {
            val today = LocalDate.now()
            val detailRange = appHistoryRange(today, selected, offset)
            Triple(app, selected, detailRange) to offset
        }
    }.flatMapLatest { selection ->
        if (selection == null) flowOf(null) else {
            val (details, offset) = selection
            val (app, selected, detailRange) = details
            val hours: Flow<List<Long>?> = if (selected == AppHistoryPreset.TODAY) flow {
                emit(repository.appHourlyUsage(app.packageName, detailRange.start))
            } else flowOf(null)
            combine(
                repository.appHistory(app.packageName, detailRange),
                repository.appHistory(app.packageName, DateRange(LocalDate.of(2010, 1, 1), LocalDate.now())),
                hours,
            ) { days, calendarDays, hourly -> AppDetailUiState(app, selected, detailRange, days, calendarDays, offset > 0, hourly) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPreset(value: RangePreset) { selection.value = value to 0L }
    fun movePeriod(periodsOlder: Long) {
        selection.update { (preset, offset) -> preset to (offset + periodsOlder).coerceAtLeast(0) }
    }
    fun openApp(app: AppUsage) {
        selectedApp.value = app
        appHistoryPreset.value = AppHistoryPreset.WEEK
        appHistoryPeriodOffset.value = 0
    }
    fun closeApp() { selectedApp.value = null; appHistoryPeriodOffset.value = 0 }
    fun selectAppHistoryPreset(value: AppHistoryPreset) { appHistoryPeriodOffset.value = 0; appHistoryPreset.value = value }
    fun moveAppHistoryPeriod(periodsOlder: Long) {
        appHistoryPeriodOffset.value = (appHistoryPeriodOffset.value + periodsOlder).coerceAtLeast(0)
    }
    fun clearOperationMessage() { operation.value = OperationState() }

    fun refresh() {
        access.value = repository.hasUsageAccess
        if (access.value) viewModelScope.launch { repository.aggregate(LocalDate.now()) }
    }

    fun exportCsv(uri: Uri, range: DateRange?) = perform("CSV export") {
        if (range == null) repository.exportAllCsv(uri) else repository.exportCsv(uri, range)
    }
    fun exportJson(uri: Uri, range: DateRange?) = perform("JSON export") {
        if (range == null) repository.exportAllJson(uri) else repository.exportJson(uri, range)
    }
    fun exportEncryptedBackup(uri: Uri, password: CharArray) = perform("Encrypted backup") { repository.exportEncryptedBackup(uri, password) }
    fun restore(uri: Uri, password: CharArray?) = perform("Restore") { repository.restore(uri, password) }

    private fun perform(label: String, block: suspend () -> Int) {
        viewModelScope.launch {
            operation.value = OperationState(inProgress = true)
            operation.value = try {
                val count = block()
                OperationState(message = "$label complete: $count daily app records")
            } catch (error: Exception) {
                OperationState(message = "$label failed: ${error.message ?: "unknown error"}")
            }
        }
    }

    class Factory(private val repository: UsageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository) as T
    }
}

internal fun appHistoryYearRange(today: LocalDate, yearsOlder: Long): DateRange {
    val year = today.year - yearsOlder.coerceAtLeast(0).toInt()
    return DateRange(
        LocalDate.of(year, 1, 1),
        LocalDate.of(year, 12, 31),
    )
}

internal fun appHistoryRange(today: LocalDate, preset: AppHistoryPreset, periodsOlder: Long): DateRange {
    val offset = periodsOlder.coerceAtLeast(0)
    return when (preset) {
        AppHistoryPreset.TODAY -> today.minusDays(offset).let { DateRange(it, it) }
        AppHistoryPreset.WEEK -> today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .minusWeeks(offset).let { DateRange(it, it.plusDays(6)) }
        AppHistoryPreset.MONTH -> YearMonth.from(today).minusMonths(offset).let {
            DateRange(it.atDay(1), it.atEndOfMonth())
        }
        AppHistoryPreset.YEAR -> appHistoryYearRange(today, offset)
    }
}

internal fun monthWeekRanges(date: LocalDate): List<DateRange> {
    val month = YearMonth.from(date)
    return (1..month.lengthOfMonth() step 7).map { day ->
        val start = month.atDay(day)
        DateRange(start, minOf(start.plusDays(6), month.atEndOfMonth()))
    }
}

/** Cancel the old range before emitting a selection with its matching query results. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeRangeDashboard(
    ranges: Flow<Pair<RangePreset, DateRange>>,
    dashboard: (DateRange) -> Flow<DashboardStats>,
    dailyAppUsage: (DateRange) -> Flow<List<DailyAppUsage>>,
): Flow<RangeDashboard> = ranges.flatMapLatest { (selected, selectedRange) ->
    val day = selectedRange.endInclusive
    val month = YearMonth.from(day)
    val monthRange = DateRange(month.atDay(1), minOf(month.atEndOfMonth(), LocalDate.now()))
    val headlines = combine(
        dashboard(DateRange(day, day)),
        dashboard(monthRange),
    ) { dayStats, monthStats ->
        HeadlineStats(dayStats.totalSeconds, monthStats.averageDailySeconds, day)
    }
    combine(
        dashboard(selectedRange),
        dailyAppUsage(selectedRange),
        headlines,
    ) { stats, apps, summary ->
        RangeDashboard(selected, selectedRange, stats, apps, summary, loading = false)
    }.onStart { emit(RangeDashboard(selected, selectedRange)) }
}
