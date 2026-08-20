package org.screenconsume.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: AppEntity): Long

    @Query("SELECT id FROM apps WHERE packageName = :packageName")
    suspend fun appId(packageName: String): Long?

    @Upsert
    suspend fun upsertDaily(record: DailyAppUsageEntity)

    @Query("DELETE FROM daily_app_usage WHERE date = :date")
    suspend fun deleteDate(date: String)

    @Query("SELECT MIN(date) FROM daily_app_usage")
    fun observeEarliestDate(): Flow<String?>

    @Query("""
        SELECT d.date, a.packageName, a.displayName, a.category,
               d.usageSeconds, d.launchCount, d.morningUsageSeconds,
               d.afternoonUsageSeconds, d.eveningUsageSeconds, d.nightUsageSeconds
        FROM daily_app_usage d JOIN apps a ON a.id = d.appId
        WHERE d.date BETWEEN :start AND :end
        ORDER BY d.date, a.packageName
    """)
    suspend fun portableRows(start: String, end: String): List<PortableUsageRow>

    @Query("""
        SELECT d.date, a.packageName, a.displayName, a.category,
               d.usageSeconds, d.launchCount, d.morningUsageSeconds,
               d.afternoonUsageSeconds, d.eveningUsageSeconds, d.nightUsageSeconds
        FROM daily_app_usage d JOIN apps a ON a.id = d.appId
        WHERE d.date BETWEEN :start AND :end
        ORDER BY d.date, d.usageSeconds DESC
    """)
    fun observePortableRows(start: String, end: String): Flow<List<PortableUsageRow>>

    @Query("""
        SELECT a.packageName, a.displayName, a.category,
               SUM(d.usageSeconds) AS usageSeconds, SUM(d.launchCount) AS launchCount
        FROM daily_app_usage d JOIN apps a ON a.id = d.appId
        WHERE d.date BETWEEN :start AND :end
        GROUP BY a.id ORDER BY usageSeconds DESC
    """)
    fun observeApps(start: String, end: String): Flow<List<AppUsageRow>>

    @Query("""
        SELECT date, SUM(usageSeconds) AS usageSeconds FROM daily_app_usage
        WHERE date BETWEEN :start AND :end GROUP BY date ORDER BY date
    """)
    fun observeDays(start: String, end: String): Flow<List<DayUsageRow>>

    @Query("""
        SELECT d.date, d.usageSeconds FROM daily_app_usage d
        JOIN apps a ON a.id = d.appId
        WHERE a.packageName = :packageName AND d.date BETWEEN :start AND :end
        ORDER BY d.date
    """)
    fun observeAppDays(packageName: String, start: String, end: String): Flow<List<DayUsageRow>>
}
