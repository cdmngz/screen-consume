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
}

