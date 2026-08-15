package org.screenconsume.app.workers

import android.content.Context
import androidx.work.*
import org.screenconsume.app.ScreenConsumeApplication
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DailyAggregationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val repository = (applicationContext as ScreenConsumeApplication).container.repository
        if (!repository.hasUsageAccess) Result.success() else {
            val today = LocalDate.now()
            // Re-aggregate recent days to recover from delayed or interrupted work.
            (0L..2L).forEach { repository.aggregate(today.minusDays(it)) }
            Result.success()
        }
    } catch (_: Exception) { Result.retry() }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyAggregationWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily-usage-aggregation", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

