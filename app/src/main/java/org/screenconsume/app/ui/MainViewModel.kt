package org.screenconsume.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.screenconsume.app.data.repository.UsageRepository
import org.screenconsume.app.domain.model.DashboardStats
import org.screenconsume.app.domain.model.DateRange
import java.time.LocalDate

enum class RangePreset(val label: String) {
    TODAY("Today"), WEEK("7 days"), MONTH("30 days"), THIS_MONTH("Month"), YEAR("Year"), ALL("All time"), CUSTOM("Custom")
}

data class MainUiState(
    val hasUsageAccess: Boolean = false,
    val preset: RangePreset = RangePreset.TODAY,
    val range: DateRange = DateRange.endingToday(1),
    val stats: DashboardStats = DashboardStats(),
    val lastSuccessfulAggregationMillis: Long? = null,
    val operationMessage: String? = null,
    val operationInProgress: Boolean = false,
    val loading: Boolean = true,
)

private data class RangeDashboard(val preset: RangePreset, val range: DateRange, val stats: DashboardStats)
private data class OperationState(val inProgress: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: UsageRepository) : ViewModel() {
    private val preset = MutableStateFlow(RangePreset.TODAY)
    private val customRange = MutableStateFlow(DateRange.endingToday(30))
    private val access = MutableStateFlow(repository.hasUsageAccess)
    private val operation = MutableStateFlow(OperationState())

    private val range = combine(preset, customRange, repository.earliestDate) { selected, custom, earliest ->
        val today = LocalDate.now()
        when (selected) {
            RangePreset.TODAY -> DateRange.endingToday(1, today)
            RangePreset.WEEK -> DateRange.endingToday(7, today)
            RangePreset.MONTH -> DateRange.endingToday(30, today)
            RangePreset.THIS_MONTH -> DateRange(today.withDayOfMonth(1), today)
            RangePreset.YEAR -> DateRange(today.withDayOfYear(1), today)
            RangePreset.ALL -> DateRange(earliest ?: today, today)
            RangePreset.CUSTOM -> custom
        }
    }.distinctUntilChanged()

    private val rangeDashboard = combine(preset, range.flatMapLatest { selectedRange ->
        repository.dashboard(selectedRange).map { selectedRange to it }
    }) { selected, pair -> RangeDashboard(selected, pair.first, pair.second) }

    val state: StateFlow<MainUiState> = combine(access, rangeDashboard, repository.lastSuccessfulAggregationMillis, operation) { granted, dashboard, lastRun, task ->
        MainUiState(granted, dashboard.preset, dashboard.range, dashboard.stats, lastRun, task.message, task.inProgress, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun selectPreset(value: RangePreset) { preset.value = value }
    fun selectCustomRange(value: DateRange) { customRange.value = value; preset.value = RangePreset.CUSTOM }
    fun clearOperationMessage() { operation.value = OperationState() }

    fun refresh() {
        access.value = repository.hasUsageAccess
        if (access.value) viewModelScope.launch { repository.aggregate(LocalDate.now()) }
    }

    fun exportCsv(uri: Uri) = perform("CSV export") { repository.exportCsv(uri, state.value.range) }
    fun exportJson(uri: Uri) = perform("JSON export") { repository.exportJson(uri, state.value.range) }
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
