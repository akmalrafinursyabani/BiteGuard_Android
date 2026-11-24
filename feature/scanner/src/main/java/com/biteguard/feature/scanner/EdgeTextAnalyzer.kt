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

                val rawBoxes = visionText.textBlocks.mapNotNull { block ->
                    val rect = block.boundingBox ?: return@mapNotNull null
                    val blockText = block.text.trim()
                    if (blockText.isEmpty()) return@mapNotNull null
                    DetectedTextBox(
                        text = blockText,
                        boundingBox = rect,
                        containsIngredientKeyword = containsStructuralKeyword(blockText)
                    )
                }

                // Spatial Paragraph Clustering: Merge adjacent/stacked lines into cohesive paragraph blocks
                val boxes = clusterTextBoxes(rawBoxes)

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

        private fun unionRects(a: Rect, b: Rect): Rect {
            return Rect().apply {
                left = minOf(a.left, b.left)
                top = minOf(a.top, b.top)
                right = maxOf(a.right, b.right)
                bottom = maxOf(a.bottom, b.bottom)
            }
        }

        /**
         * Clusters fragmented ML Kit text blocks into wider paragraph-level containers.
         * Reduces visual clutter on screen and enables 1-tap capture of entire ingredient sections.
         *
         * Mitigation: If clustering produces an empty result or fails, safely returns the raw input boxes.
         */
        fun clusterTextBoxes(rawBoxes: List<DetectedTextBox>): List<DetectedTextBox> {
            if (rawBoxes.size <= 1) return rawBoxes

            return try {
                // 1. Filter out stray micro-noise (e.g. isolated punctuation or single non-alphanumeric chars)
                val validBoxes = rawBoxes.filter { box ->
                    val trimmed = box.text.trim()
                    trimmed.isNotEmpty() && (trimmed.length > 1 || trimmed[0].isLetterOrDigit())
                }
                if (validBoxes.size <= 1) return validBoxes.ifEmpty { rawBoxes }

                // 2. Sort by vertical top position primarily (snapped to 12px grid) then left
                val sorted = validBoxes.sortedWith(
                    compareBy<DetectedTextBox> { it.boundingBox.top / 12 }
                        .thenBy { it.boundingBox.left }
                )

                val clusters = mutableListOf<MutableList<DetectedTextBox>>()

                for (box in sorted) {
                    val bRect = box.boundingBox
                    val bHeight = bRect.bottom - bRect.top
                    val bCenterX = (bRect.left + bRect.right) / 2

                    // Find a cluster this box can merge into
                    val matchingCluster = clusters.firstOrNull { cluster ->
                        val clusterRect = cluster.fold(cluster.first().boundingBox) { acc, item ->
                            unionRects(acc, item.boundingBox)
                        }
                        val clusterHeight = clusterRect.bottom - clusterRect.top
                        val clusterCenterX = (clusterRect.left + clusterRect.right) / 2

                        // Check horizontal overlap
                        val horizontalOverlap = maxOf(0, minOf(clusterRect.right, bRect.right) - maxOf(clusterRect.left, bRect.left))
                        val horizontalGap = maxOf(0, bRect.left - clusterRect.right)
                        val sharesColumn = abs(clusterRect.left - bRect.left) < 60 || abs(clusterCenterX - bCenterX) < 80
                        val hasHorizontalAffinity = horizontalOverlap > 0 || sharesColumn

                        // Check vertical overlap or vertical gap
                        val verticalOverlap = maxOf(0, minOf(clusterRect.bottom, bRect.bottom) - maxOf(clusterRect.top, bRect.top))
                        val verticalGap = bRect.top - clusterRect.bottom
                        val avgLineHeight = (bHeight + clusterHeight) / (cluster.size + 1)
                        val maxAllowedGap = maxOf(48, (avgLineHeight * 1.6).toInt())

                        // Case 1: Same horizontal line (horizontal gap <= 40px and vertical overlap > 0)
                        val isSameLineContinuation = verticalOverlap > 0 && horizontalGap <= 40

                        // Case 2: Vertically stacked paragraph line (horizontal affinity and vertical gap in reasonable range)
                        val isVerticallyStackedParagraph = hasHorizontalAffinity && verticalGap in -24..maxAllowedGap

                        // Case 3: Keyword anchor affinity (contains "Ingredients" or "Contains", pull adjacent text)
                        val isKeywordAffinity = (cluster.any { it.containsIngredientKeyword } || box.containsIngredientKeyword) &&
                            hasHorizontalAffinity && verticalGap in -24..maxOf(72, avgLineHeight * 2)

                        isSameLineContinuation || isVerticallyStackedParagraph || isKeywordAffinity
                    }

                    if (matchingCluster != null) {
                        matchingCluster.add(box)
                    } else {
                        clusters.add(mutableListOf(box))
                    }
                }

                val clusteredBoxes = clusters.map { cluster ->
                    if (cluster.size == 1) {
                        cluster.first()
                    } else {
                        val unionRect = cluster.fold(cluster.first().boundingBox) { acc, item ->
                            unionRects(acc, item.boundingBox)
                        }
                        // Sort items in top-to-bottom, left-to-right reading order
                        val sortedItems = cluster.sortedWith(
                            compareBy<DetectedTextBox> { it.boundingBox.top / 10 }
                                .thenBy { it.boundingBox.left }
                        )
                        val mergedText = sortedItems.joinToString("\n") { it.text.trim() }
                        val hasKeyword = cluster.any { it.containsIngredientKeyword } || containsStructuralKeyword(mergedText)

                        DetectedTextBox(
                            text = mergedText,
                            boundingBox = unionRect,
                            containsIngredientKeyword = hasKeyword
                        )
                    }
                }

                clusteredBoxes.ifEmpty { rawBoxes }
            } catch (t: Throwable) {
                // Mitigation: on any unexpected spatial error, safely fallback to raw boxes
                runCatching {
                    android.util.Log.w(TAG, "Clustering fallback to raw boxes: [${t::class.java.simpleName}] ${t.message}")
                }
                rawBoxes
            }
        }
    }
}
