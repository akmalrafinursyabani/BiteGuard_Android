package com.biteguard.core.domain

import app.cash.turbine.test
import com.biteguard.core.domain.engine.RuleBasedOfflineAuditEngine
import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.domain.usecase.AuditLabelUseCase
import com.biteguard.core.domain.usecase.ExtractHighlightTermsUseCase
import com.biteguard.core.domain.usecase.FilterScanHistoryUseCase
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.TelemetryEvent
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUseCasesTest {

    private val engine = RuleBasedOfflineAuditEngine()

    @Test
    fun `offline audit engine flags hidden chemical derivatives and cross-contamination advisories`() {
        val profile = UserDietaryProfile(
            selectedAllergens = setOf(Allergen.PEANUTS, Allergen.SESAME),
            healthGoals = setOf(HealthGoal.LOW_SUGAR)
        )

        val verdict = engine.evaluateOffline(
            rawText = "Granola Bar\nIngredients: Oats, Arachis Oil, Maltodextrin. May contain traces of sesame.",
            profile = profile
        )

        assertEquals(SafetyLevel.DANGER, verdict.safetyLevel)
        assertEquals("Granola Bar", verdict.productName)
        assertTrue(verdict.flaggedIngredients.any { it.name.contains("Arachis oil", ignoreCase = true) })
        assertTrue(verdict.flaggedIngredients.any { it.name.contains("Maltodextrin", ignoreCase = true) })

        val highlights = ExtractHighlightTermsUseCase(engine)(verdict, profile)
        assertTrue(highlights.any { it.equals("arachis oil", ignoreCase = true) })
    }

    @Test
    fun `filter scan history use case filters by safety level and search query`() {
        val profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        val danger = engine.evaluateOffline("Peanut Bar\nIngredients: Peanuts, Sugar", profile)
        val safe = engine.evaluateOffline("Mint Water\nIngredients: Carbonated Water, Mint", profile)
        val filterUseCase = FilterScanHistoryUseCase()

        assertEquals(listOf(danger), filterUseCase(listOf(danger, safe), "", SafetyLevel.DANGER))
        assertEquals(listOf(safe), filterUseCase(listOf(danger, safe), "mint", null))
        assertEquals(listOf(danger, safe), filterUseCase(listOf(danger, safe), "   ", null))
    }

    @Test
    fun `audit label use case rejects blank text without calling repository`() = runTest {
        var repoCalled = false
        val fakeRepo = object : AuditRepository {
            override val scanHistoryFlow: Flow<List<AuditVerdict>> = emptyFlow()
            override val telemetryEventsFlow: Flow<List<TelemetryEvent>> = emptyFlow()
            override fun performLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                imageJpegBytes: ByteArray?,
                thumbnailBytes: ByteArray?,
                retryAttempt: Int
            ): Flow<StreamState<AuditVerdict>> {
                repoCalled = true
                return flowOf(StreamState.Idle)
            }
            override suspend fun evaluateOfflineOnly(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                thumbnailBytes: ByteArray?
            ): AuditVerdict = error("unused")
            override suspend fun submitFeedback(verdict: AuditVerdict, feedback: UserFeedback) = Unit
            override suspend fun clearScanHistory() = Unit
            override suspend fun deleteVerdicts(ids: List<String>) = Unit
        }

        val useCase = AuditLabelUseCase(fakeRepo)
        useCase("   ", UserDietaryProfile()).test {
            val item = awaitItem()
            assertTrue(item is StreamState.Error)
            assertEquals(AuditErrorKind.NO_TEXT_DETECTED, (item as StreamState.Error).errorKind)
            awaitComplete()
        }
        assertTrue(!repoCalled)
    }
}
