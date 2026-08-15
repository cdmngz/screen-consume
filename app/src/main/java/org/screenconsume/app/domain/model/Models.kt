package org.screenconsume.app.domain.model

import java.time.LocalDate

data class UsageInterval(val packageName: String, val startMillis: Long, val endMillis: Long)

data class DailyAggregate(
    val date: LocalDate,
    val packageName: String,
    val usageSeconds: Long,
    val launchCount: Int,
    val morningUsageSeconds: Long,
    val afternoonUsageSeconds: Long,
    val eveningUsageSeconds: Long,
    val nightUsageSeconds: Long,
)

data class AppUsage(
    val packageName: String,
    val displayName: String,
    val category: String?,
    val usageSeconds: Long,
    val launchCount: Int,
)

data class DayUsage(val date: LocalDate, val usageSeconds: Long)

data class DashboardStats(
    val totalSeconds: Long = 0,
    val previousTotalSeconds: Long = 0,
    val averageDailySeconds: Long = 0,
    val launchCount: Int = 0,
    val apps: List<AppUsage> = emptyList(),
    val days: List<DayUsage> = emptyList(),
) {
    val comparisonPercent: Int?
        get() = when {
            previousTotalSeconds == 0L && totalSeconds == 0L -> 0
            previousTotalSeconds == 0L -> null
            else -> (((totalSeconds - previousTotalSeconds) * 100.0) / previousTotalSeconds).toInt()
        }
}

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init { require(!endInclusive.isBefore(start)) }
    val dayCount: Long get() = endInclusive.toEpochDay() - start.toEpochDay() + 1
    fun previous(): DateRange = DateRange(start.minusDays(dayCount), start.minusDays(1))

    companion object {
        fun endingToday(days: Long, today: LocalDate = LocalDate.now()) =
            DateRange(today.minusDays(days - 1), today)
    }
}

