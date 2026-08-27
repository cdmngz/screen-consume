package org.screenconsume.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import org.screenconsume.app.domain.model.*
import java.time.LocalDate

class AnalyticsCalculatorTest {
    @Test fun `date range produces equal previous period`() {
        val range = DateRange(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 14))
        assertEquals(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7)), range.previous())
        assertEquals(7, range.dayCount)
    }

    @Test fun `calculates totals averages launches and comparison`() {
        val range = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))
        val current = listOf(AppUsage("a", "A", null, 600, 3), AppUsage("b", "B", null, 300, 2))
        val previous = listOf(AppUsage("a", "A", null, 600, 1))
        val result = AnalyticsCalculator.calculate(range, current, previous, emptyList())
        assertEquals(900, result.totalSeconds)
        assertEquals(450, result.averageDailySeconds)
        assertEquals(5, result.launchCount)
        assertEquals(50, result.comparisonPercent)
    }

    @Test fun `sorts applications by usage and preserves daily values`() {
        val range = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
        val days = listOf(DayUsage(range.start, 42))
        val result = AnalyticsCalculator.calculate(
            range,
            listOf(AppUsage("small", "Small", null, 10, 1), AppUsage("large", "Large", null, 30, 2)),
            emptyList(),
            days,
        )

        assertEquals(listOf("large", "small"), result.apps.map { it.packageName })
        assertEquals(days, result.days)
        assertEquals(null, result.comparisonPercent)
    }

    @Test fun `empty current and previous periods compare as no change`() {
        val range = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))

        val result = AnalyticsCalculator.calculate(range, emptyList(), emptyList(), emptyList())

        assertEquals(0, result.totalSeconds)
        assertEquals(0, result.averageDailySeconds)
        assertEquals(0, result.comparisonPercent)
    }

    @Test fun `date range rejects an end before its start`() {
        val error = runCatching {
            DateRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1))
        }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
