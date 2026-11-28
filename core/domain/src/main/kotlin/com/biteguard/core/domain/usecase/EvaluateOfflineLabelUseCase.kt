package com.biteguard.core.domain.usecase

import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.UserDietaryProfile

/**
 * Evaluates an ingredient list strictly on-device without calling cloud AI,
 * persisting the verdict to the audit log.
 */
class EvaluateOfflineLabelUseCase(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        thumbnailBytes: ByteArray? = null
    ): AuditVerdict = auditRepository.evaluateOfflineOnly(
        rawExtractedText = rawExtractedText,
        profile = profile,
        thumbnailBytes = thumbnailBytes
    )
}
