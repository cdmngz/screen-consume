package org.screenconsume.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.screenconsume.app.data.database.HistoryDeletionEntity
import org.screenconsume.app.data.database.ScreenConsumeDatabase
import org.screenconsume.app.data.preferences.AppPreferences
import org.screenconsume.app.data.usage.UsageDataSource
import org.screenconsume.app.data.usage.UsageSnapshot
import org.screenconsume.app.domain.model.UsageInterval
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HistoryRetentionTest {
    @Test fun missingAndShorterSnapshotsRetainHistoryAndRetriesDoNotDuplicate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ScreenConsumeDatabase::class.java).build()
        try {
            val source = SnapshotSource()
            val repository = UsageRepository(context, db, source, AppPreferences(context))
            val date = LocalDate.now().minusDays(1)
            repository.aggregate(date)
            source.seconds = 30
            repository.aggregate(date)
            source.seconds = 0
            repository.aggregate(date)
            assertEquals(60L, db.usageDao().portableRows(date.toString(), date.toString()).single().usageSeconds)
            source.seconds = 120
            repository.aggregate(date)
            repository.aggregate(date)
            val rows = db.usageDao().portableRows(date.toString(), date.toString())
            assertEquals(1, rows.size)
            assertEquals(120L, rows.single().usageSeconds)
        } finally {
            db.close()
        }
    }

    @Test fun deletionBlocksRecollectionAndTransientChartsButAllowsLaterDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ScreenConsumeDatabase::class.java).build()
        try {
            val source = SnapshotSource()
            val repository = UsageRepository(context, db, source, AppPreferences(context))
            val date = LocalDate.now().minusDays(1)
            repository.aggregate(date)
            repository.deleteAppHistory("test.missing")
            repository.aggregate(date)
            assertEquals(emptyList<Any>(), db.usageDao().portableRows(date.toString(), date.toString()))
            assertNull(db.usageDao().appId("test.missing"))
            assertEquals(LocalDate.now().toString(), db.usageDao().deletedThrough("test.missing"))
            assertNull(repository.appHourlyUsage("test.missing", date))
            assertEquals(false, repository.threeHourUsage(date)?.containsKey("test.missing") == true)
            // Move the boundary back to simulate collection on a later calendar day.
            db.usageDao().upsertDeletion(HistoryDeletionEntity("test.missing", date.minusDays(1).toString()))
            repository.aggregate(date)
            assertEquals(60L, db.usageDao().portableRows(date.toString(), date.toString()).single().usageSeconds)
        } finally {
            db.close()
        }
    }

    private class SnapshotSource : UsageDataSource {
        var seconds = 60L
        override fun hasUsageAccess() = true
        override fun read(beginMillis: Long, endMillis: Long) = UsageSnapshot(
            if (seconds == 0L) emptyList() else listOf(UsageInterval("test.missing", beginMillis, minOf(endMillis, beginMillis + seconds * 1000))),
            mapOf("test.missing" to 1),
        )
    }
}
