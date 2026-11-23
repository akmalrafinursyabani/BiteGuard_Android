package com.biteguard.core.data.fallback

import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.FlaggedIngredient
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.UserDietaryProfile
import java.time.Instant
import java.util.UUID

/**
 * FR-04: Resilient Partial-Failure & Offline Fallback Engine.
 * Performs fast on-device rule-based chemical/synonym matching against MLKit extracted text
 * whenever cloud AI times out (> 5000ms), fails, or is offline.
 */
object LocalFallbackAuditEngine {

    private data class RuleMatch(
        val canonicalTerm: String,
        val matchedSubstring: String,
        val reason: String,
        val severity: Severity
    )

    private val allergenDictionaries: Map<Allergen, List<Pair<String, String>>> = mapOf(
        Allergen.PEANUTS to listOf(
            "peanut" to "Direct peanut allergen detected.",
            "arachis oil" to "Arachis oil is cold-pressed or refined peanut oil.",
            "arachis hypogaea" to "Scientific name for peanut.",
            "groundnut" to "Groundnut is a regional synonym for peanut.",
            "monkey nut" to "Synonym for peanut.",
            "beer nut" to "Contains whole peanuts."
        ),
        Allergen.TREE_NUTS to listOf(
            "almond" to "Tree nut allergen (Almond).",
            "cashew" to "Tree nut allergen (Cashew).",
            "walnut" to "Tree nut allergen (Walnut).",
            "pecan" to "Tree nut allergen (Pecan).",
            "hazelnut" to "Tree nut allergen (Hazelnut / Filbert).",
            "filbert" to "Synonym for hazelnut (Tree Nut).",
            "pistachio" to "Tree nut allergen (Pistachio).",
            "macadamia" to "Tree nut allergen (Macadamia).",
            "brazil nut" to "Tree nut allergen (Brazil nut).",
            "praline" to "Confection made from tree nuts.",
            "marzipan" to "Almond paste derivative (Tree Nut).",
            "gianduja" to "Contains hazelnut paste (Tree Nut)."
        ),
        Allergen.DAIRY to listOf(
            "milk" to "Direct dairy allergen detected.",
            "casein" to "Major milk protein derivative (Dairy).",
            "caseinate" to "Sodium/calcium caseinate is derived from cow's milk.",
            "whey" to "Hydrolyzed or sweet whey is a dairy protein byproduct.",
            "lactose" to "Milk sugar derivative (Dairy).",
            "lactalbumin" to "Milk protein derivative (Dairy).",
            "lactoglobulin" to "Whey protein component (Dairy).",
            "ghee" to "Clarified butter derived from dairy milk.",
            "butter" to "Dairy fat product.",
            "cream" to "Dairy cream derivative.",
            "cheese" to "Fermented dairy product.",
            "yogurt" to "Cultured dairy product."
        ),
        Allergen.GLUTEN to listOf(
            "wheat" to "Contains gluten protein (gliadin & glutenin).",
            "barley" to "Contains hordein gluten protein.",
            "rye" to "Contains secalin gluten protein.",
            "malt" to "Usually derived from malted barley (contains Gluten).",
            "brewer's yeast" to "Often a byproduct of malted barley brewing (Gluten risk).",
            "triticale" to "Wheat-rye hybrid grain containing gluten.",
            "spelt" to "Ancient wheat species containing gluten.",
            "semolina" to "Coarse durum wheat middlings (Gluten).",
            "farina" to "Milled wheat cereal (Gluten).",
            "seitan" to "Made from vital wheat gluten.",
            "gluten" to "Explicit gluten protein listed."
        ),
        Allergen.SHELLFISH to listOf(
            "shrimp" to "Crustacean shellfish allergen.",
            "prawn" to "Crustacean shellfish allergen.",
            "crab" to "Crustacean shellfish allergen.",
            "lobster" to "Crustacean shellfish allergen.",
            "crayfish" to "Crustacean shellfish allergen.",
            "krill" to "Crustacean shellfish allergen.",
            "shellfish" to "Explicit shellfish allergen listed.",
            "barnacle" to "Crustacean shellfish derivative."
        ),
        Allergen.SOY to listOf(
            "soy" to "Direct soy allergen detected.",
            "soya" to "Direct soy allergen detected.",
            "soybean" to "Direct soy allergen detected.",
            "edamame" to "Immature soybeans (Soy allergen).",
            "tofu" to "Coagulated soy milk curds.",
            "tempeh" to "Fermented whole soybeans.",
            "miso" to "Fermented soybean paste.",
            "glycine max" to "Botanical name for soybean.",
            "textured vegetable protein" to "TVP is predominantly manufactured from defatted soy flour."
        ),
        Allergen.EGGS to listOf(
            "egg" to "Direct egg allergen detected.",
            "albumin" to "Egg white protein derivative.",
            "albumen" to "Egg white component.",
            "globulin" to "Egg protein derivative.",
            "lysozyme" to "Enzyme typically extracted from egg whites.",
            "ovalbumin" to "Main protein found in egg white.",
            "ovomucin" to "Egg white glycoprotein.",
            "vitellin" to "Egg yolk protein derivative.",
            "mayonnaise" to "Emulsion traditionally containing egg yolk."
        ),
        Allergen.FISH to listOf(
            "fish" to "Direct fish allergen detected.",
            "anchovy" to "Finned fish allergen often hidden in sauces.",
            "anchovies" to "Finned fish allergen.",
            "cod" to "Finned fish allergen.",
            "salmon" to "Finned fish allergen.",
            "tuna" to "Finned fish allergen.",
            "tilapia" to "Finned fish allergen.",
            "surimi" to "Paste made from minced fish (pollock/hake).",
            "worcestershire" to "Traditionally fermented with anchovies (Fish allergen)."
        ),
        Allergen.SESAME to listOf(
            "sesame" to "Direct sesame seed/oil allergen.",
            "tahini" to "Ground sesame seed paste.",
            "sesamum indicum" to "Botanical name for sesame.",
            "gingelly" to "Gingelly oil is sesame oil.",
            "benne" to "Heirloom culinary term for sesame seeds.",
            "halvah" to "Confection made from tahini (sesame paste)."
        )
    )

    private val healthGoalDictionaries: Map<HealthGoal, List<Pair<String, String>>> = mapOf(
        HealthGoal.LOW_SUGAR to listOf(
            "high-fructose corn syrup" to "Concentrated added liquid sugar linked to glycemic spikes (conflicts with Low Sugar).",
            "high fructose corn syrup" to "Concentrated added liquid sugar (conflicts with Low Sugar).",
            "hfcs" to "High-fructose corn syrup (conflicts with Low Sugar).",
            "maltodextrin" to "Ultra-high glycemic index polysaccharide (GI 110, higher than table sugar).",
            "sucrose" to "Refined table sugar (conflicts with Low Sugar).",
            "dextrose" to "Simple glucose sugar derived from corn starch (conflicts with Low Sugar).",
            "corn syrup" to "Refined glucose syrup (conflicts with Low Sugar).",
            "fructose" to "Added crystalline fruit sugar (conflicts with Low Sugar).",
            "cane sugar" to "Added refined sugar (conflicts with Low Sugar).",
            "invert sugar" to "Liquid mixture of glucose and fructose (conflicts with Low Sugar).",
            "maltose" to "High-glycemic disaccharide sugar (conflicts with Low Sugar)."
        ),
        HealthGoal.LOW_SODIUM to listOf(
            "monosodium glutamate" to "Sodium salt of glutamic acid (contributes hidden sodium).",
            "disodium inosinate" to "E631 sodium-based flavor enhancer (conflicts with Low Sodium).",
            "disodium guanylate" to "E627 sodium-based flavor enhancer (conflicts with Low Sodium).",
            "sodium benzoate" to "E211 sodium-based preservative.",
            "sodium nitrite" to "E250 curing salt high in sodium.",
            "sodium nitrate" to "E251 curing salt high in sodium.",
            "baking soda" to "Sodium bicarbonate contributes dietary sodium.",
            "sodium bicarbonate" to "Contributes dietary sodium.",
            "sea salt" to "Added sodium chloride (conflicts with Low Sodium).",
            "salt" to "Sodium chloride (monitor intake for Low Sodium goal)."
        ),
        HealthGoal.HALAL to listOf(
            "pork" to "Haram ingredient (Pork derivative conflicts with Halal).",
            "lard" to "Rendered pig fat (conflicts with Halal).",
            "bacon" to "Pork product (conflicts with Halal).",
            "gelatin" to "Unverified gelatin may be porcine-derived unless certified Halal.",
            "carmine" to "E120 insect-derived dye (cochineal); disputed/avoided in strict Halal.",
            "ethanol" to "Alcohol solvent/carrier (conflicts with Halal).",
            "alcohol" to "Alcoholic ingredient (conflicts with Halal).",
            "wine" to "Fermented alcoholic beverage (conflicts with Halal).",
            "rum" to "Distilled alcoholic spirit (conflicts with Halal).",
            "pepsin" to "Digestive enzyme often sourced from pig stomachs."
        ),
        HealthGoal.VEGAN to listOf(
            "milk" to "Animal dairy derivative (conflicts with Vegan).",
            "whey" to "Dairy protein byproduct (conflicts with Vegan).",
            "casein" to "Dairy protein derivative (conflicts with Vegan).",
            "egg" to "Animal egg ingredient (conflicts with Vegan).",
            "albumin" to "Egg protein (conflicts with Vegan).",
            "honey" to "Bee-derived product (conflicts with Vegan).",
            "beeswax" to "E901 bee secretion (conflicts with Vegan).",
            "gelatin" to "Hydrolyzed animal collagen (conflicts with Vegan).",
            "carmine" to "E120 crushed cochineal beetle pigment (conflicts with Vegan).",
            "shellac" to "E904 resin secreted by lac insects (conflicts with Vegan).",
            "lard" to "Rendered animal fat (conflicts with Vegan).",
            "tallow" to "Rendered beef/mutton fat (conflicts with Vegan).",
            "anchovy" to "Fish ingredient (conflicts with Vegan).",
            "chicken" to "Animal meat (conflicts with Vegan).",
            "beef" to "Animal meat (conflicts with Vegan)."
        ),
        HealthGoal.VEGETARIAN to listOf(
            "gelatin" to "Slaughter-derived animal collagen (conflicts with Vegetarian).",
            "lard" to "Rendered pig fat (conflicts with Vegetarian).",
            "tallow" to "Rendered beef fat (conflicts with Vegetarian).",
            "rennet" to "Animal stomach enzyme unless specified as microbial rennet.",
            "anchovy" to "Fish derivative (conflicts with Vegetarian).",
            "carmine" to "E120 insect-derived red pigment (conflicts with Vegetarian).",
            "pork" to "Meat product (conflicts with Vegetarian).",
            "beef" to "Meat product (conflicts with Vegetarian).",
            "chicken" to "Poultry meat (conflicts with Vegetarian).",
            "fish" to "Fish product (conflicts with Vegetarian)."
        ),
        HealthGoal.KETO_FRIENDLY to listOf(
            "sugar" to "Net carbohydrate spike that disrupts ketosis (conflicts with Keto-Friendly).",
            "high-fructose corn syrup" to "High glycemic carbohydrate (conflicts with Keto-Friendly).",
            "maltodextrin" to "Rapidly absorbed carbohydrate starch (conflicts with Keto-Friendly).",
            "wheat flour" to "High-carbohydrate grain flour (conflicts with Keto-Friendly).",
            "enriched flour" to "Refined high-carb grain flour (conflicts with Keto-Friendly).",
            "corn starch" to "Pure starch carbohydrate filler (conflicts with Keto-Friendly).",
            "potato starch" to "High-glycemic starch carbohydrate (conflicts with Keto-Friendly).",
            "rice flour" to "High-carbohydrate grain flour (conflicts with Keto-Friendly).",
            "dextrose" to "Simple glucose sugar that interrupts ketosis."
        )
    )

    fun evaluateOffline(
        rawText: String,
        profile: UserDietaryProfile,
        snapshotThumbnailBytes: ByteArray? = null,
        elapsedMs: Long = 15L
    ): AuditVerdict {
        val cleanText = rawText.trim()
        val lowerText = cleanText.lowercase()
        val matches = mutableListOf<RuleMatch>()

        // 1. Check Allergens (HIGH severity -> DANGER)
        for (allergen in profile.selectedAllergens) {
            val rules = allergenDictionaries[allergen].orEmpty()
            for ((keyword, reason) in rules) {
                val pattern = Regex("\\b${Regex.escape(keyword)}s?\\b", RegexOption.IGNORE_CASE)
                val found = pattern.find(lowerText)
                if (found != null) {
                    val isAdvisoryOnly = isInsideMayContainClause(lowerText, found.range.first)
                    matches.add(
                        RuleMatch(
                            canonicalTerm = "${found.value.replaceFirstChar { it.uppercase() }} (${allergen.displayName})",
                            matchedSubstring = found.value,
                            reason = if (isAdvisoryOnly) {
                                "Cross-contamination advisory ('May contain') for ${allergen.displayName}: $reason"
                            } else {
                                "Active ${allergen.displayName} allergen restriction: $reason"
                            },
                            severity = if (isAdvisoryOnly) Severity.MEDIUM else Severity.HIGH
                        )
                    )
                }
            }
        }

        // 2. Check Health Goals (MEDIUM severity -> CAUTION)
        for (goal in profile.healthGoals) {
            val rules = healthGoalDictionaries[goal].orEmpty()
            for ((keyword, reason) in rules) {
                val pattern = Regex("\\b${Regex.escape(keyword)}s?\\b", RegexOption.IGNORE_CASE)
                val found = pattern.find(lowerText)
                if (found != null) {
                    matches.add(
                        RuleMatch(
                            canonicalTerm = "${found.value.replaceFirstChar { it.uppercase() }} (${goal.displayName})",
                            matchedSubstring = found.value,
                            reason = reason,
                            severity = Severity.MEDIUM
                        )
                    )
                }
            }
        }

        val deduplicatedFlags = matches
            .distinctBy { it.matchedSubstring.lowercase() to it.severity }
            .sortedBy { it.severity.ordinal }
            .map {
                FlaggedIngredient(
                    name = it.canonicalTerm,
                    riskReason = it.reason,
                    severity = it.severity
                )
            }

        val safetyLevel = when {
            deduplicatedFlags.any { it.severity == Severity.HIGH } -> SafetyLevel.DANGER
            deduplicatedFlags.isNotEmpty() -> SafetyLevel.CAUTION
            else -> SafetyLevel.SAFE
        }

        val activeCriteriaCount = profile.selectedAllergens.size + profile.healthGoals.size
        val summary = when {
            cleanText.isBlank() -> "No readable ingredient text was captured. Please re-scan the package label or paste the ingredients manually."
            safetyLevel == SafetyLevel.DANGER -> {
                val highRiskNames = deduplicatedFlags.filter { it.severity == Severity.HIGH }.joinToString(", ") { it.name }
                "DANGER: Local allergen engine identified explicit allergen matches ($highRiskNames) that conflict with your profile. Do not consume without verifying the physical package."
            }
            safetyLevel == SafetyLevel.CAUTION -> {
                val cautionNames = deduplicatedFlags.joinToString(", ") { it.name }
                "CAUTION: Local audit flagged ${deduplicatedFlags.size} ingredient(s) ($cautionNames) that conflict with your health goals or cross-contamination advisories."
            }
            activeCriteriaCount == 0 -> {
                "SAFE (No Profile Restrictions Active): No allergens or health goals are selected in your profile yet. Configure your dietary profile to enable personalized alerts."
            }
            else -> {
                "SAFE (Basic Local Match): No direct keyword matches found for your ${profile.selectedAllergens.size} allergen(s) and ${profile.healthGoals.size} health goal(s). For complex chemical derivatives, retry Cloud AI when online."
            }
        }

        return AuditVerdict(
            id = UUID.randomUUID().toString(),
            safetyLevel = safetyLevel,
            summary = summary,
            flaggedIngredients = deduplicatedFlags,
            rawExtractedText = cleanText,
            timestamp = Instant.now(),
            isOfflineFallback = true,
            productName = inferProductName(cleanText),
            snapshotThumbnailBytes = snapshotThumbnailBytes,
            ttftMs = elapsedMs,
            totalStreamDurationMs = elapsedMs
        )
    }

    /**
     * Extracts raw keywords matched in the text so the UI can highlight them (FR-04).
     */
    fun extractHighlightTerms(verdict: AuditVerdict, profile: UserDietaryProfile): List<String> {
        val terms = mutableSetOf<String>()
        verdict.flaggedIngredients.forEach { flag ->
            val rawName = flag.name.substringBefore(" (").trim()
            if (rawName.isNotBlank()) terms.add(rawName)
        }
        profile.selectedAllergens.forEach { allergen ->
            allergenDictionaries[allergen]?.forEach { (kw, _) ->
                if (verdict.rawExtractedText.contains(kw, ignoreCase = true)) {
                    terms.add(kw)
                }
            }
        }
        return terms.toList()
    }

    private fun isInsideMayContainClause(lowerText: String, matchIndex: Int): Boolean {
        val windowStart = (matchIndex - 65).coerceAtLeast(0)
        val precedingSnippet = lowerText.substring(windowStart, matchIndex)
        return precedingSnippet.contains("may contain") ||
            precedingSnippet.contains("traces of") ||
            precedingSnippet.contains("facility that processes")
    }

    fun inferProductName(rawText: String): String {
        val firstLine = rawText.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.length in 3..42 &&
                    !line.startsWith("ingredient", ignoreCase = true) &&
                    !line.startsWith("contains", ignoreCase = true)
            }
        return firstLine ?: "Package Label Scan"
    }
}
