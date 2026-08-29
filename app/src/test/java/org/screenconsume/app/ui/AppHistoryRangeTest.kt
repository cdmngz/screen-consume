package org.screenconsume.app.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppHistoryRangeTest {
    @Test
    fun `current year stops today`() {
        val today = LocalDate.of(2026, 8, 28)

        assertEquals(LocalDate.of(2026, 1, 1), appHistoryYearRange(today, 0).start)
        assertEquals(today, appHistoryYearRange(today, 0).endInclusive)
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

        assertEquals(today, appHistoryYearRange(today, -1).endInclusive)
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
}
