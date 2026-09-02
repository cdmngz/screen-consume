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
    TODAY(R.string.today), WEEK(R.string.week), MONTH(R.string.month), SEMESTER(R.string.semester), YEAR(R.string.year)
}

data class AppDetailUiState(
    val app: AppUsage,
    val preset: AppHistoryPreset,
    val range: DateRange,
    val days: List<DayUsage> = emptyList(),
    val calendarDays: List<DayUsage> = emptyList(),
    val canMoveToNewerPeriod: Boolean = false,
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

private data class RangeDashboard(val preset: RangePreset, val range: DateRange, val stats: DashboardStats, val dailyApps: List<DailyAppUsage>)
private data class OperationState(val inProgress: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: UsageRepository) : ViewModel() {
    private val preset = MutableStateFlow(RangePreset.TODAY)
    private val periodOffset = MutableStateFlow(0L)
    private val access = MutableStateFlow(repository.hasUsageAccess)
    private val operation = MutableStateFlow(OperationState())
    private val selectedApp = MutableStateFlow<AppUsage?>(null)
    private val appHistoryPreset = MutableStateFlow(AppHistoryPreset.WEEK)
    private val appHistoryPeriodOffset = MutableStateFlow(0L)

    private val range = combine(preset, periodOffset) { selected, offset ->
        val today = LocalDate.now()
        when (selected) {
            RangePreset.TODAY -> DateRange.endingToday(1, today.minusDays(offset))
            RangePreset.WEEK -> DateRange.endingToday(7, today.minusWeeks(offset))
            RangePreset.MONTH -> YearMonth.from(today).minusMonths(offset).let { month ->
                DateRange(month.atDay(1), minOf(month.atEndOfMonth(), today))
            }
            RangePreset.YEAR -> today.year.minus(offset.toInt()).let { year ->
                DateRange(LocalDate.of(year, 1, 1), minOf(LocalDate.of(year, 12, 31), today))
            }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, DateRange.endingToday(1))

    private val rangeDashboard = combine(preset, range.flatMapLatest { selectedRange ->
        combine(repository.dashboard(selectedRange), repository.dailyAppUsage(selectedRange)) { stats, apps -> Triple(selectedRange, stats, apps) }
    }) { selected, data -> RangeDashboard(selected, data.first, data.second, data.third) }

    private val headlineStats = range.flatMapLatest { selectedRange ->
        val day = selectedRange.endInclusive
        val month = YearMonth.from(day)
        val monthRange = DateRange(month.atDay(1), minOf(month.atEndOfMonth(), LocalDate.now()))
        combine(
            repository.dashboard(DateRange(day, day)),
            repository.dashboard(monthRange),
        ) { dayStats, monthStats ->
            HeadlineStats(dayStats.totalSeconds, monthStats.averageDailySeconds, day)
        }
    }

    val state: StateFlow<MainUiState> = combine(access, rangeDashboard, headlineStats, repository.lastSuccessfulAggregationMillis, operation) { granted, dashboard, headlines, lastRun, task ->
        MainUiState(granted, dashboard.preset, dashboard.range, dashboard.stats, headlines, dashboard.dailyApps, lastRun, task.message, task.inProgress, false)
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
            combine(
                repository.appHistory(app.packageName, detailRange),
                repository.appHistory(app.packageName, DateRange(LocalDate.of(2010, 1, 1), LocalDate.now())),
            ) { days, calendarDays -> AppDetailUiState(app, selected, detailRange, days, calendarDays, offset > 0) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPreset(value: RangePreset) { periodOffset.value = 0; preset.value = value }
    fun movePeriod(periodsOlder: Long) {
        periodOffset.value = (periodOffset.value + periodsOlder).coerceAtLeast(0)
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
        if (year == today.year) today else LocalDate.of(year, 12, 31),
    )
}

internal fun appHistoryRange(today: LocalDate, preset: AppHistoryPreset, periodsOlder: Long): DateRange {
    val offset = periodsOlder.coerceAtLeast(0)
    return when (preset) {
        AppHistoryPreset.TODAY -> today.minusDays(offset).let { DateRange(it, it) }
        AppHistoryPreset.WEEK -> DateRange.endingToday(7, today.minusWeeks(offset))
        AppHistoryPreset.MONTH -> YearMonth.from(today).minusMonths(offset).let {
            DateRange(it.atDay(1), minOf(it.atEndOfMonth(), today))
        }
        AppHistoryPreset.SEMESTER -> {
            val start = LocalDate.of(today.year, if (today.monthValue <= 6) 1 else 7, 1).minusMonths(offset * 6)
            DateRange(start, minOf(start.plusMonths(6).minusDays(1), today))
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
