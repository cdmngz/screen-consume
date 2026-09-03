package org.screenconsume.app.domain.analytics

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.screenconsume.app.domain.model.UsageInterval

class HourlyUsageTest {
    @Test fun `splits usage between hours and clips to the day`() {
        val date = LocalDate.of(2026, 9, 2)
        val zone = ZoneId.of("UTC")
        val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val result = hourlyUsageSeconds(date, listOf(
            UsageInterval("app", midnight - 30_000, midnight + 30_000),
            UsageInterval("app", midnight + 3_570_000, midnight + 3_690_000),
        ), zone)
        assertEquals(24, result.size)
        assertEquals(60L, result[0])
        assertEquals(90L, result[1])
        assertEquals(150L, result.sum())
    }

    @Test fun `daylight saving days preserve elapsed time in twenty four clock buckets`() {
        val zone = ZoneId.of("Europe/Madrid")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 29) to 23, LocalDate.of(2026, 10, 25) to 25)) {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val result = hourlyUsageSeconds(date, listOf(UsageInterval("app", start, end)), zone)
            assertEquals(24, result.size)
            assertEquals(hours * 3_600L, result.sum())
            assertEquals(if (hours == 23) 0L else 7_200L, result[2])
        }
    }
}
