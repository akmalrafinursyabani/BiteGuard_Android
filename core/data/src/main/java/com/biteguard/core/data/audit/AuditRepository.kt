package com.biteguard.core.data.audit

import android.util.Log
import com.biteguard.core.data.BuildConfig
import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.ai.DefensiveVerdictParser
import com.biteguard.core.data.ai.MissingApiKeyException
import com.biteguard.core.data.db.AuditLogDataSource
import com.biteguard.core.domain.engine.OfflineAuditEngine
import com.biteguard.core.domain.engine.RuleBasedOfflineAuditEngine
import com.biteguard.core.domain.repository.AuditRepository
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.UUID

/**
 * FR-03, FR-04, FR-05, FR-06: Data-layer implementation of [AuditRepository].
 * Coordinates multimodal cloud streaming, first-token timeout fallback via [OfflineAuditEngine],
 * encrypted history persistence, and telemetry queuing.
 */
class DefaultAuditRepository(
    private val aiStreamDataSource: AiStreamDataSource,
    private val auditLogDataSource: AuditLogDataSource,
    private val offlineAuditEngine: OfflineAuditEngine = RuleBasedOfflineAuditEngine(),
    private val firstTokenTimeoutMs: Long = 15_000L,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AuditRepository {

    override val scanHistoryFlow: Flow<List<AuditVerdict>> = auditLogDataSource.historyFlow
    override val telemetryEventsFlow: Flow<List<TelemetryEvent>> = auditLogDataSource.telemetryQueueFlow

    override fun performLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        imageJpegBytes: ByteArray?,
        thumbnailBytes: ByteArray?,
        retryAttempt: Int
    ): Flow<StreamState<AuditVerdict>> = channelFlow {
        val cleanText = rawExtractedText.trim()
        debugLog("performLabelAudit started | retryAttempt=$retryAttempt | textLen=${cleanText.length} | allergens=${profile.selectedAllergens.size} | goals=${profile.healthGoals.size} | hasImage=${imageJpegBytes != null}")

        if (cleanText.isBlank()) {
            debugWarn("performLabelAudit aborted: cleanText is blank (NO_TEXT_DETECTED)")
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
                        aiStreamDataSource.streamLabelAudit(cleanText, profile, imageJpegBytes)
                            .collect { token ->
                                tokenChannel.send(token)
                            }
                        tokenChannel.close()
                    } catch (t: Throwable) {
                        debugError("Producer stream threw exception: [${t::class.java.simpleName}] ${t.message}", t)
                        tokenChannel.close(t)
                    }
                }

                // FR-04: Enforce first-token timeout before falling back to local engine
                val firstResult = try {
                    withTimeout(firstTokenTimeoutMs) {
                        tokenChannel.receiveCatching()
                    }
                } catch (timeout: TimeoutCancellationException) {
                    debugWarn("First-token timeout (${firstTokenTimeoutMs}ms) exceeded! Cancelling cloud stream and switching to offline fallback.")
                    producerJob.cancel()
                    throw timeout
                }

                if (firstResult.isFailure || firstResult.isClosed) {
                    val cause = firstResult.exceptionOrNull()
                    debugError("Stream closed before first token arrived. Cause: [${cause?.javaClass?.simpleName}] ${cause?.message}", cause)
                    producerJob.cancel()
                    throw (cause ?: IllegalStateException("Cloud stream closed before first token"))
                } else {
                    val firstChunk = firstResult.getOrThrow()
                    firstTokenTimeMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                    debugLog("First token received in ${firstTokenTimeMs}ms | firstChunk='${firstChunk.take(60)}'")
                    accumulatedRaw.append(firstChunk)
                    val (partialLevel, partialSummary) = withContext(defaultDispatcher) {
                        DefensiveVerdictParser.parseIncremental(accumulatedRaw.toString())
                    }
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
                    val (partialLevel, partialSummary) = withContext(defaultDispatcher) {
                        DefensiveVerdictParser.parseIncremental(accumulatedRaw.toString())
                    }
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
            debugLog("Cloud stream finished in ${totalDurationMs}ms | rawPayloadLen=${accumulatedRaw.length}")

            val (parsed, localFallback) = withContext(defaultDispatcher) {
                val parsedOutput = DefensiveVerdictParser.parseFinal(accumulatedRaw.toString())
                // Cross-verify with local rule engine so critical life-threatening allergens are never missed
                val localEval = offlineAuditEngine.evaluateOffline(
                    rawText = cleanText,
                    profile = profile,
                    snapshotThumbnailBytes = thumbnailBytes,
                    elapsedMs = totalDurationMs
                )
                parsedOutput to localEval
            }

            if (parsed.cleanSummary.isBlank() && parsed.safetyLevel == null) {
                debugError("Malformed AI stream output! Raw payload was:\n$accumulatedRaw")
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
                productName = offlineAuditEngine.inferProductName(cleanText),
                snapshotThumbnailBytes = thumbnailBytes,
                ttftMs = firstTokenTimeMs,
                totalStreamDurationMs = totalDurationMs
            )

            debugLog("Final AI verdict resolved: safetyLevel=$resolvedSafetyLevel | flagsCount=${mergedFlags.size} | ttft=${firstTokenTimeMs}ms | total=${totalDurationMs}ms")
            auditLogDataSource.insertVerdict(finalVerdict)
            send(StreamState.Success(finalVerdict))
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) {
                debugLog("performLabelAudit coroutine cancelled normally")
                throw ce
            }
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
            val msg = t.message.orEmpty()
            val kind = when {
                msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                    msg.contains("API key not valid", ignoreCase = true) ||
                    msg.contains("API_KEY", ignoreCase = true) ||
                    t.javaClass.simpleName.contains("InvalidAPIKey", ignoreCase = true) -> {
                    AuditErrorKind.API_KEY_MISSING
                }
                msg.contains("Malformed", ignoreCase = true) ||
                    t.javaClass.simpleName.contains("Serialization", ignoreCase = true) -> {
                    AuditErrorKind.MALFORMED_AI_RESPONSE
                }
                else -> {
                    AuditErrorKind.NETWORK_TIMEOUT
                }
            }
            debugError("Cloud audit failed -> mapped to errorKind=$kind | exception=[${t::class.java.name}] $msg", t)
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
        val fallbackVerdict = withContext(defaultDispatcher) {
            offlineAuditEngine.evaluateOffline(
                rawText = cleanText,
                profile = profile,
                snapshotThumbnailBytes = thumbnailBytes,
                elapsedMs = elapsed
            )
        }
        debugWarn("Emitting StreamState.Error with offline fallback verdict: errorKind=$errorKind | fallbackLevel=${fallbackVerdict.safetyLevel} | flags=${fallbackVerdict.flaggedIngredients.size} | cause=${cause.message}")
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
        thumbnailBytes: ByteArray?
    ): AuditVerdict {
        debugLog("evaluateOfflineOnly invoked (textLen=${rawExtractedText.length})")
        val verdict = withContext(defaultDispatcher) {
            offlineAuditEngine.evaluateOffline(
                rawText = rawExtractedText,
                profile = profile,
                snapshotThumbnailBytes = thumbnailBytes
            )
        }
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

    override suspend fun deleteVerdicts(ids: List<String>) {
        auditLogDataSource.deleteVerdicts(ids)
    }

    companion object {
        private const val TAG = "BiteGuardAI_AuditRepo"

        private fun debugLog(message: String) {
            if (BuildConfig.DEBUG) {
                runCatching { Log.d(TAG, message) }
            }
        }

        private fun debugWarn(message: String) {
            if (BuildConfig.DEBUG) {
                runCatching { Log.w(TAG, message) }
            }
        }

        private fun debugError(message: String, throwable: Throwable? = null) {
            if (BuildConfig.DEBUG) {
                runCatching {
                    if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
                }
            }
        }
    }
}
