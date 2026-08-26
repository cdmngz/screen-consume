package org.screenconsume.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.DayUsage
import java.time.LocalDate
import java.util.Locale

class AppDetailChartDataTest {
    @Test fun `short history includes unused days as zero value bars`() {
        val range = DateRange(date(1), date(3))

        val buckets = historyBuckets(listOf(DayUsage(date(2), 120)), range, Locale.ENGLISH)

        assertEquals(listOf(0L, 120L, 0L), buckets.map { it.usageSeconds })
        assertEquals(listOf("1", "2", "3"), buckets.map { it.label })
    }

    @Test fun `medium history sums usage into seven day buckets`() {
        val range = DateRange(date(1), date(21))
        val days = listOf(DayUsage(date(1), 10), DayUsage(date(7), 20), DayUsage(date(8), 40), DayUsage(date(21), 80))

        val buckets = historyBuckets(days, range, Locale.ENGLISH)

        assertEquals(listOf(30L, 40L, 80L), buckets.map { it.usageSeconds })
    }

    @Test fun `long history retains empty months`() {
        val range = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1))
        val days = listOf(DayUsage(LocalDate.of(2026, 1, 2), 10), DayUsage(LocalDate.of(2026, 4, 1), 40))

        val buckets = historyBuckets(days, range, Locale.ENGLISH)

        assertEquals(listOf(10L, 0L, 0L, 40L), buckets.map { it.usageSeconds })
    }

    @Test fun `frequency maps Monday through Sunday to rows`() {
        val monday = LocalDate.of(2026, 1, 5)
        val range = DateRange(monday, monday.plusDays(6))
        val data = frequencyChartData(
            listOf(DayUsage(monday, 25), DayUsage(monday.plusDays(6), 100)),
            range,
        )

        assertEquals(1, data.weekCount)
        assertEquals(listOf(0, 6), data.cells.map { it.row })
        assertEquals(.5f, data.cells[0].intensity, .0001f)
        assertEquals(1f, data.cells[1].intensity, .0001f)
    }

    @Test fun `frequency limits old data and scales against visible maximum`() {
        val end = LocalDate.of(2026, 4, 12)
        val range = DateRange(end.minusWeeks(20), end)
        val visible = end.minusDays(1)
        val data = frequencyChartData(
            listOf(DayUsage(range.start, 10_000), DayUsage(visible, 100)),
            range,
            maximumWeeks = 14,
        )

        assertEquals(14, data.weekCount)
        assertEquals(1, data.cells.size)
        assertEquals(1f, data.cells.single().intensity, .0001f)
    }

    @Test fun `frequency rejects a non-positive week limit`() {
        val range = DateRange(date(1), date(1))

        val error = runCatching { frequencyChartData(emptyList(), range, maximumWeeks = 0) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun date(day: Int) = LocalDate.of(2026, 1, day)
}
