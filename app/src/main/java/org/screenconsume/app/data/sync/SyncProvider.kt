package org.screenconsume.app.data.sync

import java.time.LocalDate

/** Extension point for explicit, user-authorized integrations. No provider ships in the MVP. */
interface SyncProvider {
    val id: String
    suspend fun sync(start: LocalDate, endInclusive: LocalDate): SyncResult
    suspend fun disconnect()
}

sealed interface SyncResult {
    data class Success(val recordsSynced: Int) : SyncResult
    data class Failure(val retryable: Boolean, val message: String) : SyncResult
}

