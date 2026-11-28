package com.biteguard.core.domain.usecase

import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.PartialAuditVerdict
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.UserDietaryProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Orchestrates label input validation, exponential retry backoff, and streaming label audit.
 */
class AuditLabelUseCase(
    private val auditRepository: AuditRepository
) {
    operator fun invoke(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        imageJpegBytes: ByteArray? = null,
        thumbnailBytes: ByteArray? = null,
        retryAttempt: Int = 0
    ): Flow<StreamState<AuditVerdict>> = flow {
        val cleanText = rawExtractedText.trim()
        if (cleanText.isBlank()) {
            emit(
                StreamState.Error(
                    throwable = IllegalArgumentException("No text detected on label"),
                    cachedRawText = "",
                    errorKind = AuditErrorKind.NO_TEXT_DETECTED,
                    fallbackVerdict = null,
                    retryAttempt = retryAttempt
                )
            )
            return@flow
        }

        if (retryAttempt > 0) {
            val backoffMs = ((1L shl retryAttempt.coerceAtMost(3)) * 350L).coerceAtMost(2_500L)
            emit(
                StreamState.Streaming(
                    partialVerdict = PartialAuditVerdict(),
                    stageMessage = "Retrying Cloud AI (attempt #$retryAttempt)..."
                )
            )
            delay(backoffMs)
        }

        emitAll(
            auditRepository.performLabelAudit(
                rawExtractedText = cleanText,
                profile = profile,
                imageJpegBytes = imageJpegBytes,
                thumbnailBytes = thumbnailBytes,
                retryAttempt = retryAttempt
            )
        )
    }
}
