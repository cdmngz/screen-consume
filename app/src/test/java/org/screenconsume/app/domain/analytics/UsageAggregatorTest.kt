package org.screenconsume.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import org.screenconsume.app.domain.model.UsageInterval
import java.time.LocalDate
import java.time.ZoneId

class UsageAggregatorTest {
    private val zone = ZoneId.of("UTC")
    private val aggregator = UsageAggregator(zone)
    private val date = LocalDate.of(2026, 1, 10)

    @Test fun `aggregates sessions and launch counts without retaining events`() {
        val start = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val result = aggregator.aggregate(date, listOf(UsageInterval("example.app", start, start + 90_000)), mapOf("example.app" to 2)).single()
        assertEquals(90, result.usageSeconds)
        assertEquals(90, result.morningUsageSeconds)
        assertEquals(2, result.launchCount)
    }

    @Test fun `splits a session across time-of-day boundaries`() {
        val start = date.atTime(11, 59, 30).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(12, 0, 30).atZone(zone).toInstant().toEpochMilli()
        val result = aggregator.aggregate(date, listOf(UsageInterval("example.app", start, end)), emptyMap()).single()
        assertEquals(30, result.morningUsageSeconds)
        assertEquals(30, result.afternoonUsageSeconds)
    }

    @Test fun `clips intervals to the requested calendar day`() {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val result = aggregator.aggregate(
            date,
            listOf(UsageInterval("example.app", dayStart - 30_000, dayEnd + 30_000)),
            emptyMap(),
        ).single()

        assertEquals(24 * 60 * 60L, result.usageSeconds)
        assertEquals(6 * 60 * 60L, result.morningUsageSeconds)
        assertEquals(6 * 60 * 60L, result.afternoonUsageSeconds)
        assertEquals(4 * 60 * 60L, result.eveningUsageSeconds)
        assertEquals(8 * 60 * 60L, result.nightUsageSeconds)
    }

    @Test fun `ignores intervals outside the requested day and zero length intervals`() {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val result = aggregator.aggregate(
            date,
            listOf(
                UsageInterval("before", dayStart - 60_000, dayStart - 30_000),
                UsageInterval("zero", dayStart, dayStart),
            ),
            mapOf("before" to 3, "zero" to 2),
        )

        assertEquals(emptyList<Any>(), result)
    }

    @Test fun `aggregates packages independently`() {
        val start = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        val result = aggregator.aggregate(
            date,
            listOf(
                UsageInterval("first", start, start + 10_000),
                UsageInterval("second", start, start + 20_000),
            ),
            mapOf("first" to 1, "second" to 2),
        ).associateBy { it.packageName }

        assertEquals(10, result.getValue("first").eveningUsageSeconds)
        assertEquals(20, result.getValue("second").eveningUsageSeconds)
        assertEquals(2, result.getValue("second").launchCount)
    }
}
