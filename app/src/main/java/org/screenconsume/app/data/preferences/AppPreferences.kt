package org.screenconsume.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("preferences")

class AppPreferences(private val context: Context) {
    private val onboardingSeen = booleanPreferencesKey("onboarding_seen")
    val hasSeenOnboarding = context.dataStore.data.map { it[onboardingSeen] ?: false }
    suspend fun markOnboardingSeen() { context.dataStore.edit { it[onboardingSeen] = true } }
}

