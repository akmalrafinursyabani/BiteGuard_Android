package com.biteguard.feature.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import app.cash.turbine.test
import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.audit.DefaultAuditRepository
import com.biteguard.core.data.db.InMemoryAuditLogDataSource
import com.biteguard.core.data.profile.InMemoryProfileRepository
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Main Feature Use-Case Unit Tests for ScannerViewModel (FR-02, FR-03, FR-04, FR-06, NFR-NT1).
 * Results are stored in JUnit XML format for CircleCI (`store_test_results`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Use Case 1 (FR-02 & NFR-NT1): Real-time Edge OCR frame analysis detects structural keyword
     * ("Ingredients:") and transitions to ScanningEdge with Ready-to-Scan progressive status.
     */
    @Test
    fun `UseCase 1 - Edge OCR frame with Ingredients keyword sets ReadyToScan and progressive status`() = runTest {
        val dummyAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = true
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                frameBitmap: Bitmap?
            ): Flow<String> = flow {}
        }
        val viewModel = ScannerViewModel(
            auditRepository = DefaultAuditRepository(dummyAi, InMemoryAuditLogDataSource()),
            profileRepository = InMemoryProfileRepository()
        )

        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.streamState is StreamState.Idle)

            val frameResult = EdgeOcrFrameResult(
                fullText = "Ingredients: Rolled Oats, Arachis Oil, Whey Protein",
                boxes = listOf(
                    DetectedTextBox(
                        text = "Ingredients: Rolled Oats, Arachis Oil",
                        boundingBox = Rect(10, 20, 400, 120),
                        containsIngredientKeyword = true
                    )
                ),
                imageWidth = 1080,
                imageHeight = 1920,
                isReadyToScan = true,
                isThrottled = false,
                processingLatencyMs = 84L,
                latestFrameBitmap = null
            )

            viewModel.onIntent(ScannerIntent.OnEdgeFrameAnalyzed(frameResult))
            var updated = awaitItem()
            if (updated.streamState is StreamState.Idle) {
                updated = awaitItem()
            }
            assertTrue(updated.streamState is StreamState.ScanningEdge)
            assertTrue(updated.isReadyToScan)
            assertEquals(1, updated.liveBoxes.size)
            assertTrue(updated.progressiveStatusText.contains("Ready to Scan"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case 2 (FR-03): Multimodal AI streaming audit emits token-by-token Streaming states
     * and resolves to DANGER when chemical allergen derivatives (Arachis Oil / Casein) are detected.
     */
    @Test
    fun `UseCase 2 - Streaming Cloud AI audit detects hidden peanut and dairy derivatives as DANGER`() = runTest {
        val streamingAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = true
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                frameBitmap: Bitmap?
            ): Flow<String> = flow {
                emit("VERDICT: DANGER\n")
                emit("SUMMARY: Product contains Arachis Oil (peanut) and Sodium Caseinate (dairy).\n")
                emit(
                    """
                    FLAGS_JSON:
                    [{"name":"Arachis Oil","riskReason":"Refined peanut oil","severity":"HIGH"},
                     {"name":"Sodium Caseinate","riskReason":"Milk protein derivative","severity":"HIGH"}]
                    """.trimIndent()
                )
            }
        }
        val profileRepo = InMemoryProfileRepository(
            UserDietaryProfile(
                selectedAllergens = setOf(Allergen.PEANUTS, Allergen.DAIRY),
                healthGoals = setOf(HealthGoal.LOW_SUGAR)
            )
        )
        val auditLog = InMemoryAuditLogDataSource()
        val viewModel = ScannerViewModel(DefaultAuditRepository(streamingAi, auditLog), profileRepo)

        viewModel.uiState.test {
            awaitItem() // Idle

            viewModel.onIntent(
                ScannerIntent.TriggerSampleOrManualAudit(
                    "Ingredients: Rolled Oats, Arachis Oil, Sodium Caseinate."
                )
            )

            var state = awaitItem()
            while (state.streamState is StreamState.Streaming) {
                state = awaitItem()
            }

            assertTrue(state.streamState is StreamState.Success)
            val verdict = (state.streamState as StreamState.Success).data
            assertEquals(SafetyLevel.DANGER, verdict.safetyLevel)
            assertFalse(verdict.isOfflineFallback)
            assertTrue(verdict.flaggedIngredients.any { it.name.contains("Arachis Oil", ignoreCase = true) })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case 3 (FR-04): When Cloud AI fails or is unreachable, ScannerViewModel transitions
     * to StreamState.Error with an actionable offline fallback verdict and allows user to keep it.
     */
    @Test
    fun `UseCase 3 - Cloud failure provides actionable offline fallback verdict and user can keep local match`() = runTest {
        val offlineAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = false
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                frameBitmap: Bitmap?
            ): Flow<String> = flow {
                throw IllegalStateException("Network unreachable")
            }
        }
        val profileRepo = InMemoryProfileRepository(
            UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )
        val auditRepo = DefaultAuditRepository(offlineAi, InMemoryAuditLogDataSource())
        val viewModel = ScannerViewModel(auditRepo, profileRepo)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.streamState is StreamState.Idle)

            viewModel.onIntent(
                ScannerIntent.TriggerSampleOrManualAudit(
                    "Ingredients: Oats, Arachis Oil, Honey."
                )
            )

            var current = awaitItem()
            while (current.streamState is StreamState.Streaming) {
                current = awaitItem()
            }

            assertTrue(current.streamState is StreamState.Error)
            val err = current.streamState as StreamState.Error
            assertEquals(AuditErrorKind.NETWORK_TIMEOUT, err.errorKind)
            assertEquals(SafetyLevel.DANGER, err.fallbackVerdict?.safetyLevel)

            // User taps "Keep Local Match" action
            viewModel.onIntent(ScannerIntent.AcceptOfflineFallbackVerdict)
            val accepted = awaitItem()
            assertTrue(accepted.streamState is StreamState.Success)
            assertEquals(SafetyLevel.DANGER, (accepted.streamState as StreamState.Success).data.safetyLevel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case 4 (FR-02 & Actionable Error): Triggering capture when no readable text is present
     * emits StreamState.Error with AuditErrorKind.NO_TEXT_DETECTED so the user can retry or paste text.
     */
    @Test
    fun `UseCase 4 - Triggering capture with empty OCR text emits NO_TEXT_DETECTED actionable error`() = runTest {
        val dummyAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = true
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                frameBitmap: Bitmap?
            ): Flow<String> = flow {}
        }
        val viewModel = ScannerViewModel(
            auditRepository = DefaultAuditRepository(dummyAi, InMemoryAuditLogDataSource()),
            profileRepository = InMemoryProfileRepository()
        )

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.onIntent(ScannerIntent.TriggerCaptureAudit)

            val errState = awaitItem()
            assertTrue(errState.streamState is StreamState.Error)
            val error = errState.streamState as StreamState.Error
            assertEquals(AuditErrorKind.NO_TEXT_DETECTED, error.errorKind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case 5 (FR-06): Submitting binary user feedback (Thumbs Up / Thumbs Down) on a completed
     * scan updates the active verdict state and queues a telemetry event in the database.
     */
    @Test
    fun `UseCase 5 - Submitting binary feedback updates verdict and queues telemetry metric event`() = runTest {
        val cleanAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = true
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                frameBitmap: Bitmap?
            ): Flow<String> = flow {
                emit("VERDICT: SAFE\nSUMMARY: Clean sparkling water with no allergens.\nFLAGS_JSON: []")
            }
        }
        val auditLog = InMemoryAuditLogDataSource()
        val viewModel = ScannerViewModel(
            auditRepository = DefaultAuditRepository(cleanAi, auditLog),
            profileRepository = InMemoryProfileRepository()
        )

        viewModel.uiState.test {
            awaitItem() // Idle

            viewModel.onIntent(
                ScannerIntent.TriggerSampleOrManualAudit("Ingredients: Carbonated Water, Lemon Extract.")
            )

            var state = awaitItem()
            while (state.streamState is StreamState.Streaming) {
                state = awaitItem()
            }

            assertTrue(state.streamState is StreamState.Success)
            val verdict = (state.streamState as StreamState.Success).data
            assertEquals(SafetyLevel.SAFE, verdict.safetyLevel)

            viewModel.onIntent(ScannerIntent.SubmitFeedback(verdict, UserFeedback.THUMBS_UP))
            val afterFeedback = awaitItem()
            val updatedVerdict = (afterFeedback.streamState as StreamState.Success).data
            assertEquals(UserFeedback.THUMBS_UP, updatedVerdict.userFeedback)

            val queuedTelemetry = auditLog.telemetryQueueFlow.first()
            assertEquals(1, queuedTelemetry.size)
            assertEquals(verdict.id, queuedTelemetry.first().promptExecutionId)
            assertEquals(UserFeedback.THUMBS_UP, queuedTelemetry.first().rating)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case 6 (3-Stage Block Picker Flow): Capturing a frame freezes detected OCR blocks so
     * subsequent camera frames do not overwrite them, pre-selects ingredient blocks, allows toggling,
     * and audits the chosen blocks.
     */
    @Test
    fun `UseCase 6 - CaptureFrameForBlockSelection freezes OCR blocks and audits user-selected blocks`() = runTest {
        var auditedRawText = ""
        val recordingAi = object : AiStreamDataSource {
            override val isApiKeyConfigured: Boolean = true
            override fun streamLabelAudit(
                rawExtractedText: String,
                profile: UserDietaryProfile,
                frameBitmap: Bitmap?
            ): Flow<String> = flow {
                auditedRawText = rawExtractedText
                emit("VERDICT: SAFE\nSUMMARY: Selected blocks are safe.\nFLAGS_JSON: []")
            }
        }
        val viewModel = ScannerViewModel(
            auditRepository = DefaultAuditRepository(recordingAi, InMemoryAuditLogDataSource()),
            profileRepository = InMemoryProfileRepository()
        )

        viewModel.uiState.test {
            awaitItem() // Idle

            val frame1 = EdgeOcrFrameResult(
                fullText = "Brand Header\nIngredients: Oats, Honey\nNet Wt 200g",
                boxes = listOf(
                    DetectedTextBox("Brand Header", Rect(0, 0, 100, 40), containsIngredientKeyword = false),
                    DetectedTextBox("Ingredients: Oats, Honey", Rect(0, 50, 300, 120), containsIngredientKeyword = true),
                    DetectedTextBox("Net Wt 200g", Rect(0, 130, 100, 160), containsIngredientKeyword = false)
                ),
                imageWidth = 1080,
                imageHeight = 1920,
                isReadyToScan = true,
                isThrottled = false,
                processingLatencyMs = 45L,
                latestFrameBitmap = null
            )

            viewModel.onIntent(ScannerIntent.OnEdgeFrameAnalyzed(frame1))
            var state = awaitItem()
            if (state.streamState is StreamState.Idle) {
                state = awaitItem()
            }

            // Freeze frame for block selection
            viewModel.onIntent(ScannerIntent.CaptureFrameForBlockSelection)
            state = awaitItem()
            while (!state.isSelectingBlocks) {
                state = awaitItem()
            }
            assertTrue(state.isSelectingBlocks)
            assertEquals(3, state.capturedBoxes.size)
            // Block #1 ("Ingredients: Oats, Honey") is automatically pre-selected
            assertEquals(setOf(1), state.selectedBlockIndices)

            // User also selects Block #0 ("Brand Header")
            viewModel.onIntent(ScannerIntent.ToggleBlockSelection(0))
            state = awaitItem()
            assertEquals(setOf(0, 1), state.selectedBlockIndices)

            // User confirms block selection and runs audit
            viewModel.onIntent(ScannerIntent.ConfirmBlockSelectionAndAudit)
            state = awaitItem()
            while (state.streamState !is StreamState.Success) {
                state = awaitItem()
            }

            assertFalse(state.isSelectingBlocks)
            assertTrue(state.streamState is StreamState.Success)
            assertEquals("Brand Header\nIngredients: Oats, Honey", auditedRawText)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
