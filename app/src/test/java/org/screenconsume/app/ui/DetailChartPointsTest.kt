package org.screenconsume.app.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.screenconsume.app.domain.model.DayUsage

class DetailChartPointsTest {
    private val today = LocalDate.of(2026, 9, 2)
    private val now = today.atTime(14, 30)

    @Test fun `day sums three hour buckets with future values blank`() {
        val points = detailChartPoints(AppHistoryPreset.TODAY, appHistoryRange(today, AppHistoryPreset.TODAY, 0), emptyList(), List(24) { 60L }, now)
        assertEquals((0 until 24 step 3).toList(), points.map { it.hour })
        assertEquals(180L, points[4].seconds)
        assertEquals(null, points[5].seconds)
    }

    @Test fun `unavailable hourly history is not invented from daily totals`() {
        val points = detailChartPoints(AppHistoryPreset.TODAY, appHistoryRange(today, AppHistoryPreset.TODAY, 0), listOf(DayUsage(today, 7_200)), null, now)
        assertEquals(8, points.size)
        assertEquals(true, points.all { it.seconds == null })
    }

    @Test fun `week retains every date including future dates`() {
        val points = detailChartPoints(AppHistoryPreset.WEEK, appHistoryRange(today, AppHistoryPreset.WEEK, 0), listOf(DayUsage(today, 120)), null, now)
        assertEquals(7, points.size)
        assertEquals(120L, points.single { it.date == today }.seconds)
        assertEquals(null, points.last().seconds)
        assertEquals(0L, points.first().seconds)
    }

    @Test fun `year sums daily data into twelve calendar months`() {
        val days = listOf(DayUsage(today.minusDays(1), 120), DayUsage(today, 180), DayUsage(today.minusMonths(1), 60))
        val points = detailChartPoints(AppHistoryPreset.MONTH, appHistoryRange(today, AppHistoryPreset.MONTH, 0), days, null, now)
        assertEquals(12, points.size)
        assertEquals(60L, points[7].seconds)
        assertEquals(300L, points[8].seconds)
        assertEquals(null, points[9].seconds)
    }


    @Test fun `year view sums six calendar years`() {
        val days = listOf(DayUsage(LocalDate.of(2021, 1, 1), 60), DayUsage(today, 120))
        val points = detailChartPoints(AppHistoryPreset.YEAR, appHistoryRange(today, AppHistoryPreset.YEAR, 0), days, null, now)
        assertEquals((2021..2026).toList(), points.map { it.date.year })
        assertEquals(listOf(60L, 0L, 0L, 0L, 0L, 120L), points.map { it.seconds })
    }
    @Test fun `current three hour bucket excludes future hours`() {
        val points = detailChartPoints(AppHistoryPreset.TODAY, appHistoryRange(today, AppHistoryPreset.TODAY, 0), emptyList(), List(24) { 60L }, today.atTime(13, 30))
        assertEquals(120L, points[4].seconds)
        assertEquals(null, points[5].seconds)
    }

    @Test fun `past day groups all hours without losing usage`() {
        val yesterday = today.minusDays(1)
        val points = detailChartPoints(AppHistoryPreset.TODAY, org.screenconsume.app.domain.model.DateRange(yesterday, yesterday), emptyList(), List(24) { it.toLong() }, now)
        assertEquals(276L, points.sumOf { requireNotNull(it.seconds) })
        assertEquals(66L, points.last().seconds)
    }
}
