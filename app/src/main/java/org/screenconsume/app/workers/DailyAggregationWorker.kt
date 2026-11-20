package org.screenconsume.app.workers

import android.content.Context
import androidx.work.*
import org.screenconsume.app.ScreenConsumeApplication
import java.util.concurrent.TimeUnit

class DailyAggregationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val repository = (applicationContext as ScreenConsumeApplication).container.repository
        if (repository.hasUsageAccess) repository.aggregateRecentDays()
        Result.success()
    } catch (_: Exception) { Result.retry() }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyAggregationWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily-usage-aggregation", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

