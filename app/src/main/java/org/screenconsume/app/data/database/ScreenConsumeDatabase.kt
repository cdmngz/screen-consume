package org.screenconsume.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppEntity::class, DailyAppUsageEntity::class, HistoryDeletionEntity::class], version = 2, exportSchema = true)
abstract class ScreenConsumeDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `history_deletions` (`packageName` TEXT NOT NULL, `throughDate` TEXT NOT NULL, PRIMARY KEY(`packageName`))")
            }
        }
    }

    abstract fun usageDao(): UsageDao
}
