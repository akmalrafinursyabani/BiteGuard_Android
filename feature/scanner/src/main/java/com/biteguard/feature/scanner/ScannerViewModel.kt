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

data class BlockSelectionSession(
    val isActive: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val capturedBoxes: List<DetectedTextBox> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val imageWidth: Int = 1080,
    val imageHeight: Int = 1920
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
    val retryAttempt: Int = 0,
    val isSelectingBlocks: Boolean = false,
    val capturedFrameBitmap: Bitmap? = null,
    val capturedBoxes: List<DetectedTextBox> = emptyList(),
    val selectedBlockIndices: Set<Int> = emptySet(),
    val capturedFrameWidth: Int = 1080,
    val capturedFrameHeight: Int = 1920
) {
    /**
     * NFR-NT1: Progressive scanner status text (never a static loading spinner).
     */
    val progressiveStatusText: String
        get() = when {
            isSelectingBlocks -> "Select which text block(s) to audit (${selectedBlockIndices.size}/${capturedBoxes.size} selected)"
            else -> when (val s = streamState) {
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
}

sealed interface ScannerIntent {
    data class OnEdgeFrameAnalyzed(val result: EdgeOcrFrameResult) : ScannerIntent
    data object TriggerCaptureAudit : ScannerIntent
    data object CaptureFrameForBlockSelection : ScannerIntent
    data class ToggleBlockSelection(val index: Int) : ScannerIntent
    data object SelectAllCapturedBlocks : ScannerIntent
    data object ClearCapturedBlockSelection : ScannerIntent
    data object CancelBlockSelection : ScannerIntent
    data object ConfirmBlockSelectionAndAudit : ScannerIntent
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
    private val blockSelectionFlow = MutableStateFlow(BlockSelectionSession())

    private var activeAuditJob: Job? = null
    private var lastCapturedText: String = ""
    private var lastCapturedBitmap: Bitmap? = null
    private var latestNonNullCameraBitmap: Bitmap? = null

    val uiState: StateFlow<ScannerUiState> = combine(
        profileRepository.userProfileFlow,
        streamStateFlow,
        liveFrameFlow,
        combine(manualSheetFlow, retryAttemptFlow, blockSelectionFlow) { manual, retry, blockSel ->
            Triple(manual, retry, blockSel)
        }
    ) { profile, stream, frame, (manualPair, retryCount, blockSel) ->
        val (showManual, manualText) = manualPair
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
            retryAttempt = retryCount,
            isSelectingBlocks = blockSel.isActive,
            capturedFrameBitmap = blockSel.capturedBitmap,
            capturedBoxes = blockSel.capturedBoxes,
            selectedBlockIndices = blockSel.selectedIndices,
            capturedFrameWidth = blockSel.imageWidth,
            capturedFrameHeight = blockSel.imageHeight
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScannerUiState()
    )

    fun onIntent(intent: ScannerIntent) {
        when (intent) {
            is ScannerIntent.OnEdgeFrameAnalyzed -> {
                if (intent.result.latestFrameBitmap != null) {
                    latestNonNullCameraBitmap = intent.result.latestFrameBitmap
                }
                if (blockSelectionFlow.value.isActive) {
                    // Keep frozen blocks stable while the user is choosing which block(s) to scan
                    return
                }
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
                lastCapturedBitmap = frame?.latestFrameBitmap ?: latestNonNullCameraBitmap
                startAuditStream(
                    rawText = textToAudit,
                    bitmap = lastCapturedBitmap,
                    attempt = 0
                )
            }

            ScannerIntent.CaptureFrameForBlockSelection -> {
                val frame = liveFrameFlow.value
                val boxes = frame?.boxes.orEmpty()
                val snapshot = frame?.latestFrameBitmap ?: latestNonNullCameraBitmap
                if (boxes.isEmpty()) {
                    val fallbackText = frame?.fullText.orEmpty().trim()
                    lastCapturedBitmap = snapshot
                    startAuditStream(
                        rawText = fallbackText,
                        bitmap = lastCapturedBitmap,
                        attempt = 0
                    )
                } else {
                    val keywordMatches = boxes.mapIndexedNotNull { idx, box ->
                        if (box.containsIngredientKeyword) idx else null
                    }.toSet()
                    val defaultSelection = if (keywordMatches.isNotEmpty()) {
                        keywordMatches
                    } else {
                        boxes.indices.toSet()
                    }
                    blockSelectionFlow.value = BlockSelectionSession(
                        isActive = true,
                        capturedBitmap = snapshot,
                        capturedBoxes = boxes,
                        selectedIndices = defaultSelection,
                        imageWidth = frame?.imageWidth ?: 1080,
                        imageHeight = frame?.imageHeight ?: 1920
                    )
                }
            }

            is ScannerIntent.ToggleBlockSelection -> {
                val current = blockSelectionFlow.value
                if (!current.isActive || intent.index !in current.capturedBoxes.indices) return
                val updated = if (intent.index in current.selectedIndices) {
                    current.selectedIndices - intent.index
                } else {
                    current.selectedIndices + intent.index
                }
                blockSelectionFlow.value = current.copy(selectedIndices = updated)
            }

            ScannerIntent.SelectAllCapturedBlocks -> {
                val current = blockSelectionFlow.value
                if (!current.isActive) return
                blockSelectionFlow.value = current.copy(
                    selectedIndices = current.capturedBoxes.indices.toSet()
                )
            }

            ScannerIntent.ClearCapturedBlockSelection -> {
                val current = blockSelectionFlow.value
                if (!current.isActive) return
                blockSelectionFlow.value = current.copy(selectedIndices = emptySet())
            }

            ScannerIntent.CancelBlockSelection -> {
                blockSelectionFlow.value = BlockSelectionSession(isActive = false)
            }

            ScannerIntent.ConfirmBlockSelectionAndAudit -> {
                val current = blockSelectionFlow.value
                val selectedText = current.selectedIndices
                    .sorted()
                    .mapNotNull { idx -> current.capturedBoxes.getOrNull(idx)?.text }
                    .joinToString("\n")
                    .trim()
                lastCapturedBitmap = current.capturedBitmap ?: latestNonNullCameraBitmap
                blockSelectionFlow.value = BlockSelectionSession(isActive = false)
                startAuditStream(
                    rawText = selectedText,
                    bitmap = lastCapturedBitmap,
                    attempt = 0
                )
            }

            is ScannerIntent.TriggerSampleOrManualAudit -> {
                manualSheetFlow.value = false to ""
                blockSelectionFlow.value = BlockSelectionSession(isActive = false)
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
                blockSelectionFlow.value = BlockSelectionSession(isActive = false)
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
        debugLog("startAuditStream | attempt=$attempt | textLen=${lastCapturedText.length} | hasBitmap=${bitmap != null}")

        if (lastCapturedText.isBlank()) {
            debugWarn("startAuditStream aborted: no text detected on label")
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
                debugLog("Retrying Cloud AI attempt #$attempt after backoff=${backoffMs}ms")
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
                when (state) {
                    is StreamState.Streaming -> debugLog("StreamState.Streaming: stage='${state.stageMessage}' | partialLevel=${state.partialVerdict.partialSafetyLevel}")
                    is StreamState.Success -> debugLog("StreamState.Success: safetyLevel=${state.data.safetyLevel} | isOfflineFallback=${state.data.isOfflineFallback}")
                    is StreamState.Error -> debugError("StreamState.Error: kind=${state.errorKind} | cause=[${state.throwable::class.java.simpleName}] ${state.throwable.message}", state.throwable)
                    else -> Unit
                }
                streamStateFlow.value = state
            }
        }
    }

    companion object {
        private const val TAG = "BiteGuardAI_ScannerVM"

        private fun debugLog(message: String) {
            runCatching { android.util.Log.d(TAG, message) }
        }

        private fun debugWarn(message: String) {
            runCatching { android.util.Log.w(TAG, message) }
        }

        private fun debugError(message: String, throwable: Throwable? = null) {
            runCatching {
                if (throwable != null) android.util.Log.e(TAG, message, throwable) else android.util.Log.e(TAG, message)
            }
        }
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
