package com.biteguard.core.data.ai

import android.graphics.Bitmap
import com.biteguard.core.data.BuildConfig
import com.biteguard.core.model.FlaggedIngredient
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.UserDietaryProfile
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Abstraction over Gemini multimodal streaming API (FR-03) so repositories can be unit-tested
 * with simulated token streams, network timeouts, and malformed payloads (NFR-T3, NFR-T6).
 */
interface AiStreamDataSource {
    val isApiKeyConfigured: Boolean
    fun streamLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        frameBitmap: Bitmap?
    ): Flow<String>
}

class GeminiStreamDataSource(
    private val apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY }
) : AiStreamDataSource {

    override val isApiKeyConfigured: Boolean
        get() = apiKeyProvider().isNotBlank()

    override fun streamLabelAudit(
        rawExtractedText: String,
        profile: UserDietaryProfile,
        frameBitmap: Bitmap?
    ): Flow<String> = flow {
        val key = apiKeyProvider().trim()
        if (key.isBlank()) {
            throw MissingApiKeyException("GEMINI_API_KEY is not configured in local.properties")
        }

        val model = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = key,
            generationConfig = generationConfig {
                temperature = 0.1f
            }
        )

        val prompt = buildAuditPrompt(rawExtractedText, profile)
        val inputContent = content {
            if (frameBitmap != null) {
                image(frameBitmap)
            }
            text(prompt)
        }

        model.generateContentStream(inputContent).collect { chunk ->
            val piece = chunk.text
            if (!piece.isNullOrEmpty()) {
                emit(piece)
            }
        }
    }

    companion object {
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
        val verdictRegex = Regex("VERDICT\\s*:\\s*\\*\\*?(SAFE|CAUTION|DANGER)\\*\\*?", RegexOption.IGNORE_CASE)
        verdictRegex.find(raw)?.groupValues?.getOrNull(1)?.uppercase()?.let { matched ->
            return runCatching { SafetyLevel.valueOf(matched) }.getOrNull()
        }
        val jsonSafetyRegex = Regex("\"safetyLevel\"\\s*:\\s*\"(SAFE|CAUTION|DANGER)\"", RegexOption.IGNORE_CASE)
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
        val summaryMatch = Regex("SUMMARY\\s*:\\s*(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(raw)
        val afterSummaryMarker = summaryMatch?.groupValues?.getOrNull(1)
            ?: raw.replaceFirst(Regex("^\\s*VERDICT\\s*:.*\\r?\\n?", RegexOption.IGNORE_CASE), "")

        val beforeFlagsMarker = afterSummaryMarker
            .replace(Regex("FLAGS_JSON\\s*:.*", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .substringBefore("```json")
            .substringBefore("[{")

        return beforeFlagsMarker
            .replace("```", "")
            .replace("**", "")
            .trim()
    }

    private fun extractFlaggedIngredients(raw: String): List<FlaggedIngredient> {
        val flagsMatch = Regex("FLAGS_JSON\\s*:\\s*(.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(raw)
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
