package com.biteguard.feature.profile

import android.graphics.Bitmap
import app.cash.turbine.test
import com.biteguard.core.data.ai.AiStreamDataSource
import com.biteguard.core.data.audit.DefaultAuditRepository
import com.biteguard.core.data.db.InMemoryAuditLogDataSource
import com.biteguard.core.data.fallback.LocalFallbackAuditEngine
import com.biteguard.core.data.profile.InMemoryProfileRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

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
                    frameBitmap: Bitmap?
                ): Flow<String> = emptyFlow()
            },
            auditLogDataSource = auditLog
        )

        val viewModel = ProfileViewModel(profileRepo, auditRepo)

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
        val dangerVerdict = LocalFallbackAuditEngine.evaluateOffline(
            rawText = "Granola Bar\nIngredients: Oats, Arachis Oil, Whey.",
            profile = UserDietaryProfile(selectedAllergens = setOf(Allergen.PEANUTS))
        )
        val safeVerdict = LocalFallbackAuditEngine.evaluateOffline(
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
                    frameBitmap: Bitmap?
                ): Flow<String> = emptyFlow()
            },
            auditLogDataSource = auditLog
        )

        val viewModel = ProfileViewModel(profileRepo, auditRepo, testDispatcher)

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
}
