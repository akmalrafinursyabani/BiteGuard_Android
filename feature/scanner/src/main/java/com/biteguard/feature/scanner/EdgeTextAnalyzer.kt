package com.biteguard.feature.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class DetectedTextBox(
    val text: String,
    val boundingBox: Rect,
    val containsIngredientKeyword: Boolean
)

data class EdgeOcrFrameResult(
    val fullText: String,
    val boxes: List<DetectedTextBox>,
    val imageWidth: Int,
    val imageHeight: Int,
    val isReadyToScan: Boolean,
    val isThrottled: Boolean,
    val processingLatencyMs: Long,
    val latestFrameBitmap: Bitmap?
)

/**
 * FR-02, NFR-T1, NFR-T2: Real-Time On-Device Edge Text Detection via CameraX & MLKit v2.
 * - Executes on `Dispatchers.Default`.
 * - Explicitly closes every `ImageProxy` in `finally` to prevent memory leaks and buffer starvation.
 * - Automatically throttles from 30 FPS to 5 FPS (200ms gate) when no text or movement change is
 *   detected for > 5 seconds (NFR-T2 Thermal Throttling Protection).
 */
class EdgeTextAnalyzer(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onResult: (EdgeOcrFrameResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val isProcessing = AtomicBoolean(false)

    @Volatile
    private var lastAnalyzedTimestampMs: Long = 0L

    @Volatile
    private var lastChangeTimestampMs: Long = System.currentTimeMillis()

    @Volatile
    private var lastTextSignature: Int = 0

    @Volatile
    private var lastCentroidSum: Int = 0

    @Volatile
    private var isThrottledMode: Boolean = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        // NFR-T2: Thermal Throttling gate (5 FPS = 200ms minimum interval when idle > 5s)
        val minIntervalMs = if (isThrottledMode) 200L else 33L
        if (now - lastAnalyzedTimestampMs < minIntervalMs || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestampMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val isRotated90or270 = rotationDegrees == 90 || rotationDegrees == 270
        val orientedWidth = if (isRotated90or270) imageProxy.height else imageProxy.width
        val orientedHeight = if (isRotated90or270) imageProxy.width else imageProxy.height

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        scope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val visionText = recognizer.process(inputImage).await()
                val latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

                val boxes = visionText.textBlocks.mapNotNull { block ->
                    val rect = block.boundingBox ?: return@mapNotNull null
                    val blockText = block.text.trim()
                    if (blockText.isEmpty()) return@mapNotNull null
                    DetectedTextBox(
                        text = blockText,
                        boundingBox = rect,
                        containsIngredientKeyword = containsStructuralKeyword(blockText)
                    )
                }

                val fullText = visionText.text.trim()
                val isReady = boxes.any { it.containsIngredientKeyword } || containsStructuralKeyword(fullText)

                // Decode bitmap on background thread (Dispatchers.Default) only when text is present
                val snapshotBitmap = if (boxes.isNotEmpty()) {
                    runCatching { imageProxy.toBitmap() }.getOrNull()
                } else {
                    null
                }

                // Check if text or bounding box centroids changed to manage 5-second thermal throttling
                val currentSignature = fullText.lowercase().take(180).hashCode()
                val currentCentroid = boxes.sumOf { it.boundingBox.centerX() + it.boundingBox.centerY() }
                val movedSignificantly = abs(currentCentroid - lastCentroidSum) > 45

                if (currentSignature != lastTextSignature || movedSignificantly) {
                    lastTextSignature = currentSignature
                    lastCentroidSum = currentCentroid
                    lastChangeTimestampMs = System.currentTimeMillis()
                    isThrottledMode = false
                } else if (System.currentTimeMillis() - lastChangeTimestampMs >= 5_000L) {
                    isThrottledMode = true
                }

                onResult(
                    EdgeOcrFrameResult(
                        fullText = fullText,
                        boxes = boxes,
                        imageWidth = orientedWidth.coerceAtLeast(1),
                        imageHeight = orientedHeight.coerceAtLeast(1),
                        isReadyToScan = isReady,
                        isThrottled = isThrottledMode,
                        processingLatencyMs = latencyMs,
                        latestFrameBitmap = snapshotBitmap
                    )
                )
            } catch (t: Throwable) {
                runCatching {
                    android.util.Log.w(TAG, "Edge OCR frame skipped/failed: [${t::class.java.simpleName}] ${t.message}")
                }
            } finally {
                // NFR-T2: Always close ImageProxy immediately after processing
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }

    fun close() {
        scope.cancel()
        runCatching { recognizer.close() }
    }

    companion object {
        private const val TAG = "BiteGuardAI_EdgeOCR"
        private val STRUCTURAL_KEYWORDS = listOf(
            "ingredients:",
            "ingredients",
            "contains:",
            "may contain:",
            "may contain",
            "allergen information",
            "nutrition facts",
            "komposisi"
        )

        fun containsStructuralKeyword(text: String): Boolean {
            if (text.isBlank()) return false
            val lower = text.lowercase()
            return STRUCTURAL_KEYWORDS.any { kw -> lower.contains(kw) }
        }
    }
}
