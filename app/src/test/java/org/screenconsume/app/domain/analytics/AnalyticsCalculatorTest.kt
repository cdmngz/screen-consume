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
}
