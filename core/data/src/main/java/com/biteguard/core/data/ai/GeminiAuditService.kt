package com.biteguard.core.data.ai

import android.util.Log
import com.biteguard.core.data.BuildConfig
import com.biteguard.core.model.FlaggedIngredient
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.UserDietaryProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Abstraction over Gemini multimodal streaming API (FR-03) so repositories can be unit-tested
 * with simulated token streams, network timeouts, and malformed payloads (NFR-T3, NFR-T6).
 */
interface AiStreamDataSource {
    val isApiKeyConfigured: Boolean
    fun streamLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        imageJpegBytes: ByteArray? = null
    ): Flow<String>
}

/**
 * Direct SSE (`streamGenerateContent?alt=sse`) client for `generativelanguage.googleapis.com/v1beta`.
 *
 * Why direct REST SSE instead of the archived `com.google.ai.client.generativeai:0.9.0` wrapper:
 * 1. Native support for Google AI Studio's new `AQ.` Authentication Keys as well as legacy `AIza` keys.
 * 2. Resilient to `gemini-2.5-flash` thinking parts (`"thought": true`) and new `usageMetadata` fields
 *    that crash the archived 0.9.0 SDK with `SerializationException` / `MissingFieldException`.
 * 3. Transparently surfaces real HTTP error codes and messages from the Gemini endpoint in Logcat.
 */
class GeminiStreamDataSource(
    private val apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val modelName: String = DEFAULT_MODEL_NAME
) : AiStreamDataSource {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override val isApiKeyConfigured: Boolean
        get() = apiKeyProvider().isNotBlank()

    override fun streamLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        imageJpegBytes: ByteArray?
    ): Flow<String> = flow {
        val key = apiKeyProvider().trim()
        val maskedPrefix = if (key.length >= 6) "${key.take(6)}..." else "(empty)"
        debugLog("Starting streamLabelAudit | model=$modelName | keyPrefix=$maskedPrefix (len=${key.length}) | textLen=${rawExtractedText.length} | hasImage=${imageJpegBytes != null}")

        if (key.isBlank()) {
            debugError("GEMINI_API_KEY is blank in BuildConfig / local.properties!")
            throw MissingApiKeyException("GEMINI_API_KEY is not configured in local.properties")
        }

        val prompt = buildAuditPrompt(rawExtractedText, profile)
        val requestBodyJson = buildRequestJson(prompt, imageJpegBytes)

        val candidateModels = listOf(modelName) + FALLBACK_MODEL_NAMES.filter { it != modelName }
        var lastException: Throwable? = null

        for ((attemptIdx, activeModel) in candidateModels.withIndex()) {
            val endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$activeModel:streamGenerateContent?alt=sse"
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("x-goog-api-key", key)
            }

            var chunkIndex = 0
            try {
                connection.outputStream.use { os ->
                    os.write(requestBodyJson.toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                }

                val responseCode = connection.responseCode
                debugLog("Gemini HTTP responseCode=$responseCode for model=$activeModel (attempt=${attemptIdx + 1}/${candidateModels.size})")

                if (responseCode !in 200..299) {
                    val errorBody = runCatching {
                        (connection.errorStream ?: connection.inputStream)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                    }.getOrNull().orEmpty()

                    debugError("Gemini HTTP $responseCode error payload for model=$activeModel: $errorBody")
                    if (responseCode == 400 && (errorBody.contains("API_KEY_INVALID", ignoreCase = true) || errorBody.contains("API key not valid", ignoreCase = true))) {
                        throw MissingApiKeyException("Google AI Studio rejected GEMINI_API_KEY (HTTP 400 API_KEY_INVALID): $errorBody")
                    }
                    if (responseCode == 401 || responseCode == 403) {
                        throw MissingApiKeyException("Google AI Studio authentication error (HTTP $responseCode): $errorBody")
                    }
                    if (responseCode == 404 && attemptIdx < candidateModels.lastIndex) {
                        debugLog("Model '$activeModel' returned 404 NOT_FOUND; falling back to '${candidateModels[attemptIdx + 1]}'")
                        lastException = IllegalStateException("Gemini API HTTP 404 on $activeModel: ${errorBody.take(280)}")
                        continue
                    }
                    throw IllegalStateException("Gemini API HTTP $responseCode: ${errorBody.take(280)}")
                }

                BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: continue
                        if (!trimmed.startsWith("data:")) continue
                        val payload = trimmed.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue

                        val extractedPiece = extractTextFromSseChunk(payload)
                        if (!extractedPiece.isNullOrEmpty()) {
                            chunkIndex++
                            debugLog("Received SSE stream chunk #$chunkIndex from $activeModel (${extractedPiece.length} chars)")
                            emit(extractedPiece)
                        }
                    }
                }
                debugLog("Completed SSE generateContentStream successfully with model=$activeModel ($chunkIndex total chunks)")
                lastException = null
                break
            } catch (t: Throwable) {
                debugError("Gemini stream failed on model=$activeModel after $chunkIndex chunk(s): [${t::class.java.simpleName}] ${t.message}", t)
                if (chunkIndex == 0 && t is java.net.SocketTimeoutException && attemptIdx < candidateModels.lastIndex) {
                    debugLog("SocketTimeoutException on '$activeModel' before any chunk arrived; trying fallback model '${candidateModels[attemptIdx + 1]}'")
                    lastException = t
                    continue
                }
                throw t
            } finally {
                runCatching { connection.disconnect() }
            }
        }

        if (lastException != null) {
            throw lastException
        }
    }.flowOn(ioDispatcher)

    private fun buildRequestJson(prompt: String, imageJpegBytes: ByteArray?): String {
        val partsArray = buildJsonArray {
            if (imageJpegBytes != null && imageJpegBytes.isNotEmpty()) {
                val base64Jpeg = Base64.getEncoder().encodeToString(imageJpegBytes)
                add(
                    buildJsonObject {
                        put(
                            "inline_data",
                            buildJsonObject {
                                put("mime_type", "image/jpeg")
                                put("data", base64Jpeg)
                            }
                        )
                    }
                )
            }
            add(
                buildJsonObject {
                    put("text", prompt)
                }
            )
        }

        val root = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", partsArray)
                        }
                    )
                }
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", 0.1)
                }
            )
        }
        return root.toString()
    }

    /**
     * Parses a single SSE JSON event from `streamGenerateContent?alt=sse`, skipping internal
     * `"thought": true` reasoning parts so only user-facing verdict text is emitted.
     */
    private fun extractTextFromSseChunk(jsonPayload: String): String? = runCatching {
        val rootObj = lenientJson.parseToJsonElement(jsonPayload).jsonObject
        val candidates = rootObj["candidates"] as? JsonArray ?: return@runCatching null
        val firstCandidate = candidates.firstOrNull() as? JsonObject ?: return@runCatching null
        val contentObj = firstCandidate["content"] as? JsonObject ?: return@runCatching null
        val parts = contentObj["parts"] as? JsonArray ?: return@runCatching null

        val sb = StringBuilder()
        for (partElement in parts) {
            val partObj = partElement as? JsonObject ?: continue
            val isThought = (partObj["thought"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true
            if (isThought) continue
            val textPiece = (partObj["text"] as? JsonPrimitive)?.contentOrNull
            if (!textPiece.isNullOrEmpty()) {
                sb.append(textPiece)
            }
        }
        sb.toString().takeIf { it.isNotEmpty() }
    }.getOrNull()

    companion object {
        private const val TAG = "BiteGuardAI_Gemini"
        const val DEFAULT_MODEL_NAME = "gemini-3.8-flash"
        val FALLBACK_MODEL_NAMES = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash"
        )

        private fun debugLog(message: String) {
            if (BuildConfig.DEBUG) {
                runCatching { Log.d(TAG, message) }
            }
        }

        private fun debugError(message: String, throwable: Throwable? = null) {
            if (BuildConfig.DEBUG) {
                runCatching {
                    if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
                }
            }
        }
        fun buildAuditPrompt(
            rawExtractedText: String,
            profile: UserDietaryProfile
        ): String {
            val allergensText = if (profile.selectedAllergens.isEmpty()) {
                "None selected"
            } else {
                profile.selectedAllergens.joinToString(", ") { "${it.displayName} (${it.examples})" }
            }

            val goalsText = if (profile.healthGoals.isEmpty()) {
                "None selected"
            } else {
                profile.healthGoals.joinToString(", ") { "${it.displayName} (${it.description})" }
            }

            return """
                You are BiteGuard AI, a high-precision clinical food & allergen safety auditor.
                Analyze the following product ingredient text (and package photo if attached) against the user's dietary profile.
                
                USER DIETARY PROFILE:
                - Severe Allergens to Avoid: $allergensText
                - Active Health & Lifestyle Goals: $goalsText
                
                EXTRACTED OCR TEXT FROM PACKAGE:
                $rawExtractedText
                
                STRICT OUTPUT FORMAT (follow this exact 3-part structure so the mobile app can stream the summary live):
                VERDICT: [SAFE or CAUTION or DANGER]
                SUMMARY: [Write 2-3 clear, plain-language sentences explaining the verdict for a shopper in a grocery aisle. Do not use markdown code blocks in the summary.]
                FLAGS_JSON:
                [{"name":"Ingredient Name","riskReason":"Clear plain-language reason why it conflicts with the profile","severity":"HIGH|MEDIUM|LOW"}]
                
                RULES:
                1. Use DANGER if ANY selected Allergen or its chemical derivative (e.g., arachis oil, casein, whey, albumin, tahini) is present.
                2. Use CAUTION if a cross-contamination 'May contain' warning matches an allergen, OR if an ingredient conflicts with a Health Goal (e.g., maltodextrin/HFCS for Low Sugar, disodium inosinate/MSG for Low Sodium, gelatin/lard for Halal/Vegan).
                3. Use SAFE only if no conflicts exist. If SAFE, output FLAGS_JSON: []
            """.trimIndent()
        }
    }
}

class MissingApiKeyException(message: String) : IllegalStateException(message)

/**
 * NFR-T3: Defensive streaming parser & sanitizer for non-deterministic LLM outputs.
 * Extracts incremental `PartialAuditVerdict` during streaming and sanitizes malformed Markdown
 * or truncated JSON payloads when the stream completes.
 */
object DefensiveVerdictParser {

    @Serializable
    private data class RawFlagDto(
        val name: String = "",
        val riskReason: String = "",
        val severity: String = "MEDIUM"
    )

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val verdictRegex = Regex("VERDICT\\s*:\\s*\\*\\*?(SAFE|CAUTION|DANGER)\\*\\*?", RegexOption.IGNORE_CASE)
    private val jsonSafetyRegex = Regex("\"safetyLevel\"\\s*:\\s*\"(SAFE|CAUTION|DANGER)\"", RegexOption.IGNORE_CASE)
    private val summaryRegex = Regex("SUMMARY\\s*:\\s*(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val leadingVerdictLineRegex = Regex("^\\s*VERDICT\\s*:.*\\r?\\n?", RegexOption.IGNORE_CASE)
    private val flagsJsonStripRegex = Regex("FLAGS_JSON\\s*:.*", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val flagsJsonExtractRegex = Regex("FLAGS_JSON\\s*:\\s*(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    data class ParsedLlmOutput(
        val safetyLevel: SafetyLevel?,
        val cleanSummary: String,
        val flaggedIngredients: List<FlaggedIngredient>
    )

    fun parseIncremental(accumulatedRaw: String): Pair<SafetyLevel?, String> {
        val level = extractSafetyLevel(accumulatedRaw)
        val summary = extractCleanSummary(accumulatedRaw)
        return level to summary
    }

    fun parseFinal(accumulatedRaw: String): ParsedLlmOutput {
        val level = extractSafetyLevel(accumulatedRaw)
        val summary = extractCleanSummary(accumulatedRaw)
        val flags = extractFlaggedIngredients(accumulatedRaw)
        return ParsedLlmOutput(
            safetyLevel = level,
            cleanSummary = summary,
            flaggedIngredients = flags
        )
    }

    private fun extractSafetyLevel(raw: String): SafetyLevel? {
        verdictRegex.find(raw)?.groupValues?.getOrNull(1)?.uppercase()?.let { matched ->
            return runCatching { SafetyLevel.valueOf(matched) }.getOrNull()
        }
        jsonSafetyRegex.find(raw)?.groupValues?.getOrNull(1)?.uppercase()?.let { matched ->
            return runCatching { SafetyLevel.valueOf(matched) }.getOrNull()
        }
        val firstLine = raw.lineSequence().firstOrNull()?.uppercase().orEmpty()
        return when {
            "DANGER" in firstLine -> SafetyLevel.DANGER
            "CAUTION" in firstLine -> SafetyLevel.CAUTION
            "SAFE" in firstLine -> SafetyLevel.SAFE
            else -> null
        }
    }

    private fun extractCleanSummary(raw: String): String {
        val summaryMatch = summaryRegex.find(raw)
        val afterSummaryMarker = summaryMatch?.groupValues?.getOrNull(1)
            ?: raw.replaceFirst(leadingVerdictLineRegex, "")

        val beforeFlagsMarker = afterSummaryMarker
            .replace(flagsJsonStripRegex, "")
            .substringBefore("```json")
            .substringBefore("[{")

        return beforeFlagsMarker
            .replace("```", "")
            .replace("**", "")
            .trim()
    }

    private fun extractFlaggedIngredients(raw: String): List<FlaggedIngredient> {
        val flagsMatch = flagsJsonExtractRegex.find(raw)
        val jsonSection = flagsMatch?.groupValues?.getOrNull(1) ?: raw

        val startBracket = jsonSection.indexOf('[')
        if (startBracket == -1) return emptyList()

        val endBracket = jsonSection.lastIndexOf(']')
        val candidateJson = if (endBracket > startBracket) {
            jsonSection.substring(startBracket, endBracket + 1)
        } else {
            // Handle truncated JSON stream by closing the last complete object
            val sub = jsonSection.substring(startBracket)
            val lastCloseBrace = sub.lastIndexOf('}')
            if (lastCloseBrace != -1) {
                sub.substring(0, lastCloseBrace + 1) + "]"
            } else {
                "[]"
            }
        }

        val cleanedJson = candidateJson
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return runCatching {
            lenientJson.decodeFromString<List<RawFlagDto>>(cleanedJson)
                .filter { it.name.isNotBlank() }
                .map { dto ->
                    val severity = when (dto.severity.uppercase().trim()) {
                        "HIGH", "DANGER", "CRITICAL" -> Severity.HIGH
                        "LOW", "INFO", "ADVISORY" -> Severity.LOW
                        else -> Severity.MEDIUM
                    }
                    FlaggedIngredient(
                        name = dto.name.trim(),
                        riskReason = dto.riskReason.trim().ifBlank { "Flagged during ingredient audit." },
                        severity = severity
                    )
                }
        }.getOrDefault(emptyList())
    }
}
