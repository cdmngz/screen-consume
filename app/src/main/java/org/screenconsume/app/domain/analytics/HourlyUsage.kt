package org.screenconsume.app.domain.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.screenconsume.app.domain.model.UsageInterval

/** Local clock-hour buckets; repeated daylight-saving hours share a bucket. */
internal fun hourlyUsageSeconds(date: LocalDate, intervals: List<UsageInterval>, zone: ZoneId): List<Long> {
    val milliseconds = LongArray(24)
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    intervals.forEach { interval ->
        var cursor = maxOf(interval.startMillis, dayStart)
        val end = minOf(interval.endMillis, dayEnd)
        while (cursor < end) {
            val local = Instant.ofEpochMilli(cursor).atZone(zone)
            val next = minOf(end, local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli())
            milliseconds[local.hour] += next - cursor
            cursor = next
        }
    }
    return milliseconds.map { it / 1_000 }
}

/** Eight local three-hour buckets per package; usage remains transient and is not persisted. */
internal fun threeHourUsageByPackage(
    date: LocalDate,
    intervals: List<UsageInterval>,
    zone: ZoneId,
): Map<String, List<Long>> = intervals.groupBy(UsageInterval::packageName).mapValues { (_, appIntervals) ->
    hourlyUsageSeconds(date, appIntervals, zone).chunked(3).map(List<Long>::sum)
}
