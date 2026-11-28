package com.biteguard.core.data

import app.cash.turbine.test
import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.ai.DefensiveVerdictParser
import com.biteguard.core.data.audit.DefaultAuditRepository
import com.biteguard.core.data.db.InMemoryAuditLogDataSource
import com.biteguard.core.domain.engine.RuleBasedOfflineAuditEngine
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRepositoryTest {

    @Test
    fun `streamLabelAudit emits Streaming then Success and saves to encrypted audit log`() = runTest {
        val fakeAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = true
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                imageJpegBytes: ByteArray?
            ): Flow<String> = flow {
                emit("VERDICT: DANGER\n")
                emit("SUMMARY: Contains arachis oil (peanut derivative) and whey protein.\n")
                emit(
                    """
                    FLAGS_JSON:
                    [{"name":"Arachis Oil","riskReason":"Refined peanut oil derivative","severity":"HIGH"}]
                    """.trimIndent()
                )
            }
        }
        val auditLog = InMemoryAuditLogDataSource()
        val repository = DefaultAuditRepository(
            aiStreamDataSource = fakeAi,
            auditLogDataSource = auditLog,
            firstTokenTimeoutMs = 5_000L
        )
        val profile = UserDietaryProfile(
            selectedAllergens = setOf(Allergen.PEANUTS, Allergen.DAIRY)
        )

        repository.performLabelAudit(
            rawExtractedText = "Ingredients: Rolled oats, arachis oil, whey protein.",
            profile = profile
        ).test {
            val initialStreaming = awaitItem()
            assertTrue(initialStreaming is StreamState.Streaming)

            var latestItem = awaitItem()
            while (latestItem is StreamState.Streaming) {
                latestItem = awaitItem()
            }

            assertTrue(latestItem is StreamState.Success)
            val verdict = (latestItem as StreamState.Success).data
            assertEquals(SafetyLevel.DANGER, verdict.safetyLevel)
            assertFalse(verdict.isOfflineFallback)
            assertTrue(verdict.flaggedIngredients.any { it.name.contains("Arachis", ignoreCase = true) })
            awaitComplete()
        }

        val savedHistory = auditLog.historyFlow.first()
        assertEquals(1, savedHistory.size)
        assertEquals(SafetyLevel.DANGER, savedHistory.first().safetyLevel)
    }

    @Test
    fun `streamLabelAudit falls back to local rule engine when cloud exceeds 5000ms first token timeout`() = runTest {
        val slowAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = true
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                imageJpegBytes: ByteArray?
            ): Flow<String> = flow {
                delay(6_500L)
                emit("VERDICT: SAFE")
            }
        }
        val auditLog = InMemoryAuditLogDataSource()
        val repository = DefaultAuditRepository(
            aiStreamDataSource = slowAi,
            auditLogDataSource = auditLog,
            firstTokenTimeoutMs = 5_000L
        )
        val profile = UserDietaryProfile(
            selectedAllergens = setOf(Allergen.DAIRY),
            healthGoals = setOf(HealthGoal.LOW_SUGAR)
        )

        repository.performLabelAudit(
            rawExtractedText = "Ingredients: Water, Sodium Caseinate, Maltodextrin, Natural Flavors.",
            profile = profile
        ).test {
            val first = awaitItem()
            assertTrue(first is StreamState.Streaming)

            val errorItem = awaitItem()
            assertTrue(errorItem is StreamState.Error)
            val err = errorItem as StreamState.Error
            assertEquals(AuditErrorKind.NETWORK_TIMEOUT, err.errorKind)
            assertNotNull(err.fallbackVerdict)
            assertEquals(SafetyLevel.DANGER, err.fallbackVerdict?.safetyLevel)
            assertTrue(err.fallbackVerdict!!.isOfflineFallback)
            assertTrue(err.fallbackVerdict!!.flaggedIngredients.any { it.name.contains("Caseinate", ignoreCase = true) })
            awaitComplete()
        }
    }

    @Test
    fun `defensive parser sanitizes markdown fences and truncated JSON stream`() {
        val malformedOutput = """
            VERDICT: **CAUTION**
            SUMMARY: **High glycemic** maltodextrin and disodium inosinate found.
            FLAGS_JSON:
            ```json
            [{"name":"Maltodextrin","riskReason":"Spikes blood glucose","severity":"MEDIUM"}, {"name":"Incomplete
        """.trimIndent()

        val parsed = DefensiveVerdictParser.parseFinal(malformedOutput)
        assertEquals(SafetyLevel.CAUTION, parsed.safetyLevel)
        assertEquals("High glycemic maltodextrin and disodium inosinate found.", parsed.cleanSummary)
        assertEquals(1, parsed.flaggedIngredients.size)
        assertEquals("Maltodextrin", parsed.flaggedIngredients.first().name)
    }

    @Test
    fun `submitFeedback updates verdict and queues telemetry event`() = runTest {
        val fakeAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = false
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                imageJpegBytes: ByteArray?
            ): Flow<String> = flow {}
        }
        val auditLog = InMemoryAuditLogDataSource()
        val repository = DefaultAuditRepository(fakeAi, auditLog)

        val verdict = RuleBasedOfflineAuditEngine().evaluateOffline(
            rawText = "Ingredients: Filtered water, organic lemon extract.",
            profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )
        auditLog.insertVerdict(verdict)

        repository.submitFeedback(verdict, UserFeedback.THUMBS_UP)

        val telemetry = auditLog.telemetryQueueFlow.first()
        assertEquals(1, telemetry.size)
        assertEquals(verdict.id, telemetry.first().promptExecutionId)
        assertEquals(UserFeedback.THUMBS_UP, telemetry.first().rating)
    }
}
