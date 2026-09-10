package org.screenconsume.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.screenconsume.app.data.database.AppEntity
import org.screenconsume.app.data.database.DailyAppUsageEntity
import org.screenconsume.app.data.database.ScreenConsumeDatabase
import org.screenconsume.app.data.export.DataPortability
import org.screenconsume.app.data.preferences.AppPreferences
import org.screenconsume.app.data.usage.AndroidUsageDataSource
import org.screenconsume.app.domain.model.DateRange
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ExportRangeTest {
    @Test fun selectedDatesAreInclusiveAndDoNotExportAdjacentDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ScreenConsumeDatabase::class.java).build()
        val json = File.createTempFile("range-test", ".json", context.cacheDir)
        val csv = File.createTempFile("range-test", ".csv", context.cacheDir)
        try {
            val dao = db.usageDao()
            val id = dao.insertApp(AppEntity(packageName = "test.example", displayName = "Example", category = null))
            (1..4).forEach { day ->
                dao.upsertDaily(DailyAppUsageEntity("2026-01-0$day", id, 60, 1, 60, 0, 0, 0))
            }
            val repository = UsageRepository(context, db, AndroidUsageDataSource(context), AppPreferences(context))
            val range = DateRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3))
            assertEquals(2, repository.exportJson(Uri.fromFile(json), range))
            assertEquals(listOf("2026-01-02", "2026-01-03"), DataPortability.fromJson(json.readBytes()).map { it.date })
            assertEquals(2, repository.exportCsv(Uri.fromFile(csv), range))
            assertEquals(listOf("2026-01-02", "2026-01-03"), csv.readLines().drop(1).map { it.substringBefore(',') })
            assertEquals(4, repository.exportAllJson(Uri.fromFile(json)))
            assertEquals(4, DataPortability.fromJson(json.readBytes()).size)
        } finally {
            db.close()
            json.delete()
            csv.delete()
        }
    }
}
