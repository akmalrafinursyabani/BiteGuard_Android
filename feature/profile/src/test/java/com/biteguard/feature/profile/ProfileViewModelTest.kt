package com.biteguard.feature.profile

import app.cash.turbine.test
import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.audit.DefaultAuditRepository
import com.biteguard.core.data.db.InMemoryAuditLogDataSource
import com.biteguard.core.data.profile.InMemoryProfileRepository
import com.biteguard.core.domain.engine.RuleBasedOfflineAuditEngine
import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.domain.repository.ProfileRepository
import com.biteguard.core.domain.usecase.ExtractHighlightTermsUseCase
import com.biteguard.core.domain.usecase.FilterScanHistoryUseCase
import com.biteguard.core.domain.usecase.SubmitAuditFeedbackUseCase
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.UserDietaryProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val offlineEngine = RuleBasedOfflineAuditEngine()

    private fun createViewModel(
        profileRepository: ProfileRepository,
        auditRepository: AuditRepository
    ): ProfileViewModel = ProfileViewModel(
        profileRepository = profileRepository,
        auditRepository = auditRepository,
        filterScanHistoryUseCase = FilterScanHistoryUseCase(),
        submitAuditFeedbackUseCase = SubmitAuditFeedbackUseCase(auditRepository),
        extractHighlightTermsUseCase = ExtractHighlightTermsUseCase(offlineEngine),
        defaultDispatcher = testDispatcher
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggling allergens, health goals, and high contrast mode updates uiState immediately`() = runTest {
        val profileRepo = InMemoryProfileRepository()
        val auditLog = InMemoryAuditLogDataSource()
        val auditRepo = DefaultAuditRepository(
            aiStreamDataSource = object : AiStreamDataSource {
                override val isApiKeyConfigured: Boolean = false
                override fun streamLabelAudit(
                    rawExtractedText: String,
                    profile: UserDietaryProfile,
                    imageJpegBytes: ByteArray?
                ): Flow<String> = emptyFlow()
            },
            auditLogDataSource = auditLog
        )

        val viewModel = createViewModel(profileRepo, auditRepo)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.profile.selectedAllergens.isEmpty())

            viewModel.onIntent(ProfileIntent.ToggleAllergen(Allergen.PEANUTS))
            val afterPeanut = awaitItem()
            assertTrue(Allergen.PEANUTS in afterPeanut.profile.selectedAllergens)

            viewModel.onIntent(ProfileIntent.ToggleHealthGoal(HealthGoal.LOW_SUGAR))
            val afterGoal = awaitItem()
            assertTrue(HealthGoal.LOW_SUGAR in afterGoal.profile.healthGoals)

            viewModel.onIntent(ProfileIntent.SetHighContrast(true))
            val afterContrast = awaitItem()
            assertTrue(afterContrast.profile.highContrastMode)

            viewModel.onIntent(ProfileIntent.UpdateUserName("Akmal"))
            val afterName = awaitItem()
            assertEquals("Akmal", afterName.profile.userName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filtering scan history by safety level and search query works reactively`() = runTest {
        val dangerVerdict = offlineEngine.evaluateOffline(
            rawText = "Granola Bar\nIngredients: Oats, Arachis Oil, Whey.",
            profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )
        val safeVerdict = offlineEngine.evaluateOffline(
            rawText = "Sparkling Water\nIngredients: Carbonated Water, Lemon Extract.",
            profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )

        val profileRepo = InMemoryProfileRepository()
        val auditLog = InMemoryAuditLogDataSource(listOf(dangerVerdict, safeVerdict))
        val auditRepo = DefaultAuditRepository(
            aiStreamDataSource = object : AiStreamDataSource {
                override val isApiKeyConfigured: Boolean = false
                override fun streamLabelAudit(
                    rawExtractedText: String,
                    profile: UserDietaryProfile,
                    imageJpegBytes: ByteArray?
                ): Flow<String> = emptyFlow()
            },
            auditLogDataSource = auditLog
        )

        val viewModel = createViewModel(profileRepo, auditRepo)

        viewModel.uiState.test {
            val all = awaitItem()
            assertEquals(2, all.filteredHistory.size)

            viewModel.onIntent(ProfileIntent.FilterBySafetyLevel(SafetyLevel.DANGER))
            val dangerOnly = awaitItem()
            assertEquals(1, dangerOnly.filteredHistory.size)
            assertEquals(SafetyLevel.DANGER, dangerOnly.filteredHistory.first().safetyLevel)

            viewModel.onIntent(ProfileIntent.FilterBySafetyLevel(SafetyLevel.DANGER)) // toggles off
            val resetFilter = awaitItem()
            assertEquals(2, resetFilter.filteredHistory.size)

            viewModel.onIntent(ProfileIntent.UpdateSearchQuery("Sparkling"))
            val searched = awaitItem()
            assertEquals(1, searched.filteredHistory.size)
            assertEquals(SafetyLevel.SAFE, searched.filteredHistory.first().safetyLevel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multi-select and bulk delete removes only selected scan items`() = runTest {
        val verdict1 = offlineEngine.evaluateOffline(
            rawText = "Granola Bar\nIngredients: Oats, Arachis Oil, Whey.",
            profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )
        val verdict2 = offlineEngine.evaluateOffline(
            rawText = "Sparkling Water\nIngredients: Carbonated Water, Lemon Extract.",
            profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )
        val verdict3 = offlineEngine.evaluateOffline(
            rawText = "Cookies\nIngredients: Flour, Sugar, Butter.",
            profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )

        val profileRepo = InMemoryProfileRepository()
        val auditLog = InMemoryAuditLogDataSource(listOf(verdict1, verdict2, verdict3))
        val auditRepo = DefaultAuditRepository(
            aiStreamDataSource = object : AiStreamDataSource {
                override val isApiKeyConfigured: Boolean = false
                override fun streamLabelAudit(
                    rawExtractedText: String,
                    profile: UserDietaryProfile,
                    imageJpegBytes: ByteArray?
                ): Flow<String> = emptyFlow()
            },
            auditLogDataSource = auditLog
        )

        val viewModel = createViewModel(profileRepo, auditRepo)

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(3, initial.allHistory.size)
            assertFalse(initial.isSelectionMode)

            // Start selection mode with verdict1
            viewModel.onIntent(ProfileIntent.StartSelectionMode(verdict1.id))
            val sel1 = awaitItem()
            assertTrue(sel1.isSelectionMode)
            assertEquals(setOf(verdict1.id), sel1.selectedIdsForBulkDelete)

            // Toggle select verdict2
            viewModel.onIntent(ProfileIntent.ToggleSelectVerdict(verdict2.id))
            val sel2 = awaitItem()
            assertEquals(setOf(verdict1.id, verdict2.id), sel2.selectedIdsForBulkDelete)

            // Request bulk delete
            viewModel.onIntent(ProfileIntent.RequestBulkDelete(true))
            val dialogState = awaitItem()
            assertTrue(dialogState.showBulkDeleteConfirmation)

            // Confirm bulk delete
            viewModel.onIntent(ProfileIntent.ConfirmBulkDelete)
            var afterDelete = awaitItem()
            while (afterDelete.allHistory.size != 1) {
                afterDelete = awaitItem()
            }
            assertEquals(1, afterDelete.allHistory.size)
            assertEquals(verdict3.id, afterDelete.allHistory.first().id)
            assertFalse(afterDelete.isSelectionMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
