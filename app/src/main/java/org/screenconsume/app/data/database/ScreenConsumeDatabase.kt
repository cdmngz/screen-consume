package org.screenconsume.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AppEntity::class, DailyAppUsageEntity::class], version = 1, exportSchema = true)
abstract class ScreenConsumeDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
}

