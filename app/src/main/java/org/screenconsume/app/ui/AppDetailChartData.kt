package org.screenconsume.app.ui

import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.DayUsage
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.sqrt

internal fun horizontalPageOffset(horizontalDistance: Float, threshold: Float): Long? = when {
    horizontalDistance >= threshold -> 1L
    horizontalDistance <= -threshold -> -1L
    else -> null
}

internal fun yAxisLabelValues(maximum: Long): List<Long?> =
    (3 downTo 1).map { step -> (maximum * step / 3).takeIf { it > 0 } }

internal data class HistoryBucket(val label: String, val usageSeconds: Long)

internal data class FrequencyCell(
    val column: Int,
    val row: Int,
    val intensity: Float,
)

internal data class FrequencyChartData(
    val weekCount: Int,
    val cells: List<FrequencyCell>,
)

internal data class CalendarCell(
    val date: java.time.LocalDate,
    val column: Int,
    val row: Int,
    val usageSeconds: Long,
    val intensity: Float,
)

internal data class CalendarChartData(
    val firstWeekStart: java.time.LocalDate,
    val weekCount: Int,
    val cells: List<CalendarCell>,
)

internal data class UsageStreak(
    val start: java.time.LocalDate,
    val endInclusive: java.time.LocalDate,
) {
    val dayCount: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1
}

internal data class UsagePatterns(
    val mostUsedDay: DayOfWeek?,
    val weekdaySeconds: Long,
    val weekendSeconds: Long,
    val activeDays: Int,
)

internal fun historyBuckets(
    days: List<DayUsage>,
    range: DateRange,
    locale: Locale,
): List<HistoryBucket> = when {
    range.dayCount <= 14 -> (0 until range.dayCount).map { offset ->
        val date = range.start.plusDays(offset)
        HistoryBucket(date.dayOfMonth.toString(), days.firstOrNull { it.date == date }?.usageSeconds ?: 0)
    }
    range.dayCount <= 62 -> (0 until ((range.dayCount + 6) / 7)).map { week ->
        val start = range.start.plusDays(week * 7)
        val end = minOf(start.plusDays(6), range.endInclusive)
        HistoryBucket(
            start.dayOfMonth.toString(),
            days.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.sumOf { it.usageSeconds },
        )
    }
    else -> {
        val usageByMonth = days.groupBy { YearMonth.from(it.date) }.mapValues { (_, values) -> values.sumOf { it.usageSeconds } }
        val firstMonth = YearMonth.from(range.start)
        val monthCount = ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(range.endInclusive)).toInt() + 1
        (0 until monthCount).map { offset ->
            val month = firstMonth.plusMonths(offset.toLong())
            HistoryBucket(month.month.getDisplayName(TextStyle.NARROW, locale), usageByMonth[month] ?: 0)
        }
    }
}

internal fun frequencyChartData(
    days: List<DayUsage>,
    range: DateRange,
    maximumWeeks: Int = 14,
): FrequencyChartData {
    require(maximumWeeks > 0)
    val usageByDate = days.associate { it.date to it.usageSeconds }
    val lastWeekStart = range.endInclusive.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rangeWeekStart = range.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstWeekStart = maxOf(rangeWeekStart, lastWeekStart.minusWeeks((maximumWeeks - 1).toLong()))
    val weekCount = ChronoUnit.WEEKS.between(firstWeekStart, lastWeekStart).toInt() + 1
    val visibleValues = usageByDate.filterKeys { !it.isBefore(firstWeekStart) && !it.isAfter(range.endInclusive) }.values
    val maximum = visibleValues.maxOrNull()?.coerceAtLeast(1) ?: 1
    val cells = buildList {
        repeat(weekCount) { column ->
            repeat(7) { row ->
                val date = firstWeekStart.plusWeeks(column.toLong()).plusDays(row.toLong())
                val seconds = usageByDate[date] ?: 0
                if (seconds > 0 && !date.isBefore(range.start) && !date.isAfter(range.endInclusive)) {
                    add(FrequencyCell(column, row, sqrt(seconds.toFloat() / maximum)))
                }
            }
        }
    }
    return FrequencyChartData(weekCount, cells)
}

internal fun calendarChartData(
    days: List<DayUsage>,
    range: DateRange,
    maximumWeeks: Int = 14,
): CalendarChartData {
    require(maximumWeeks > 0)
    val usageByDate = days.associate { it.date to it.usageSeconds }
    val lastWeekStart = range.endInclusive.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rangeWeekStart = range.start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstWeekStart = maxOf(rangeWeekStart, lastWeekStart.minusWeeks((maximumWeeks - 1).toLong()))
    val weekCount = ChronoUnit.WEEKS.between(firstWeekStart, lastWeekStart).toInt() + 1
    val maximum = usageByDate.filterKeys { !it.isBefore(firstWeekStart) && !it.isAfter(range.endInclusive) }
        .values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val cells = buildList {
        repeat(weekCount) { column ->
            repeat(7) { row ->
                val date = firstWeekStart.plusWeeks(column.toLong()).plusDays(row.toLong())
                if (!date.isBefore(range.start) && !date.isAfter(range.endInclusive)) {
                    val seconds = usageByDate[date] ?: 0
                    add(CalendarCell(date, column, row, seconds, sqrt(seconds.toFloat() / maximum)))
                }
            }
        }
    }
    return CalendarChartData(firstWeekStart, weekCount, cells)
}

internal fun bestUsageStreaks(days: List<DayUsage>, limit: Int = 5): List<UsageStreak> {
    require(limit >= 0)
    val activeDates = days.asSequence().filter { it.usageSeconds > 0 }.map { it.date }.distinct().sorted().toList()
    if (activeDates.isEmpty() || limit == 0) return emptyList()
    val streaks = buildList {
        var start = activeDates.first()
        var previous = start
        activeDates.drop(1).forEach { date ->
            if (date != previous.plusDays(1)) {
                add(UsageStreak(start, previous))
                start = date
            }
            previous = date
        }
        add(UsageStreak(start, previous))
    }
    return streaks.sortedWith(compareByDescending<UsageStreak> { it.dayCount }.thenByDescending { it.endInclusive }).take(limit)
}

internal fun usagePatterns(days: List<DayUsage>): UsagePatterns {
    val active = days.filter { it.usageSeconds > 0 }
    val totalsByWeekday = active.groupBy { it.date.dayOfWeek }.mapValues { (_, values) -> values.sumOf { it.usageSeconds } }
    return UsagePatterns(
        mostUsedDay = totalsByWeekday.maxByOrNull { it.value }?.key,
        weekdaySeconds = active.filter { it.date.dayOfWeek.value <= 5 }.sumOf { it.usageSeconds },
        weekendSeconds = active.filter { it.date.dayOfWeek.value > 5 }.sumOf { it.usageSeconds },
        activeDays = active.map { it.date }.distinct().size,
    )
}
