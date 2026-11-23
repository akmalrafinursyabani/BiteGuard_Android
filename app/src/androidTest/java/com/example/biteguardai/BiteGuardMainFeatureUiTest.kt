package com.example.biteguardai

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.FlaggedIngredient
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.StreamState
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import com.biteguard.core.ui.components.ActionableFailureCard
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.SafetyVerdictBanner
import com.biteguard.feature.profile.DietaryProfileScreen
import com.biteguard.feature.profile.ProfileAndHistoryUiState
import com.biteguard.feature.profile.ProfileIntent
import com.biteguard.feature.scanner.ScannerIntent
import com.biteguard.feature.scanner.ScannerScreen
import com.biteguard.feature.scanner.ScannerUiState
import com.example.biteguardai.ui.theme.BiteGuardAITheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Main Feature Compose UI Use-Case Tests (FR-01, FR-03, FR-04, FR-06, NFR-NT2, NFR-NT4).
 * Verifies accessible safety signage, actionable failure decisions, profile toggles, and feedback flows.
 */
class BiteGuardMainFeatureUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * UI Use Case 1 (NFR-NT2 & NFR-NT4): Multimodal Safety Signage (SAFE, CAUTION, DANGER)
     * displays explicit text labels, accessible TalkBack content descriptions, and persistent medical disclaimer.
     */
    @Test
    fun useCase1_multimodalSafetySignageAndMedicalDisclaimerAreAccessible() {
        composeTestRule.setContent {
            BiteGuardAITheme(highContrast = true) {
                Column {
                    SafetyVerdictBanner(safetyLevel = SafetyLevel.DANGER)
                    SafetyVerdictBanner(safetyLevel = SafetyLevel.CAUTION, isOfflineFallback = true)
                    SafetyVerdictBanner(safetyLevel = SafetyLevel.SAFE)
                    MedicalDisclaimerBanner()
                }
            }
        }

        composeTestRule.onNodeWithText("DANGER").assertIsDisplayed()
        composeTestRule.onNodeWithText("CAUTION").assertIsDisplayed()
        composeTestRule.onNodeWithText("LOCAL MATCH").assertIsDisplayed()
        composeTestRule.onNodeWithText("SAFE").assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                "Safety Verdict: DANGER. Do Not Consume — Conflict Detected.",
                substring = true
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "BiteGuard AI is an informational decision-support tool.",
                substring = true
            )
            .assertIsDisplayed()
    }

    /**
     * UI Use Case 2 (FR-04 & Accessibility Decision Support): ActionableFailureCard displays clear
     * offline/timeout explanation and invokes user decision callbacks (Retry Cloud AI, Keep Local Match, Edit/Paste).
     */
    @Test
    fun useCase2_actionableFailureCardHelpsUserDecideOnNetworkTimeout() {
        var retryClicked = false
        var keepOfflineClicked = false
        var manualEditClicked = false

        composeTestRule.setContent {
            BiteGuardAITheme(highContrast = false) {
                ActionableFailureCard(
                    errorKind = AuditErrorKind.NETWORK_TIMEOUT,
                    errorMessage = "Timed out after 5000ms",
                    retryAttempt = 1,
                    hasOfflineFallback = true,
                    onRetryCloudAudit = { retryClicked = true },
                    onUseOfflineMatch = { keepOfflineClicked = true },
                    onRetakeOrManualEdit = { manualEditClicked = true }
                )
            }
        }

        composeTestRule
            .onNodeWithText("Network Offline — Showing Basic Local Match")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Retry Cloud AI (#2)").performClick()
        assertTrue(retryClicked)

        composeTestRule.onNodeWithText("Keep Local Match").performClick()
        assertTrue(keepOfflineClicked)

        composeTestRule.onNodeWithText("Edit / Paste Text").performClick()
        assertTrue(manualEditClicked)
    }

    /**
     * UI Use Case 3 (FR-03 & FR-06): ScannerScreen in StreamState.Success renders DANGER verdict,
     * flagged ingredient cards, highlighted raw OCR text, and dispatches Thumbs Up feedback intent.
     */
    @Test
    fun useCase3_scannerScreenDisplaysCompletedVerdictAndDispatchesFeedback() {
        val sampleVerdict = AuditVerdict(
            id = "verdict-101",
            safetyLevel = SafetyLevel.DANGER,
            summary = "DANGER: Arachis Oil (Peanut derivative) detected in ingredient list.",
            flaggedIngredients = listOf(
                FlaggedIngredient(
                    name = "Arachis Oil (Peanuts)",
                    riskReason = "Refined peanut oil conflicts with Peanuts allergy",
                    severity = Severity.HIGH
                )
            ),
            rawExtractedText = "Ingredients: Rolled Oats, Arachis Oil, Honey.",
            timestamp = Instant.now(),
            isOfflineFallback = false,
            ttftMs = 420L,
            totalStreamDurationMs = 980L
        )

        val capturedIntents = mutableListOf<ScannerIntent>()

        composeTestRule.setContent {
            BiteGuardAITheme(highContrast = true) {
                ScannerScreen(
                    state = ScannerUiState(
                        profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS)),
                        streamState = StreamState.Success(sampleVerdict)
                    ),
                    onIntent = { capturedIntents.add(it) },
                    onNavigateToProfile = {}
                )
            }
        }

        composeTestRule.onNodeWithText("DANGER").assertIsDisplayed()
        composeTestRule.onNodeWithText("Arachis Oil (Peanuts)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Helpful").performClick()

        assertTrue(
            capturedIntents.any {
                it is ScannerIntent.SubmitFeedback && it.feedback == UserFeedback.THUMBS_UP
            }
        )
    }

    /**
     * UI Use Case 4 (FR-01 & NFR-NT2): DietaryProfileScreen allows toggling allergens, health goals,
     * and Ultra High-Contrast Accessibility Mode with immediate intent dispatch.
     */
    @Test
    fun useCase4_dietaryProfileScreenTogglesAllergensGoalsAndHighContrast() {
        val capturedIntents = mutableListOf<ProfileIntent>()

        composeTestRule.setContent {
            BiteGuardAITheme(highContrast = false) {
                DietaryProfileScreen(
                    state = ProfileAndHistoryUiState(
                        profile = UserDietaryProfile(
                            selectedAllergens = setOf(Allergen.PEANUTS),
                            healthGoals = setOf(HealthGoal.LOW_SUGAR),
                            highContrastMode = false
                        )
                    ),
                    onIntent = { capturedIntents.add(it) }
                )
            }
        }

        composeTestRule.onNodeWithText("Ultra High-Contrast Accessibility Mode").performClick()
        assertTrue(capturedIntents.contains(ProfileIntent.SetHighContrast(true)))

        composeTestRule.onNodeWithText("Dairy").performClick()
        assertEquals(
            ProfileIntent.ToggleAllergen(Allergen.DAIRY),
            capturedIntents.last()
        )
    }

    /**
     * UI Use Case 5: Two-step Onboarding Flow allows entering user's name on Welcome Screen
     * with Robot Lottie and selecting allergies on Pick Allergies Screen.
     */
    @Test
    fun useCase5_twoStepOnboardingFlowSavesUserNameAndAllergens() {
        var completedName = ""
        var completedAllergens: Set<Allergen> = emptySet()

        composeTestRule.setContent {
            BiteGuardAITheme(highContrast = false) {
                com.example.biteguardai.ui.onboarding.BiteGuardOnboardingFlow(
                    initialName = "Akmal",
                    initialAllergens = emptySet(),
                    initialHealthGoals = emptySet(),
                    onCompleteOnboarding = { name, allergens, _ ->
                        completedName = name
                        completedAllergens = allergens
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Welcome to BiteGuard AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready to make every bite safer and healthier?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue to Allergen Setup").performClick()

        composeTestRule.onNodeWithText("Hi Akmal, pick your allergies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Peanuts").performClick()
        composeTestRule.onNodeWithText("Start Guarding (1)").performClick()

        assertEquals("Akmal", completedName)
        assertTrue(Allergen.PEANUTS in completedAllergens)
    }
}
