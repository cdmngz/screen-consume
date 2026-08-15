package org.screenconsume.app

import android.app.Application
import org.screenconsume.app.workers.DailyAggregationWorker

class ScreenConsumeApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        DailyAggregationWorker.schedule(this)
    }
}

