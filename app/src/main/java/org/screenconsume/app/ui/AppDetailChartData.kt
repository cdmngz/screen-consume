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
