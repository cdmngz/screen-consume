package org.screenconsume.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("preferences")

class AppPreferences(private val context: Context) {
    private val onboardingSeen = booleanPreferencesKey("onboarding_seen")
    private val lastSuccessfulAggregation = longPreferencesKey("last_successful_aggregation")
    val hasSeenOnboarding = context.dataStore.data.map { it[onboardingSeen] ?: false }
    val lastSuccessfulAggregationMillis = context.dataStore.data.map { it[lastSuccessfulAggregation] }
    suspend fun markOnboardingSeen() { context.dataStore.edit { it[onboardingSeen] = true } }
    suspend fun markAggregationSuccessful(atMillis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[lastSuccessfulAggregation] = atMillis }
    }
}
