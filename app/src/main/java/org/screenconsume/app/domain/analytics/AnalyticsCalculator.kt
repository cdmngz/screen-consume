package org.screenconsume.app.domain.analytics

import org.screenconsume.app.domain.model.*

object AnalyticsCalculator {
    fun calculate(range: DateRange, current: List<AppUsage>, previous: List<AppUsage>, days: List<DayUsage>): DashboardStats {
        val total = current.sumOf { it.usageSeconds }
        return DashboardStats(
            totalSeconds = total,
            previousTotalSeconds = previous.sumOf { it.usageSeconds },
            averageDailySeconds = total / range.dayCount,
            launchCount = current.sumOf { it.launchCount },
            apps = current.sortedByDescending { it.usageSeconds },
            days = days,
        )
    }
}
