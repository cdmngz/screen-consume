package org.screenconsume.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test
import org.screenconsume.app.domain.analytics.UsageAggregator
import org.screenconsume.app.domain.model.UsageInterval
import java.time.LocalDate
import java.time.ZoneId

class UsageSessionTrackerTest {
    private val pkg = "com.duolingo"

    @Test fun `delayed stop from previous screen does not hide a long lesson`() {
        val date = LocalDate.of(2026, 9, 3)
        val start = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val tracker = UsageSessionTracker(start, start + 900_000)
        tracker.resume(pkg, "Home", start)
        tracker.endActivity(pkg, "Home", start + 1_000) // pause
        tracker.resume(pkg, "Lesson", start + 1_000)
        tracker.endActivity(pkg, "Home", start + 1_100) // delayed stop
        tracker.endActivity(pkg, "Lesson", start + 601_000)

        val snapshot = tracker.snapshot()
        val result = UsageAggregator(ZoneId.of("UTC"))
            .aggregate(date, snapshot.intervals, snapshot.launches).single()
        assertEquals(601L, result.usageSeconds)
    }

    @Test fun `overlapping activities count once and end independently`() {
        val tracker = UsageSessionTracker(0, 1_000)
        tracker.resume(pkg, "Home", 10)
        tracker.resume(pkg, "Lesson", 20)
        tracker.endActivity(pkg, "Home", 30)
        tracker.endActivity(pkg, "Home", 40)
        tracker.endActivity(pkg, "Lesson", 100)
        assertEquals(listOf(UsageInterval(pkg, 10, 100)), tracker.snapshot().intervals)
        assertEquals(mapOf(pkg to 1), tracker.snapshot().launches)
    }

    @Test fun `stop without pause closes its own activity`() {
        val tracker = UsageSessionTracker(0, 1_000)
        tracker.resume(pkg, "Lesson", 10)
        tracker.endActivity(pkg, "Lesson", 100)
        assertEquals(listOf(UsageInterval(pkg, 10, 100)), tracker.snapshot().intervals)
    }

    @Test fun `open sessions clip to query boundaries and snapshots are repeatable`() {
        val tracker = UsageSessionTracker(10, 100)
        tracker.resume(pkg, null, 0)
        tracker.resume(pkg, null, 20)
        val expected = UsageSnapshot(listOf(UsageInterval(pkg, 10, 100)), mapOf(pkg to 1))
        assertEquals(expected, tracker.snapshot())
        assertEquals(expected, tracker.snapshot())
    }

    @Test fun `background events from another package do not close a session`() {
        val tracker = UsageSessionTracker(0, 100)
        tracker.resume(pkg, "Lesson", 10)
        tracker.endActivity("another.app", "Lesson", 20)
        assertEquals(listOf(UsageInterval(pkg, 10, 100)), tracker.snapshot().intervals)
    }
}
