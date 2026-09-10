package org.screenconsume.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("preferences")

data class DashboardPreferences(val preset: String = "TODAY", val sortByName: Boolean = false, val includeBrief: Boolean = false)

class AppPreferences(private val context: Context) {
    private val dashboardPreset = stringPreferencesKey("dashboard_preset")
    private val dashboardSort = booleanPreferencesKey("dashboard_sort_by_name")
    private val dashboardBrief = booleanPreferencesKey("dashboard_include_brief")
    val dashboard = context.dataStore.data.map {
        DashboardPreferences(it[dashboardPreset] ?: "TODAY", it[dashboardSort] ?: false, it[dashboardBrief] ?: false)
    }
    suspend fun setDashboardPreset(value: String) { context.dataStore.edit { it[dashboardPreset] = value } }
    suspend fun setDashboardSort(value: Boolean) { context.dataStore.edit { it[dashboardSort] = value } }
    suspend fun setDashboardBrief(value: Boolean) { context.dataStore.edit { it[dashboardBrief] = value } }
    private val onboardingSeen = booleanPreferencesKey("onboarding_seen")
    private val lastSuccessfulAggregation = longPreferencesKey("last_successful_aggregation")
    val hasSeenOnboarding = context.dataStore.data.map { it[onboardingSeen] ?: false }
    val lastSuccessfulAggregationMillis = context.dataStore.data.map { it[lastSuccessfulAggregation] }
    suspend fun markOnboardingSeen() { context.dataStore.edit { it[onboardingSeen] = true } }
    suspend fun markAggregationSuccessful(atMillis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[lastSuccessfulAggregation] = atMillis }
    }
}
