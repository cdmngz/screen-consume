package org.screenconsume.app.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.screenconsume.app.domain.model.DayUsage

class DetailChartPointsTest {
    private val today = LocalDate.of(2026, 9, 2)
    private val now = today.atTime(14, 30)

    @Test fun `day has twenty four hours with future values blank`() {
        val points = detailChartPoints(AppHistoryPreset.TODAY, appHistoryRange(today, AppHistoryPreset.TODAY, 0), emptyList(), List(24) { 60L }, now)
        assertEquals((0..23).toList(), points.map { it.hour })
        assertEquals(60L, points[14].seconds)
        assertEquals(null, points[15].seconds)
    }

    @Test fun `unavailable hourly history is not invented from daily totals`() {
        val points = detailChartPoints(AppHistoryPreset.TODAY, appHistoryRange(today, AppHistoryPreset.TODAY, 0), listOf(DayUsage(today, 7_200)), null, now)
        assertEquals(24, points.size)
        assertEquals(true, points.all { it.seconds == null })
    }

    @Test fun `week and month retain every date including future dates`() {
        for ((preset, size) in listOf(AppHistoryPreset.WEEK to 7, AppHistoryPreset.MONTH to 30)) {
            val points = detailChartPoints(preset, appHistoryRange(today, preset, 0), listOf(DayUsage(today, 120)), null, now)
            assertEquals(size, points.size)
            assertEquals(120L, points.single { it.date == today }.seconds)
            assertEquals(null, points.last().seconds)
            assertEquals(0L, points.first().seconds)
        }
    }

    @Test fun `year sums daily data into twelve calendar months`() {
        val days = listOf(DayUsage(today.minusDays(1), 120), DayUsage(today, 180), DayUsage(today.minusMonths(1), 60))
        val points = detailChartPoints(AppHistoryPreset.YEAR, appHistoryRange(today, AppHistoryPreset.YEAR, 0), days, null, now)
        assertEquals(12, points.size)
        assertEquals(60L, points[7].seconds)
        assertEquals(300L, points[8].seconds)
        assertEquals(null, points[9].seconds)
    }
}
