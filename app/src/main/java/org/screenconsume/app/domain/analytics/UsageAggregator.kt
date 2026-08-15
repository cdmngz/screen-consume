package org.screenconsume.app.domain.analytics

import org.screenconsume.app.domain.model.DailyAggregate
import org.screenconsume.app.domain.model.UsageInterval
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Pure aggregation: no event or timestamp is retained after this result is persisted. */
class UsageAggregator(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    fun aggregate(date: LocalDate, intervals: List<UsageInterval>, launches: Map<String, Int>): List<DailyAggregate> {
        val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return intervals.groupBy { it.packageName }.mapNotNull { (packageName, sessions) ->
            var morning = 0L; var afternoon = 0L; var evening = 0L; var night = 0L
            sessions.forEach { session ->
                var cursor = maxOf(session.startMillis, dayStart)
                val end = minOf(session.endMillis, dayEnd)
                while (cursor < end) {
                    val time = Instant.ofEpochMilli(cursor).atZone(zoneId)
                    val nextBoundary = when {
                        time.toLocalTime() < LocalTime.of(6, 0) -> time.toLocalDate().atTime(6, 0).atZone(zoneId)
                        time.toLocalTime() < LocalTime.NOON -> time.toLocalDate().atTime(12, 0).atZone(zoneId)
                        time.toLocalTime() < LocalTime.of(18, 0) -> time.toLocalDate().atTime(18, 0).atZone(zoneId)
                        time.toLocalTime() < LocalTime.of(22, 0) -> time.toLocalDate().atTime(22, 0).atZone(zoneId)
                        else -> time.toLocalDate().plusDays(1).atStartOfDay(zoneId)
                    }.toInstant().toEpochMilli()
                    val seconds = (minOf(end, nextBoundary) - cursor) / 1000
                    when (time.hour) {
                        in 6..11 -> morning += seconds
                        in 12..17 -> afternoon += seconds
                        in 18..21 -> evening += seconds
                        else -> night += seconds
                    }
                    cursor = minOf(end, nextBoundary)
                }
            }
            val total = morning + afternoon + evening + night
            if (total == 0L) null else DailyAggregate(date, packageName, total, launches[packageName] ?: 0, morning, afternoon, evening, night)
        }
    }
}

