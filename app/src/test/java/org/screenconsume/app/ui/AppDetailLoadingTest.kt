package org.screenconsume.app.ui

import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.screenconsume.app.domain.model.AppUsage
import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.DayUsage

@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailLoadingTest {
    private val date = LocalDate.of(2026, 9, 30)
    private val range = DateRange(date, date)
    private val app = AppUsage("test.app", "Test", null, 60, 1)

    @Test fun `detail appears immediately and waits for calendar and hourly data`() = runTest {
        val results = mutableListOf<AppDetailUiState>()
        val job = launch {
            observeAppDetail(app, AppHistoryPreset.TODAY, range, false,
                flowOf(listOf(DayUsage(date, 60))),
                flow { delay(100); emit(listOf(DayUsage(date, 120))) },
            ) { delay(200); List(24) { 5L } }.collect { results += it }
        }
        runCurrent()
        assertEquals(app, results.single().app)
        assertTrue(results.single().loading)
        assertTrue(results.single().days.isEmpty())
        assertNull(results.single().hourlySeconds)
        advanceTimeBy(101)
        runCurrent()
        assertEquals(1, results.size)
        advanceTimeBy(100)
        runCurrent()
        assertFalse(results.last().loading)
        assertEquals(60L, results.last().days.single().usageSeconds)
        assertEquals(120L, results.last().calendarDays.single().usageSeconds)
        assertEquals(List(24) { 5L }, results.last().hourlySeconds)
        job.cancel()
    }

    @Test fun `empty history finishes loading and weekly views skip hourly query`() = runTest {
        val results = mutableListOf<AppDetailUiState>()
        val job = launch {
            observeAppDetail(app, AppHistoryPreset.WEEK, range, true, flowOf(emptyList()), flowOf(emptyList())) {
                error("Hourly usage must not be queried for a week")
            }.collect { results += it }
        }
        runCurrent()
        assertTrue(results.first().loading)
        assertFalse(results.last().loading)
        assertTrue(results.last().days.isEmpty())
        assertTrue(results.last().canMoveToNewerPeriod)
        assertNull(results.last().hourlySeconds)
        job.cancel()
    }

    @Test fun `switching apps cancels delayed history and never displays old app results`() = runTest {
        val other = app.copy(packageName = "other.app")
        val selection = MutableStateFlow(app)
        val results = mutableListOf<AppDetailUiState>()
        val job = launch {
            selection.flatMapLatest { selected ->
                observeAppDetail(selected, AppHistoryPreset.WEEK, range, false,
                    flow { delay(if (selected == app) 1_000 else 100); emit(listOf(DayUsage(date, 60))) },
                    flowOf(emptyList()),
                ) { null }
            }.collect { results += it }
        }
        runCurrent()
        selection.value = other
        runCurrent()
        assertEquals(other, results.last().app)
        assertTrue(results.last().loading)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(other), results.filterNot { it.loading }.map { it.app })
        job.cancel()
    }

    @Test fun `completed empty hourly query is unavailable rather than still loading`() = runTest {
        val results = mutableListOf<AppDetailUiState>()
        val job = launch {
            observeAppDetail(app, AppHistoryPreset.TODAY, range, false,
                flowOf(listOf(DayUsage(date, 60))), flowOf(emptyList()),
            ) { null }.collect { results += it }
        }
        runCurrent()
        assertFalse(results.last().loading)
        assertNull(results.last().hourlySeconds)
        assertEquals(60L, results.last().days.single().usageSeconds)
        job.cancel()
    }

    @Test fun `history revision reloads hourly detail after collection`() = runTest {
        val revision = MutableStateFlow(0)
        var queries = 0
        val results = mutableListOf<AppDetailUiState>()
        val job = launch {
            revision.flatMapLatest { value ->
                observeAppDetail(app, AppHistoryPreset.TODAY, range, false,
                    flowOf(listOf(DayUsage(date, 60))), flowOf(emptyList()),
                ) { queries++; List(24) { value.toLong() } }
            }.collect { results += it }
        }
        runCurrent()
        assertEquals(List(24) { 0L }, results.last().hourlySeconds)
        revision.value = 1
        runCurrent()
        assertEquals(2, queries)
        assertEquals(List(24) { 1L }, results.last().hourlySeconds)
        assertEquals(2, results.count { it.loading })
        job.cancel()
    }

}
