package org.screenconsume.app.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import org.screenconsume.app.domain.model.UsageInterval

data class UsageSnapshot(val intervals: List<UsageInterval>, val launches: Map<String, Int>)

interface UsageDataSource {
    fun hasUsageAccess(): Boolean
    fun read(beginMillis: Long, endMillis: Long): UsageSnapshot
}

class AndroidUsageDataSource(private val context: Context) : UsageDataSource {
    private val manager = context.getSystemService(UsageStatsManager::class.java)

    @Suppress("DEPRECATION")
    override fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun read(beginMillis: Long, endMillis: Long): UsageSnapshot {
        if (!hasUsageAccess()) return UsageSnapshot(emptyList(), emptyMap())
        val events = manager.queryEvents(beginMillis, endMillis)
        val event = UsageEvents.Event()
        val foregroundStarts = mutableMapOf<String, Long>()
        val intervals = mutableListOf<UsageInterval>()
        val launches = mutableMapOf<String, Int>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!foregroundStarts.containsKey(event.packageName)) {
                        foregroundStarts[event.packageName] = maxOf(event.timeStamp, beginMillis)
                        launches[event.packageName] = (launches[event.packageName] ?: 0) + 1
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = foregroundStarts.remove(event.packageName)
                    if (start != null && event.timeStamp > start) intervals += UsageInterval(event.packageName, start, minOf(event.timeStamp, endMillis))
                }
            }
        }
        foregroundStarts.forEach { (pkg, start) -> if (endMillis > start) intervals += UsageInterval(pkg, start, endMillis) }
        return UsageSnapshot(intervals, launches)
    }
}
