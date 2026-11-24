package com.biteguard.core.model

import java.time.Instant

/**
 * Represents the user's active health goals and allergen restrictions.
 */
data class UserDietaryProfile(
    val userName: String = "",
    val selectedAllergens: Set<Allergen> = emptySet(),
    val healthGoals: Set<HealthGoal> = emptySet(),
    val lastUpdated: Instant = Instant.now(),
    val highContrastMode: Boolean = false,
    val selectedLanguage: String = "SYSTEM"
)

enum class Allergen(val displayName: String, val examples: String) {
    PEANUTS("Peanuts", "Arachis oil, groundnuts, peanut flour"),
    TREE_NUTS("Tree Nuts", "Almonds, cashews, walnuts, praline"),
    DAIRY("Dairy", "Casein, whey, lactose, ghee, lactalbumin"),
    GLUTEN("Gluten", "Wheat, barley, rye, malt, triticale, seitan"),
    SHELLFISH("Shellfish", "Shrimp, crab, lobster, prawn, krill"),
    SOY("Soy", "Soy lecithin, tofu, edamame, glycine max"),
    EGGS("Eggs", "Albumin, globulin, lysozyme, mayonnaise"),
    FISH("Fish", "Anchovy, fish sauce, surimi, omega-3 fish oil"),
    SESAME("Sesame", "Tahini, sesamum indicum, gingelly oil, Halvah")
}

enum class HealthGoal(val displayName: String, val description: String) {
    LOW_SUGAR("Low Sugar", "Flags high-fructose corn syrup, maltodextrin, sucrose, dextrose"),
    LOW_SODIUM("Low Sodium", "Flags MSG, disodium inosinate, high salt & sodium compounds"),
    HALAL("Halal", "Flags pork/lard derivatives, gelatin, non-halal alcohol/enzymes"),
    VEGAN("Vegan", "Flags all animal-derived ingredients including dairy, eggs, honey, carmine"),
    VEGETARIAN("Vegetarian", "Flags meat, poultry, fish, lard, tallow, animal rennet"),
    KETO_FRIENDLY("Keto-Friendly", "Flags starches, refined grains, added sugars, high-carb fillers")
}

/**
 * Evaluated verdict returned after auditing an ingredient list.
 */
data class AuditVerdict(
    val id: String,
    val safetyLevel: SafetyLevel,
    val summary: String,
    val flaggedIngredients: List<FlaggedIngredient>,
    val rawExtractedText: String,
    val timestamp: Instant,
    val isOfflineFallback: Boolean = false,
    val productName: String = "Scanned Package Label",
    val snapshotThumbnailBytes: ByteArray? = null,
    val ttftMs: Long = 0L,
    val totalStreamDurationMs: Long = 0L,
    val userFeedback: UserFeedback? = null
)

enum class SafetyLevel(val label: String, val shortActionTitle: String) {
    SAFE("SAFE", "Safe for Your Profile"),
    CAUTION("CAUTION", "Review Flagged Ingredients"),
    DANGER("DANGER", "Do Not Consume — Conflict Detected")
}

/**
 * Specific ingredient flagged during evaluation.
 */
data class FlaggedIngredient(
    val name: String,
    val riskReason: String,
    val severity: Severity
)

enum class Severity(val label: String) {
    HIGH("High Risk"),
    MEDIUM("Moderate Risk"),
    LOW("Low Risk / Advisory")
}

/**
 * Actionable failure categorization to help users decide their next step.
 */
enum class AuditErrorKind {
    NETWORK_TIMEOUT,
    NO_TEXT_DETECTED,
    MALFORMED_AI_RESPONSE,
    API_KEY_MISSING,
    CAMERA_UNAVAILABLE,
    UNKNOWN
}

/**
 * Sealed UI/Domain state wrapper for real-time streaming analysis.
 */
sealed interface StreamState<out T> {
    data object Idle : StreamState<Nothing>

    data class ScanningEdge(
        val detectedTextBlocks: Int,
        val isReadyToScan: Boolean = false,
        val isThrottled: Boolean = false,
        val previewSnippet: String = ""
    ) : StreamState<Nothing>

    data class Streaming(
        val partialVerdict: PartialAuditVerdict,
        val stageMessage: String = "Auditing with BiteGuard AI..."
    ) : StreamState<Nothing>

    data class Success<out T>(
        val data: T
    ) : StreamState<T>

    data class Error(
        val throwable: Throwable,
        val cachedRawText: String? = null,
        val errorKind: AuditErrorKind = AuditErrorKind.UNKNOWN,
        val fallbackVerdict: AuditVerdict? = null,
        val retryAttempt: Int = 0
    ) : StreamState<Nothing>
}

/**
 * Intermediate domain representation during token-by-token streaming.
 */
data class PartialAuditVerdict(
    val accumulatedSummary: String = "",
    val partialSafetyLevel: SafetyLevel? = null
)

/**
 * Binary user feedback & telemetry payload (FR-06).
 */
enum class UserFeedback {
    THUMBS_UP,
    THUMBS_DOWN
}

data class TelemetryEvent(
    val id: String,
    val promptExecutionId: String,
    val ttftMs: Long,
    val totalStreamDurationMs: Long,
    val rating: UserFeedback,
    val isOfflineFallback: Boolean,
    val timestamp: Instant = Instant.now(),
    val synced: Boolean = false
)
