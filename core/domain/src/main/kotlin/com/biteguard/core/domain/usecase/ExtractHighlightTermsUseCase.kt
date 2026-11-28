package com.biteguard.core.domain.usecase

import com.biteguard.core.domain.engine.OfflineAuditEngine
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.UserDietaryProfile

/**
 * Extracts matched ingredient & allergen keywords for highlighting raw OCR text in the UI.
 */
class ExtractHighlightTermsUseCase(
    private val offlineAuditEngine: OfflineAuditEngine
) {
    operator fun invoke(
        verdict: AuditVerdict,
        profile: UserDietaryProfile
    ): List<String> = offlineAuditEngine.extractHighlightTerms(verdict, profile)
}
