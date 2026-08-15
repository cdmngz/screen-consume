package org.screenconsume.app.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageDaoTest {
    private lateinit var db: ScreenConsumeDatabase
    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ScreenConsumeDatabase::class.java).build() }
    @After fun tearDown() = db.close()

    @Test fun upsertPreventsDuplicateDailyRecords() = runBlocking {
        val appId = db.usageDao().insertApp(AppEntity(packageName = "example.app", displayName = "Example", category = null))
        fun record(seconds: Long) = DailyAppUsageEntity("2026-01-01", appId, seconds, 1, seconds, 0, 0, 0)
        db.usageDao().upsertDaily(record(10))
        db.usageDao().upsertDaily(record(25))
        val rows = db.usageDao().observeApps("2026-01-01", "2026-01-01").first()
        assertEquals(1, rows.size)
        assertEquals(25, rows.single().usageSeconds)
    }
}

