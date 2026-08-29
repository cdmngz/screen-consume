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
    WEEK(R.string.seven_days), MONTH(R.string.thirty_days), YEAR(R.string.year)
}

data class AppDetailUiState(
    val app: AppUsage,
    val preset: AppHistoryPreset,
    val range: DateRange,
    val days: List<DayUsage> = emptyList(),
    val calendarDays: List<DayUsage> = emptyList(),
    val canMoveToNewerYear: Boolean = false,
)

data class HeadlineStats(
    val todaySeconds: Long = 0,
    val monthAverageSeconds: Long = 0,
    val previousMonthAverageSeconds: Long = 0,
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
    private val appHistoryYearOffset = MutableStateFlow(0L)

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

    private val headlineStats = LocalDate.now().let { today ->
        val currentMonth = YearMonth.from(today)
        val currentRange = DateRange(currentMonth.atDay(1), today)
        val previousMonth = currentMonth.minusMonths(1)
        val previousRange = DateRange(previousMonth.atDay(1), previousMonth.atEndOfMonth())
        combine(
            repository.dashboard(DateRange.endingToday(1, today)),
            repository.dashboard(currentRange),
            repository.dashboard(previousRange),
        ) { todayStats, monthStats, previousStats ->
            HeadlineStats(todayStats.totalSeconds, monthStats.averageDailySeconds, previousStats.averageDailySeconds)
        }
    }

    val state: StateFlow<MainUiState> = combine(access, rangeDashboard, headlineStats, repository.lastSuccessfulAggregationMillis, operation) { granted, dashboard, headlines, lastRun, task ->
        MainUiState(granted, dashboard.preset, dashboard.range, dashboard.stats, headlines, dashboard.dailyApps, lastRun, task.message, task.inProgress, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val appDetail: StateFlow<AppDetailUiState?> = combine(selectedApp, appHistoryPreset, appHistoryYearOffset) { app, selected, yearOffset ->
        if (app == null) null else {
            val today = LocalDate.now()
            val detailRange = when (selected) {
                AppHistoryPreset.WEEK -> DateRange.endingToday(7, today)
                AppHistoryPreset.MONTH -> DateRange.endingToday(30, today)
                AppHistoryPreset.YEAR -> appHistoryYearRange(today, yearOffset)
            }
            Triple(app, selected, detailRange) to yearOffset
        }
    }.flatMapLatest { selection ->
        if (selection == null) flowOf(null) else {
            val (details, yearOffset) = selection
            val (app, selected, detailRange) = details
            combine(
                repository.appHistory(app.packageName, detailRange),
                repository.appHistory(app.packageName, DateRange(LocalDate.of(2010, 1, 1), LocalDate.now())),
            ) { days, calendarDays -> AppDetailUiState(app, selected, detailRange, days, calendarDays, yearOffset > 0) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPreset(value: RangePreset) { periodOffset.value = 0; preset.value = value }
    fun movePeriod(periodsOlder: Long) {
        periodOffset.value = (periodOffset.value + periodsOlder).coerceAtLeast(0)
    }
    fun openApp(app: AppUsage) {
        selectedApp.value = app
        appHistoryPreset.value = AppHistoryPreset.WEEK
        appHistoryYearOffset.value = 0
    }
    fun closeApp() { selectedApp.value = null; appHistoryYearOffset.value = 0 }
    fun selectAppHistoryPreset(value: AppHistoryPreset) { appHistoryYearOffset.value = 0; appHistoryPreset.value = value }
    fun moveAppHistoryYear(yearsOlder: Long) {
        appHistoryYearOffset.value = (appHistoryYearOffset.value + yearsOlder).coerceAtLeast(0)
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
