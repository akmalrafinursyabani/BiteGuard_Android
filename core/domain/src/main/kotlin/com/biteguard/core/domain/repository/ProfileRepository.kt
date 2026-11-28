package com.biteguard.core.domain.repository

import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.UserDietaryProfile
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for reading and mutating the user's dietary profile and onboarding state.
 * Implemented by `:core:data` (Dependency Inversion Principle).
 */
interface ProfileRepository {
    val userProfileFlow: Flow<UserDietaryProfile>
    val onboardingAcceptedFlow: Flow<Boolean>

    fun isOnboardingCompletedSync(): Boolean
    suspend fun setUserName(name: String)
    suspend fun toggleAllergen(allergen: Allergen)
    suspend fun toggleHealthGoal(goal: HealthGoal)
    suspend fun setHighContrastMode(enabled: Boolean)
    suspend fun setLanguage(language: String)
    suspend fun setOnboardingAccepted(accepted: Boolean)
    suspend fun completeOnboarding(
        userName: String,
        allergens: Set<Allergen>,
        healthGoals: Set<HealthGoal> = emptySet()
    )
    suspend fun updateProfile(profile: UserDietaryProfile)
}
