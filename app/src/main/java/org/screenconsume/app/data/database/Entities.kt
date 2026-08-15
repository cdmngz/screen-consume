package org.screenconsume.app.data.database

import androidx.room.*

@Entity(tableName = "apps", indices = [Index(value = ["packageName"], unique = true)])
data class AppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val displayName: String,
    val category: String?,
)

@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["date", "appId"],
    foreignKeys = [ForeignKey(entity = AppEntity::class, parentColumns = ["id"], childColumns = ["appId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date"), Index("appId"), Index(value = ["date", "appId"], unique = true)],
)
data class DailyAppUsageEntity(
    val date: String,
    val appId: Long,
    val usageSeconds: Long,
    val launchCount: Int,
    val morningUsageSeconds: Long,
    val afternoonUsageSeconds: Long,
    val eveningUsageSeconds: Long,
    val nightUsageSeconds: Long,
)

data class AppUsageRow(
    val packageName: String,
    val displayName: String,
    val category: String?,
    val usageSeconds: Long,
    val launchCount: Int,
)

data class DayUsageRow(val date: String, val usageSeconds: Long)

