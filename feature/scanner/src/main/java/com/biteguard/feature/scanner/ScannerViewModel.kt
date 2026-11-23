package com.biteguard.feature.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biteguard.core.data.audit.AuditRepository
import com.biteguard.core.data.profile.ProfileRepository
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SamplePackagePreset(
    val title: String,
    val subtitle: String,
    val rawIngredients: String
)

data class ScannerUiState(
    val profile: UserDietaryProfile = UserDietaryProfile(),
    val streamState: StreamState<AuditVerdict> = StreamState.Idle,
    val liveBoxes: List<DetectedTextBox> = emptyList(),
    val frameWidth: Int = 1080,
    val frameHeight: Int = 1920,
    val latestOcrText: String = "",
    val isReadyToScan: Boolean = false,
    val isThrottled: Boolean = false,
    val ocrLatencyMs: Long = 0L,
    val showManualInputSheet: Boolean = false,
    val manualInputText: String = "",
    val retryAttempt: Int = 0
) {
    /**
     * NFR-NT1: Progressive scanner status text (never a static loading spinner).
     */
    val progressiveStatusText: String
        get() = when (val s = streamState) {
            is StreamState.Streaming -> s.stageMessage
            is StreamState.Success -> "Audit complete: ${s.data.safetyLevel.label}"
            is StreamState.Error -> "Cloud AI unavailable — Local Match ready"
            is StreamState.ScanningEdge -> when {
                s.isReadyToScan -> "Ready to Scan — Ingredient list locked (${ocrLatencyMs}ms)"
                s.detectedTextBlocks > 0 -> "Extracting text... (${s.detectedTextBlocks} text blocks)"
                else -> "Focusing on label... Point camera at 'Ingredients:'"
            }
            StreamState.Idle -> "Focusing on label... Point camera at package ingredients"
        }
}

sealed interface ScannerIntent {
    data class OnEdgeFrameAnalyzed(val result: EdgeOcrFrameResult) : ScannerIntent
    data object TriggerCaptureAudit : ScannerIntent
    data class TriggerSampleOrManualAudit(val rawText: String) : ScannerIntent
    data object RetryFailedAuditWithBackoff : ScannerIntent
    data object AcceptOfflineFallbackVerdict : ScannerIntent
    data object DismissAuditResult : ScannerIntent
    data class ToggleManualInputSheet(val show: Boolean, val prefillText: String? = null) : ScannerIntent
    data class UpdateManualInputText(val text: String) : ScannerIntent
    data class SubmitFeedback(val verdict: AuditVerdict, val feedback: UserFeedback) : ScannerIntent
}

class ScannerViewModel(
    private val auditRepository: AuditRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val streamStateFlow = MutableStateFlow<StreamState<AuditVerdict>>(StreamState.Idle)
    private val liveFrameFlow = MutableStateFlow<EdgeOcrFrameResult?>(null)
    private val manualSheetFlow = MutableStateFlow(false to "")
    private val retryAttemptFlow = MutableStateFlow(0)

    private var activeAuditJob: Job? = null
    private var lastCapturedText: String = ""
    private var lastCapturedBitmap: Bitmap? = null

    val uiState: StateFlow<ScannerUiState> = combine(
        profileRepository.userProfileFlow,
        streamStateFlow,
        liveFrameFlow,
        manualSheetFlow,
        retryAttemptFlow
    ) { profile, stream, frame, (showManual, manualText), retryCount ->
        ScannerUiState(
            profile = profile,
            streamState = stream,
            liveBoxes = frame?.boxes.orEmpty(),
            frameWidth = frame?.imageWidth ?: 1080,
            frameHeight = frame?.imageHeight ?: 1920,
            latestOcrText = frame?.fullText.orEmpty(),
            isReadyToScan = frame?.isReadyToScan ?: false,
            isThrottled = frame?.isThrottled ?: false,
            ocrLatencyMs = frame?.processingLatencyMs ?: 0L,
            showManualInputSheet = showManual,
            manualInputText = manualText,
            retryAttempt = retryCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScannerUiState()
    )

    fun onIntent(intent: ScannerIntent) {
        when (intent) {
            is ScannerIntent.OnEdgeFrameAnalyzed -> {
                liveFrameFlow.value = intent.result
                val current = streamStateFlow.value
                // Only update edge scanning state when not actively streaming or viewing a result
                if (current is StreamState.Idle || current is StreamState.ScanningEdge) {
                    streamStateFlow.value = StreamState.ScanningEdge(
                        detectedTextBlocks = intent.result.boxes.size,
                        isReadyToScan = intent.result.isReadyToScan,
                        isThrottled = intent.result.isThrottled,
                        previewSnippet = intent.result.fullText.take(120)
                    )
                }
            }

            ScannerIntent.TriggerCaptureAudit -> {
                val frame = liveFrameFlow.value
                val textToAudit = frame?.fullText.orEmpty().trim()
                lastCapturedBitmap = frame?.latestFrameBitmap
                startAuditStream(
                    rawText = textToAudit,
                    bitmap = lastCapturedBitmap,
                    attempt = 0
                )
            }

            is ScannerIntent.TriggerSampleOrManualAudit -> {
                manualSheetFlow.value = false to ""
                lastCapturedBitmap = null
                startAuditStream(
                    rawText = intent.rawText,
                    bitmap = null,
                    attempt = 0
                )
            }

            ScannerIntent.RetryFailedAuditWithBackoff -> {
                val nextAttempt = retryAttemptFlow.value + 1
                retryAttemptFlow.value = nextAttempt
                val textToRetry = lastCapturedText.ifBlank { liveFrameFlow.value?.fullText.orEmpty() }
                startAuditStream(
                    rawText = textToRetry,
                    bitmap = lastCapturedBitmap,
                    attempt = nextAttempt
                )
            }

            ScannerIntent.AcceptOfflineFallbackVerdict -> {
                val current = streamStateFlow.value
                if (current is StreamState.Error && current.fallbackVerdict != null) {
                    streamStateFlow.value = StreamState.Success(current.fallbackVerdict!!)
                } else {
                    val text = lastCapturedText.ifBlank { liveFrameFlow.value?.fullText.orEmpty() }
                    viewModelScope.launch {
                        val offlineVerdict = auditRepository.evaluateOfflineOnly(
                            rawExtractedText = text,
                            profile = uiState.value.profile,
                            frameBitmap = lastCapturedBitmap
                        )
                        streamStateFlow.value = StreamState.Success(offlineVerdict)
                    }
                }
            }

            ScannerIntent.DismissAuditResult -> {
                activeAuditJob?.cancel()
                retryAttemptFlow.value = 0
                streamStateFlow.value = StreamState.Idle
            }

            is ScannerIntent.ToggleManualInputSheet -> {
                val prefill = intent.prefillText ?: lastCapturedText.ifBlank {
                    liveFrameFlow.value?.fullText.orEmpty()
                }
                manualSheetFlow.value = intent.show to prefill
            }

            is ScannerIntent.UpdateManualInputText -> {
                manualSheetFlow.value = manualSheetFlow.value.first to intent.text
            }

            is ScannerIntent.SubmitFeedback -> viewModelScope.launch {
                auditRepository.submitFeedback(intent.verdict, intent.feedback)
                val current = streamStateFlow.value
                if (current is StreamState.Success && current.data.id == intent.verdict.id) {
                    streamStateFlow.value = StreamState.Success(
                        current.data.copy(userFeedback = intent.feedback)
                    )
                }
            }
        }
    }

    private fun startAuditStream(
        rawText: String,
        bitmap: Bitmap?,
        attempt: Int
    ) {
        activeAuditJob?.cancel()
        lastCapturedText = rawText.trim()
        retryAttemptFlow.value = attempt

        if (lastCapturedText.isBlank()) {
            streamStateFlow.value = StreamState.Error(
                throwable = IllegalArgumentException("No text detected on label"),
                cachedRawText = "",
                errorKind = AuditErrorKind.NO_TEXT_DETECTED,
                fallbackVerdict = null,
                retryAttempt = attempt
            )
            return
        }

        activeAuditJob = viewModelScope.launch {
            if (attempt > 0) {
                val backoffMs = ((1L shl attempt.coerceAtMost(3)) * 350L).coerceAtMost(2_500L)
                streamStateFlow.value = StreamState.Streaming(
                    partialVerdict = com.biteguard.core.model.PartialAuditVerdict(),
                    stageMessage = "Retrying Cloud AI (attempt #$attempt)..."
                )
                delay(backoffMs)
            }

            auditRepository.performLabelAudit(
                rawExtractedText = lastCapturedText,
                profile = uiState.value.profile,
                frameBitmap = bitmap,
                retryAttempt = attempt
            ).collect { state ->
                streamStateFlow.value = state
            }
        }
    }

    companion object {
        val SAMPLE_PRESETS = listOf(
            SamplePackagePreset(
                title = "Granola Protein Bar (Hidden Peanut & Dairy)",
                subtitle = "Tests Arachis Oil, Casein & Whey detection",
                rawIngredients = """
                     Crunchy Granola Protein Bar
                    Ingredients: Rolled Oats, Hydrolyzed Whey Protein Isolate, Roasted Almonds, Honey, Arachis Oil (Refined), Sodium Caseinate, Soy Lecithin, Natural Vanilla Flavor.
                    Contains: Milk, Soy, Tree Nuts. May contain traces of sesame and wheat.
                """.trimIndent()
            ),
            SamplePackagePreset(
                title = "Savory Instant Noodles (High Sodium & Sugar)",
                subtitle = "Tests Maltodextrin, HFCS, Disodium Inosinate & Pork Gelatin",
                rawIngredients = """
                    Spicy Umami Ramen Bowl
                    Ingredients: Enriched Wheat Flour, Palm Oil, Salt, Maltodextrin, High-Fructose Corn Syrup, Monosodium Glutamate, Disodium Inosinate, Disodium Guanylate, Hydrolyzed Pork Gelatin, Dehydrated Soy Sauce, Anchovy Extract.
                    Contains: Wheat, Soy, Fish.
                """.trimIndent()
            ),
            SamplePackagePreset(
                title = "Organic Sparkling Herb Water (100% Clean)",
                subtitle = "Tests SAFE verdict across all allergens & health goals",
                rawIngredients = """
                    Organic Botanical Sparkling Water
                    Ingredients: Carbonated Filtered Water, Organic Lemon Peel Extract, Organic Mint Leaf Infusion, Ascorbic Acid (Vitamin C).
                    Contains: No major allergens. 0g Added Sugar, 0mg Sodium.
                """.trimIndent()
            )
        )
    }
}
