package org.screenconsume.app.ui

import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.screenconsume.app.domain.model.DashboardStats
import org.screenconsume.app.domain.model.DateRange

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardTransitionTest {
    @Test fun `rapid switches cancel old results and wait for all matching data`() = runTest {
        val today = LocalDate.of(2026, 1, 15)
        val day = DateRange.endingToday(1, today)
        val week = DateRange.endingToday(7, today)
        val month = DateRange(today.withDayOfMonth(1), today)
        val ranges = MutableStateFlow(RangePreset.TODAY to day)
        val results = mutableListOf<RangeDashboard>()
        val job = launch {
            observeRangeDashboard(
                ranges,
                dashboard = { range -> flow {
                    delay(if (range == week) 1_000 else 100)
                    emit(DashboardStats(totalSeconds = range.dayCount * 60, averageDailySeconds = 60))
                } },
                dailyAppUsage = { flow { delay(200); emit(emptyList()) } },
            ).collect { results += it }
        }
        runCurrent()
        assertTrue(results.last().loading)
        assertEquals(HeadlineStats(), results.last().headlines)
        advanceTimeBy(201)
        runCurrent()
        assertEquals(60L, results.last().stats.totalSeconds)
        assertEquals(60L, results.last().headlines.todaySeconds)
        assertEquals(60L, results.last().headlines.monthAverageSeconds)
        assertEquals(today, results.last().headlines.month)

        ranges.value = RangePreset.WEEK to week
        runCurrent()
        assertEquals(RangePreset.WEEK, results.last().preset)
        assertTrue(results.last().loading)
        advanceTimeBy(150)
        ranges.value = RangePreset.MONTH to month
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertTrue(results.last().loading) // Daily rows have not arrived yet.
        advanceTimeBy(1_000)
        runCurrent()
        val ready = results.filterNot { it.loading }
        assertEquals(listOf(RangePreset.TODAY, RangePreset.MONTH), ready.map { it.preset })
        assertEquals(month, ready.last().range)
        assertEquals(900L, ready.last().stats.totalSeconds)
        assertEquals(today, ready.last().headlines.month)
        job.cancel()
    }
}
