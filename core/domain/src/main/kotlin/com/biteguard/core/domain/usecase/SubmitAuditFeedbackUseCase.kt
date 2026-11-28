package com.biteguard.core.domain.usecase

import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.UserFeedback

/**
 * Records user verdict accuracy feedback and queues a telemetry event (FR-06).
 */
class SubmitAuditFeedbackUseCase(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(verdict: AuditVerdict, feedback: UserFeedback) {
        auditRepository.submitFeedback(verdict, feedback)
    }
}
