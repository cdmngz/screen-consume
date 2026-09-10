package org.screenconsume.app.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.screenconsume.app.domain.model.DailyAppUsage

class DashboardChartDataTest {
    private fun row(packageName: String, seconds: Long) =
        DailyAppUsage(LocalDate.of(2026, 9, 1), packageName, "Same label", seconds, seconds, 0, 0, 0)

    @Test fun `apps with identical labels remain distinct`() {
        val rows = listOf(row("one", 60), row("two", 30))
        val segments = rankedSegments(rows.map { it to it.usageSeconds }, "Other", null)
        assertEquals(listOf("one", "two"), segments.map { it.packageName })
        assertEquals(90L, segments.sumOf { it.seconds })
    }
    @Test fun `highlighting a small app keeps its segment visible without changing totals`() {
        val rows = (1..5).map { row("app$it", it * 60L) }
        val segments = rankedSegments(rows.map { it to it.usageSeconds }, "Other", "app1")
        assertEquals("app1", segments.first().packageName)
        assertEquals(60L, segments.first().seconds)
        assertEquals(rows.sumOf { it.usageSeconds }, segments.sumOf { it.seconds })
        assertEquals(4, segments.size)
    }
    @Test fun `absent highlighted app does not invent usage`() {
        val rows = listOf(row("one", 60), row("two", 0))
        val segments = rankedSegments(rows.map { it to it.usageSeconds }, "Other", "two")
        assertTrue(segments.none { it.packageName == "two" })
        assertEquals(60L, segments.sumOf { it.seconds })
    }
}
