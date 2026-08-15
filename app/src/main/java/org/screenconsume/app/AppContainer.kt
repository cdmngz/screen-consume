package org.screenconsume.app

import android.content.Context
import androidx.room.Room
import org.screenconsume.app.data.database.ScreenConsumeDatabase
import org.screenconsume.app.data.repository.UsageRepository
import org.screenconsume.app.data.usage.AndroidUsageDataSource

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, ScreenConsumeDatabase::class.java, "screen-consume.db").build()
    val repository = UsageRepository(appContext, database, AndroidUsageDataSource(appContext))
}

