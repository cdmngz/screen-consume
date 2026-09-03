package org.screenconsume.app.data.usage

import org.screenconsume.app.domain.model.UsageInterval

data class UsageSnapshot(val intervals: List<UsageInterval>, val launches: Map<String, Int>)

/** Tracks foreground activities in memory only; overlapping activities count once per package. */
internal class UsageSessionTracker(private val beginMillis: Long, private val endMillis: Long) {
    private data class Session(val startMillis: Long, val activities: MutableSet<String?>)

    private val active = mutableMapOf<String, Session>()
    private val intervals = mutableListOf<UsageInterval>()
    private val launches = mutableMapOf<String, Int>()

    fun resume(packageName: String, className: String?, timestamp: Long) {
        val session = active[packageName]
        if (session == null) {
            active[packageName] = Session(maxOf(timestamp, beginMillis), mutableSetOf(className))
            launches[packageName] = (launches[packageName] ?: 0) + 1
        } else {
            session.activities.add(className)
        }
    }

    fun endActivity(packageName: String, className: String?, timestamp: Long) {
        val session = active[packageName] ?: return
        // A delayed stop for an already-paused activity must not end its replacement's session.
        if (!session.activities.remove(className) || session.activities.isNotEmpty()) return
        active.remove(packageName)
        interval(packageName, session.startMillis, timestamp)?.let(intervals::add)
    }

    fun snapshot(): UsageSnapshot = UsageSnapshot(
        intervals + active.mapNotNull { (pkg, session) -> interval(pkg, session.startMillis, endMillis) },
        launches.toMap(),
    )

    private fun interval(packageName: String, start: Long, end: Long): UsageInterval? {
        val clippedEnd = minOf(end, endMillis)
        return if (clippedEnd > start) UsageInterval(packageName, start, clippedEnd) else null
    }
}
