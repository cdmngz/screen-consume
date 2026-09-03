package org.screenconsume.app.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

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
        val sessions = UsageSessionTracker(beginMillis, endMillis)
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    sessions.resume(event.packageName, event.className, event.timeStamp)
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    sessions.endActivity(event.packageName, event.className, event.timeStamp)
                }
            }
        }
        return sessions.snapshot()
    }
}
