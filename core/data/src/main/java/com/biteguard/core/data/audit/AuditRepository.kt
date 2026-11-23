package com.biteguard.core.data.audit

import android.graphics.Bitmap
import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.ai.DefensiveVerdictParser
import com.biteguard.core.data.ai.MissingApiKeyException
import com.biteguard.core.data.db.AuditLogDataSource
import com.biteguard.core.data.fallback.LocalFallbackAuditEngine
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.PartialAuditVerdict
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.TelemetryEvent
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID

/**
 * FR-03, FR-04, FR-05, FR-06: Orchestrates Multimodal AI Label Audit,
 * 5000ms First-Token Timeout enforcement, Offline Rule-Based Fallback,
 * Encrypted History Persistence, and Telemetry Queuing.
 */
interface AuditRepository {
    val scanHistoryFlow: Flow<List<AuditVerdict>>
    val telemetryEventsFlow: Flow<List<TelemetryEvent>>

    fun performLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        frameBitmap: Bitmap? = null,
        retryAttempt: Int = 0
    ): Flow<StreamState<AuditVerdict>>

    suspend fun evaluateOfflineOnly(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        frameBitmap: Bitmap? = null
    ): AuditVerdict

    suspend fun submitFeedback(verdict: AuditVerdict, feedback: UserFeedback)
    suspend fun clearScanHistory()
}

class DefaultAuditRepository(
    private val aiStreamDataSource: AiStreamDataSource,
    private val auditLogDataSource: AuditLogDataSource,
    private val firstTokenTimeoutMs: Long = 5_000L
) : AuditRepository {

    override val scanHistoryFlow: Flow<List<AuditVerdict>> = auditLogDataSource.historyFlow
    override val telemetryEventsFlow: Flow<List<TelemetryEvent>> = auditLogDataSource.telemetryQueueFlow

    override fun performLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        frameBitmap: Bitmap?,
        retryAttempt: Int
    ): Flow<StreamState<AuditVerdict>> = channelFlow {
        val cleanText = rawExtractedText.trim()
        val thumbnailBytes = frameBitmap?.let { compressThumbnailInMemory(it) }

        if (cleanText.isBlank()) {
            send(
                StreamState.Error(
                    throwable = IllegalArgumentException("No ingredient text found in scan."),
                    cachedRawText = "",
                    errorKind = AuditErrorKind.NO_TEXT_DETECTED,
                    fallbackVerdict = null,
                    retryAttempt = retryAttempt
                )
            )
            return@channelFlow
        }

        // Progressive stage 1 (NFR-NT1)
        send(
            StreamState.Streaming(
                partialVerdict = PartialAuditVerdict(
                    accumulatedSummary = "",
                    partialSafetyLevel = null
                ),
                stageMessage = "Auditing with BiteGuard AI..."
            )
        )

        val startTimeMs = System.currentTimeMillis()
        var firstTokenTimeMs = 0L
        val accumulatedRaw = StringBuilder()

        try {
            coroutineScope {
                val tokenChannel = Channel<String>(Channel.UNLIMITED)
                val producerJob = launch {
                    try {
                        aiStreamDataSource.streamLabelAudit(cleanText, profile, frameBitmap)
                            .collect { token ->
                                tokenChannel.send(token)
                            }
                        tokenChannel.close()
                    } catch (t: Throwable) {
                        tokenChannel.close(t)
                    }
                }

                // FR-04: Enforce strict 5000ms first-token timeout
                val firstResult = try {
                    withTimeout(firstTokenTimeoutMs) {
                        tokenChannel.receiveCatching()
                    }
                } catch (timeout: TimeoutCancellationException) {
                    producerJob.cancel()
                    throw timeout
                }

                if (firstResult.isFailure || firstResult.isClosed) {
                    val cause = firstResult.exceptionOrNull()
                    producerJob.cancel()
                    throw (cause ?: IllegalStateException("Cloud stream closed before first token"))
                } else {
                    val firstChunk = firstResult.getOrThrow()
                    firstTokenTimeMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                    accumulatedRaw.append(firstChunk)
                    val (partialLevel, partialSummary) = DefensiveVerdictParser.parseIncremental(accumulatedRaw.toString())
                    send(
                        StreamState.Streaming(
                            partialVerdict = PartialAuditVerdict(
                                accumulatedSummary = partialSummary,
                                partialSafetyLevel = partialLevel
                            ),
                            stageMessage = "Streaming verdict from BiteGuard AI..."
                        )
                    )
                }

                // Consume remaining tokens
                for (chunk in tokenChannel) {
                    accumulatedRaw.append(chunk)
                    val (partialLevel, partialSummary) = DefensiveVerdictParser.parseIncremental(accumulatedRaw.toString())
                    send(
                        StreamState.Streaming(
                            partialVerdict = PartialAuditVerdict(
                                accumulatedSummary = partialSummary,
                                partialSafetyLevel = partialLevel
                            ),
                            stageMessage = "Streaming verdict from BiteGuard AI..."
                        )
                    )
                }
            }

            val totalDurationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
            val parsed = DefensiveVerdictParser.parseFinal(accumulatedRaw.toString())

            // Cross-verify with local rule engine so critical life-threatening allergens are never missed
            val localFallback = LocalFallbackAuditEngine.evaluateOffline(
                rawText = cleanText,
                profile = profile,
                snapshotThumbnailBytes = thumbnailBytes,
                elapsedMs = totalDurationMs
            )

            if (parsed.cleanSummary.isBlank() && parsed.safetyLevel == null) {
                throw IllegalStateException("Malformed or empty AI stream output")
            }

            val mergedFlags = (parsed.flaggedIngredients + localFallback.flaggedIngredients)
                .distinctBy { it.name.lowercase() }

            val resolvedSafetyLevel = when {
                parsed.safetyLevel == SafetyLevel.DANGER ||
                    localFallback.safetyLevel == SafetyLevel.DANGER ||
                    mergedFlags.any { it.severity == Severity.HIGH } -> SafetyLevel.DANGER
                parsed.safetyLevel == SafetyLevel.CAUTION ||
                    localFallback.safetyLevel == SafetyLevel.CAUTION ||
                    mergedFlags.isNotEmpty() -> SafetyLevel.CAUTION
                else -> parsed.safetyLevel ?: SafetyLevel.SAFE
            }

            val finalVerdict = AuditVerdict(
                id = UUID.randomUUID().toString(),
                safetyLevel = resolvedSafetyLevel,
                summary = parsed.cleanSummary.ifBlank { localFallback.summary },
                flaggedIngredients = mergedFlags,
                rawExtractedText = cleanText,
                timestamp = Instant.now(),
                isOfflineFallback = false,
                productName = LocalFallbackAuditEngine.inferProductName(cleanText),
                snapshotThumbnailBytes = thumbnailBytes,
                ttftMs = firstTokenTimeMs,
                totalStreamDurationMs = totalDurationMs
            )

            auditLogDataSource.insertVerdict(finalVerdict)
            send(StreamState.Success(finalVerdict))
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
            handleFallbackOnFailure(
                cause = ce,
                errorKind = AuditErrorKind.NETWORK_TIMEOUT,
                cleanText = cleanText,
                profile = profile,
                thumbnailBytes = thumbnailBytes,
                startTimeMs = startTimeMs,
                retryAttempt = retryAttempt,
                emitState = { send(it) }
            )
        } catch (missingKey: MissingApiKeyException) {
            handleFallbackOnFailure(
                cause = missingKey,
                errorKind = AuditErrorKind.API_KEY_MISSING,
                cleanText = cleanText,
                profile = profile,
                thumbnailBytes = thumbnailBytes,
                startTimeMs = startTimeMs,
                retryAttempt = retryAttempt,
                emitState = { send(it) }
            )
        } catch (t: Throwable) {
            val kind = if (t.message?.contains("Malformed", ignoreCase = true) == true) {
                AuditErrorKind.MALFORMED_AI_RESPONSE
            } else {
                AuditErrorKind.NETWORK_TIMEOUT
            }
            handleFallbackOnFailure(
                cause = t,
                errorKind = kind,
                cleanText = cleanText,
                profile = profile,
                thumbnailBytes = thumbnailBytes,
                startTimeMs = startTimeMs,
                retryAttempt = retryAttempt,
                emitState = { send(it) }
            )
        }
    }

    private suspend fun handleFallbackOnFailure(
        cause: Throwable,
        errorKind: AuditErrorKind,
        cleanText: String,
        profile: UserDietaryProfile,
        thumbnailBytes: ByteArray?,
        startTimeMs: Long,
        retryAttempt: Int,
        emitState: suspend (StreamState<AuditVerdict>) -> Unit
    ) {
        val elapsed = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
        val fallbackVerdict = LocalFallbackAuditEngine.evaluateOffline(
            rawText = cleanText,
            profile = profile,
            snapshotThumbnailBytes = thumbnailBytes,
            elapsedMs = elapsed
        )
        auditLogDataSource.insertVerdict(fallbackVerdict)
        emitState(
            StreamState.Error(
                throwable = cause,
                cachedRawText = cleanText,
                errorKind = errorKind,
                fallbackVerdict = fallbackVerdict,
                retryAttempt = retryAttempt
            )
        )
    }

    override suspend fun evaluateOfflineOnly(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        frameBitmap: Bitmap?
    ): AuditVerdict {
        val thumbnailBytes = frameBitmap?.let { compressThumbnailInMemory(it) }
        val verdict = LocalFallbackAuditEngine.evaluateOffline(
            rawText = rawExtractedText,
            profile = profile,
            snapshotThumbnailBytes = thumbnailBytes
        )
        auditLogDataSource.insertVerdict(verdict)
        return verdict
    }

    override suspend fun submitFeedback(verdict: AuditVerdict, feedback: UserFeedback) {
        auditLogDataSource.updateVerdictFeedback(verdict.id, feedback)
        val event = TelemetryEvent(
            id = UUID.randomUUID().toString(),
            promptExecutionId = verdict.id,
            ttftMs = verdict.ttftMs,
            totalStreamDurationMs = verdict.totalStreamDurationMs,
            rating = feedback,
            isOfflineFallback = verdict.isOfflineFallback,
            timestamp = Instant.now(),
            synced = false
        )
        auditLogDataSource.enqueueTelemetry(event)
    }

    override suspend fun clearScanHistory() {
        auditLogDataSource.clearHistory()
    }

    /**
     * NFR-NT3 & FR-05: Downsamples captured frame in-memory to a small 140px WebP thumbnail
     * stored exclusively inside the encrypted DB — never written to unencrypted disk files.
     */
    private fun compressThumbnailInMemory(bitmap: Bitmap): ByteArray? = runCatching {
        val targetSize = 140
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val width = if (ratio >= 1f) targetSize else (targetSize * ratio).toInt().coerceAtLeast(1)
        val height = if (ratio >= 1f) (targetSize / ratio).toInt().coerceAtLeast(1) else targetSize
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        ByteArrayOutputStream().use { bos ->
            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, bos)
            bos.toByteArray()
        }
    }.getOrNull()
}
