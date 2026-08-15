package org.screenconsume.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.screenconsume.app.data.database.*
import org.screenconsume.app.data.usage.UsageDataSource
import org.screenconsume.app.domain.analytics.AnalyticsCalculator
import org.screenconsume.app.domain.analytics.UsageAggregator
import org.screenconsume.app.domain.model.*
import java.time.LocalDate
import java.time.ZoneId

class UsageRepository(
    private val context: Context,
    private val database: ScreenConsumeDatabase,
    private val source: UsageDataSource,
    private val aggregator: UsageAggregator = UsageAggregator(),
) {
    val hasUsageAccess: Boolean get() = source.hasUsageAccess()

    suspend fun aggregate(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start || !source.hasUsageAccess()) return
        val snapshot = source.read(start, end)
        val records = aggregator.aggregate(date, snapshot.intervals, snapshot.launches)
        database.withTransaction {
            val dao = database.usageDao()
            // Replace the complete day snapshot; retries therefore converge to one record per app/day.
            dao.deleteDate(date.toString())
            records.forEach { record ->
                var id = dao.appId(record.packageName)
                if (id == null) {
                    val info = resolveApp(record.packageName)
                    dao.insertApp(AppEntity(packageName = record.packageName, displayName = info.first, category = info.second))
                    id = dao.appId(record.packageName)
                }
                id?.let { dao.upsertDaily(record.toEntity(it)) }
            }
        }
    }

    fun dashboard(range: DateRange): Flow<DashboardStats> {
        val previous = range.previous()
        return combine(
            database.usageDao().observeApps(range.start.toString(), range.endInclusive.toString()),
            database.usageDao().observeApps(previous.start.toString(), previous.endInclusive.toString()),
            database.usageDao().observeDays(range.start.toString(), range.endInclusive.toString()),
        ) { current, prior, days ->
            AnalyticsCalculator.calculate(range, current.map { it.model() }, prior.map { it.model() }, days.map { DayUsage(LocalDate.parse(it.date), it.usageSeconds) })
        }
    }

    private fun resolveApp(packageName: String): Pair<String, String?> = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val name = context.packageManager.getApplicationLabel(info).toString()
        val category = if (android.os.Build.VERSION.SDK_INT >= 26) ApplicationInfo.getCategoryTitle(context, info.category)?.toString() else null
        name to category
    } catch (_: Exception) { packageName to null }
}

private fun DailyAggregate.toEntity(appId: Long) = DailyAppUsageEntity(date.toString(), appId, usageSeconds, launchCount, morningUsageSeconds, afternoonUsageSeconds, eveningUsageSeconds, nightUsageSeconds)
private fun AppUsageRow.model() = AppUsage(packageName, displayName, category, usageSeconds, launchCount)

