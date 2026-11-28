package com.biteguard.core.domain.repository

import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.TelemetryEvent
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for auditing product ingredient labels, storing encrypted history,
 * and recording accuracy telemetry.
 *
 * Notice that image payloads are passed as platform-agnostic [ByteArray]s rather than
 * `android.graphics.Bitmap` so `:core:domain` stays 100% pure Kotlin JVM.
 */
interface AuditRepository {
    val scanHistoryFlow: Flow<List<AuditVerdict>>
    val telemetryEventsFlow: Flow<List<TelemetryEvent>>

    fun performLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        imageJpegBytes: ByteArray? = null,
        thumbnailBytes: ByteArray? = null,
        retryAttempt: Int = 0
    ): Flow<StreamState<AuditVerdict>>

    suspend fun evaluateOfflineOnly(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        thumbnailBytes: ByteArray? = null
    ): AuditVerdict

    suspend fun submitFeedback(verdict: AuditVerdict, feedback: UserFeedback)
    suspend fun clearScanHistory()
    suspend fun deleteVerdicts(ids: List<String>)
}
