package org.screenconsume.app.data.repository

import android.content.Context
import android.net.Uri
import android.content.pm.ApplicationInfo
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.screenconsume.app.data.database.*
import org.screenconsume.app.data.export.DataPortability
import org.screenconsume.app.data.preferences.AppPreferences
import org.screenconsume.app.data.usage.UsageDataSource
import org.screenconsume.app.domain.analytics.AnalyticsCalculator
import org.screenconsume.app.domain.analytics.UsageAggregator
import org.screenconsume.app.domain.model.*
import java.time.LocalDate
import java.time.ZoneId
import java.io.ByteArrayOutputStream

class UsageRepository(
    private val context: Context,
    private val database: ScreenConsumeDatabase,
    private val source: UsageDataSource,
    private val preferences: AppPreferences,
    private val aggregator: UsageAggregator = UsageAggregator(),
) {
    companion object {
        const val MAX_RESTORE_FILE_BYTES = 25 * 1024 * 1024
    }
    val hasUsageAccess: Boolean get() = source.hasUsageAccess()
    val lastSuccessfulAggregationMillis = preferences.lastSuccessfulAggregationMillis
    val earliestDate: Flow<LocalDate?> = database.usageDao().observeEarliestDate().map { it?.let(LocalDate::parse) }

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
        preferences.markAggregationSuccessful()
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

    fun appHistory(packageName: String, range: DateRange): Flow<List<DayUsage>> =
        database.usageDao().observeAppDays(packageName, range.start.toString(), range.endInclusive.toString())
            .map { rows -> rows.map { DayUsage(LocalDate.parse(it.date), it.usageSeconds) } }

    fun dailyAppUsage(range: DateRange): Flow<List<DailyAppUsage>> =
        database.usageDao().observePortableRows(range.start.toString(), range.endInclusive.toString()).map { rows ->
            rows.map {
                DailyAppUsage(
                    LocalDate.parse(it.date), it.packageName, it.displayName, it.usageSeconds,
                    it.morningUsageSeconds, it.afternoonUsageSeconds, it.eveningUsageSeconds, it.nightUsageSeconds,
                )
            }
        }

    suspend fun exportCsv(uri: Uri, range: DateRange): Int = export(uri, range, encryptedPassword = null, csv = true)
    suspend fun exportJson(uri: Uri, range: DateRange): Int = export(uri, range, encryptedPassword = null, csv = false)
    suspend fun exportAllCsv(uri: Uri): Int = export(uri, fullRange(), encryptedPassword = null, csv = true)
    suspend fun exportAllJson(uri: Uri): Int = export(uri, fullRange(), encryptedPassword = null, csv = false)
    suspend fun exportEncryptedBackup(uri: Uri, password: CharArray): Int = try {
        export(uri, fullRange(), encryptedPassword = password, csv = false)
    } finally {
        password.fill('\u0000')
    }

    suspend fun restore(uri: Uri, password: CharArray?): Int = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_RESTORE_FILE_BYTES) { "Backup exceeds the 25 MB file limit" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("Could not open selected file")
        val json = if (DataPortability.isEncrypted(bytes)) {
            require(password != null && password.isNotEmpty()) { "This backup requires its password" }
            DataPortability.decrypt(bytes, requireNotNull(password))
        } else bytes
        val rows = DataPortability.fromJson(json)
        database.withTransaction {
            val dao = database.usageDao()
            rows.forEach { row ->
                val segments = listOf(row.morningUsageSeconds, row.afternoonUsageSeconds, row.eveningUsageSeconds, row.nightUsageSeconds)
                require(row.usageSeconds >= 0 && row.launchCount >= 0 && segments.all { it >= 0 } && segments.sum() == row.usageSeconds) { "Backup contains invalid usage values" }
                LocalDate.parse(row.date)
                dao.insertApp(AppEntity(packageName = row.packageName, displayName = row.displayName, category = row.category))
                val appId = requireNotNull(dao.appId(row.packageName))
                dao.upsertDaily(DailyAppUsageEntity(row.date, appId, row.usageSeconds, row.launchCount, row.morningUsageSeconds, row.afternoonUsageSeconds, row.eveningUsageSeconds, row.nightUsageSeconds))
            }
        }
        rows.size
    } finally {
        password?.fill('\u0000')
    }

    private suspend fun export(uri: Uri, range: DateRange, encryptedPassword: CharArray?, csv: Boolean): Int {
        val rows = database.usageDao().portableRows(range.start.toString(), range.endInclusive.toString())
        val plain = if (csv) DataPortability.toCsv(rows) else DataPortability.toJson(rows)
        val bytes = encryptedPassword?.let { DataPortability.encrypt(plain, it) } ?: plain
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Could not write selected file")
        return rows.size
    }

    private suspend fun fullRange(): DateRange {
        val first = database.usageDao().portableRows("0000-01-01", "9999-12-31").firstOrNull()?.date?.let(LocalDate::parse) ?: LocalDate.now()
        return DateRange(first, LocalDate.now())
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
