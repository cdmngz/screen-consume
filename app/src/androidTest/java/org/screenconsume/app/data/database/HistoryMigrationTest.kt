package org.screenconsume.app.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {
    @Test fun versionOneHistorySurvivesUpgradeAndDeletionBoundaryPersists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "history-migration-test.db"
        context.deleteDatabase(name)
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
                // DDL from the committed version 1 Room schema.
                old.execSQL("CREATE TABLE IF NOT EXISTS `apps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `displayName` TEXT NOT NULL, `category` TEXT)")
                old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_apps_packageName` ON `apps` (`packageName`)")
                old.execSQL("CREATE TABLE IF NOT EXISTS `daily_app_usage` (`date` TEXT NOT NULL, `appId` INTEGER NOT NULL, `usageSeconds` INTEGER NOT NULL, `launchCount` INTEGER NOT NULL, `morningUsageSeconds` INTEGER NOT NULL, `afternoonUsageSeconds` INTEGER NOT NULL, `eveningUsageSeconds` INTEGER NOT NULL, `nightUsageSeconds` INTEGER NOT NULL, PRIMARY KEY(`date`, `appId`), FOREIGN KEY(`appId`) REFERENCES `apps`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                old.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_app_usage_date` ON `daily_app_usage` (`date`)")
                old.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_app_usage_appId` ON `daily_app_usage` (`appId`)")
                old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_app_usage_date_appId` ON `daily_app_usage` (`date`, `appId`)")
                old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6c30ce14f51267ac576b83bb831b732c')")
                old.execSQL("INSERT INTO apps (id, packageName, displayName, category) VALUES (1, 'test.missing', 'Saved label', NULL)")
                old.execSQL("INSERT INTO daily_app_usage VALUES ('2026-01-01', 1, 60, 1, 60, 0, 0, 0)")
                old.version = 1
            }
            val migrated = Room.databaseBuilder(context, ScreenConsumeDatabase::class.java, name)
                .addMigrations(ScreenConsumeDatabase.MIGRATION_1_2).build()
            try {
                val row = migrated.usageDao().portableRows("2026-01-01", "2026-01-01").single()
                assertEquals("Saved label", row.displayName)
                assertEquals(60L, row.usageSeconds)
                migrated.usageDao().upsertDeletion(HistoryDeletionEntity("test.missing", "2026-01-02"))
            } finally {
                migrated.close()
            }
            val reopened = Room.databaseBuilder(context, ScreenConsumeDatabase::class.java, name).build()
            try {
                assertEquals("2026-01-02", reopened.usageDao().deletedThrough("test.missing"))
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
