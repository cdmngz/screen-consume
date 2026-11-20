package org.screenconsume.app.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.screenconsume.app.data.database.ScreenConsumeDatabase
import org.screenconsume.app.data.preferences.AppPreferences
import org.screenconsume.app.data.repository.UsageRepository
import org.screenconsume.app.data.usage.UsageDataSource
import org.screenconsume.app.data.usage.UsageSnapshot
import org.screenconsume.app.domain.model.DateRange
import org.screenconsume.app.domain.model.UsageInterval
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class MainViewModelRefreshTest {
    @Test fun refreshRecoversPreviousDaysWhenTodayHasNoEvents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val source = object : UsageDataSource {
            override fun hasUsageAccess() = true

            override fun read(beginMillis: Long, endMillis: Long): UsageSnapshot {
                if (beginMillis >= todayStart) return UsageSnapshot(emptyList(), emptyMap())
                val interval = UsageInterval("test.recovered", beginMillis, beginMillis + 60_000)
                return UsageSnapshot(listOf(interval), mapOf(interval.packageName to 1))
            }
        }

        val database = Room.inMemoryDatabaseBuilder(context, ScreenConsumeDatabase::class.java).build()
        try {
            val repository = UsageRepository(context, database, source, AppPreferences(context))
            val viewModel = withContext(Dispatchers.Main) { MainViewModel(repository) }
            try {
                withContext(Dispatchers.Main) { viewModel.refresh() }
                val rows = withTimeout(5_000) {
                    repository.dailyAppUsage(DateRange(today.minusDays(2), today)).first { it.size == 2 }
                }
                assertEquals(setOf(today.minusDays(2), today.minusDays(1)), rows.map { it.date }.toSet())
                assertEquals(listOf(60L, 60L), rows.map { it.usageSeconds })
            } finally {
                viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            }
        } finally {
            database.close()
        }
    }
}
