package org.screenconsume.app.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppHistoryRangeTest {
    @Test
    fun `current year includes all twelve months`() {
        val today = LocalDate.of(2026, 8, 28)

        assertEquals(LocalDate.of(2026, 1, 1), appHistoryYearRange(today, 0).start)
        assertEquals(LocalDate.of(2026, 12, 31), appHistoryYearRange(today, 0).endInclusive)
    }

    @Test
    fun `older year covers its full calendar year`() {
        val range = appHistoryYearRange(LocalDate.of(2026, 8, 28), 3)

        assertEquals(LocalDate.of(2023, 1, 1), range.start)
        assertEquals(LocalDate.of(2023, 12, 31), range.endInclusive)
    }

    @Test
    fun `negative offset cannot navigate into the future`() {
        val today = LocalDate.of(2026, 8, 28)

        assertEquals(LocalDate.of(2026, 12, 31), appHistoryYearRange(today, -1).endInclusive)
    }

    @Test
    fun `right swipe navigates to an older period like the dashboard`() {
        assertEquals(1L, horizontalPageOffset(horizontalDistance = 120f, threshold = 100f))
    }

    @Test
    fun `left swipe navigates to a newer period like the dashboard`() {
        assertEquals(-1L, horizontalPageOffset(horizontalDistance = -120f, threshold = 100f))
    }

    @Test
    fun `short drag remains date inspection`() {
        assertEquals(null, horizontalPageOffset(horizontalDistance = 80f, threshold = 100f))
    }

    @Test
    fun `y axis labels omit the zero value at the x axis`() {
        assertEquals(listOf(90L, 60L, 30L), yAxisLabelValues(90L))
    }

    @Test
    fun `all month weeks exist even on the first day`() {
        val weeks = monthWeekRanges(LocalDate.of(2026, 9, 1))
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 1), weeks.first().start)
        assertEquals(LocalDate.of(2026, 9, 30), weeks.last().endInclusive)
        assertEquals(30L, weeks.sumOf { it.dayCount })
        assertEquals(4, monthWeekRanges(LocalDate.of(2026, 2, 1)).size)
        assertEquals(5, monthWeekRanges(LocalDate.of(2024, 2, 1)).size)
    }

    @Test
    fun `month navigation crosses year boundary`() {
        val range = appHistoryRange(LocalDate.of(2026, 1, 2), AppHistoryPreset.MONTH, 1)
        assertEquals(LocalDate.of(2025, 12, 1), range.start)
        assertEquals(LocalDate.of(2025, 12, 31), range.endInclusive)
    }

    @Test
    fun `current calendar periods include future days`() {
        val today = LocalDate.of(2026, 9, 2)
        val week = appHistoryRange(today, AppHistoryPreset.WEEK, 0)
        assertEquals(LocalDate.of(2026, 8, 31), week.start)
        assertEquals(LocalDate.of(2026, 9, 6), week.endInclusive)
        val month = appHistoryRange(today, AppHistoryPreset.MONTH, 0)
        assertEquals(LocalDate.of(2026, 9, 1), month.start)
        assertEquals(LocalDate.of(2026, 9, 30), month.endInclusive)
    }

    @Test
    fun `day and week arrows navigate by their own period`() {
        val today = LocalDate.of(2026, 9, 2)
        assertEquals(today.minusDays(1), appHistoryRange(today, AppHistoryPreset.TODAY, 1).start)
        val week = appHistoryRange(today, AppHistoryPreset.WEEK, 1)
        assertEquals(LocalDate.of(2026, 8, 24), week.start)
        assertEquals(LocalDate.of(2026, 8, 30), week.endInclusive)
        AppHistoryPreset.entries.forEach { preset ->
            assertEquals(appHistoryRange(today, preset, 0), appHistoryRange(today, preset, -1))
        }
    }
}
