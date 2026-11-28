package com.biteguard.core.domain.usecase

import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.SafetyLevel

/**
 * Filters saved scan history by optional safety level and free-text search query.
 */
class FilterScanHistoryUseCase {
    operator fun invoke(
        history: List<AuditVerdict>,
        query: String,
        safetyFilter: SafetyLevel?
    ): List<AuditVerdict> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() && safetyFilter == null) {
            return history
        }

        return history.filter { verdict ->
            val matchesLevel = safetyFilter == null || verdict.safetyLevel == safetyFilter
            val matchesQuery = trimmedQuery.isBlank() ||
                verdict.productName.contains(trimmedQuery, ignoreCase = true) ||
                verdict.summary.contains(trimmedQuery, ignoreCase = true) ||
                verdict.rawExtractedText.contains(trimmedQuery, ignoreCase = true) ||
                verdict.flaggedIngredients.any {
                    it.name.contains(trimmedQuery, ignoreCase = true) ||
                        it.riskReason.contains(trimmedQuery, ignoreCase = true)
                }
            matchesLevel && matchesQuery
        }
    }
}
