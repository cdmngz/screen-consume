package org.screenconsume.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.screenconsume.app.data.repository.UsageRepository
import org.screenconsume.app.domain.model.DashboardStats
import org.screenconsume.app.domain.model.DateRange
import java.time.LocalDate

enum class Period(val label: String, val days: Long) { TODAY("Today", 1), WEEK("7 days", 7), MONTH("30 days", 30) }

data class MainUiState(
    val hasUsageAccess: Boolean = false,
    val period: Period = Period.TODAY,
    val stats: DashboardStats = DashboardStats(),
    val loading: Boolean = true,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: UsageRepository) : ViewModel() {
    private val period = MutableStateFlow(Period.TODAY)
    private val access = MutableStateFlow(repository.hasUsageAccess)

    val state: StateFlow<MainUiState> = combine(
        access,
        period.flatMapLatest { selected -> repository.dashboard(DateRange.endingToday(selected.days)) },
        period,
    ) { granted, stats, selected -> MainUiState(granted, selected, stats, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun selectPeriod(value: Period) { period.value = value }

    fun refresh() {
        access.value = repository.hasUsageAccess
        if (access.value) viewModelScope.launch { repository.aggregate(LocalDate.now()) }
    }

    class Factory(private val repository: UsageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository) as T
    }
}
