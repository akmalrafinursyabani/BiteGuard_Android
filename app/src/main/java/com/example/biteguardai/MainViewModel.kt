package com.example.biteguardai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biteguard.core.domain.repository.ProfileRepository
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.UserDietaryProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val profile: UserDietaryProfile = UserDietaryProfile(),
    val isOnboarded: Boolean = false
)

sealed interface MainIntent {
    data object ToggleHighContrast : MainIntent
    data class SelectLanguage(val languageCode: String) : MainIntent
    data class CompleteOnboarding(
        val userName: String,
        val allergens: Set<Allergen>,
        val healthGoals: Set<HealthGoal>
    ) : MainIntent
}

/**
 * Presentation-layer ViewModel for app-level shell state (theme contrast, locale, and onboarding).
 * Keeps [MainActivity] decoupled from [ProfileRepository] and coroutine lifecycle management.
 */
class MainViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val initialOnboarded: Boolean = profileRepository.isOnboardingCompletedSync()
    private val sessionOnboardedFlow = MutableStateFlow(initialOnboarded)

    val uiState: StateFlow<MainUiState> = combine(
        profileRepository.userProfileFlow,
        profileRepository.onboardingAcceptedFlow,
        sessionOnboardedFlow
    ) { profile, acceptedFromFlow, sessionAccepted ->
        MainUiState(
            profile = profile,
            isOnboarded = sessionAccepted || acceptedFromFlow || profile.userName.isNotBlank()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(isOnboarded = initialOnboarded)
    )

    fun onIntent(intent: MainIntent) {
        when (intent) {
            MainIntent.ToggleHighContrast -> viewModelScope.launch {
                profileRepository.setHighContrastMode(!uiState.value.profile.highContrastMode)
            }

            is MainIntent.SelectLanguage -> viewModelScope.launch {
                profileRepository.setLanguage(intent.languageCode)
            }

            is MainIntent.CompleteOnboarding -> {
                sessionOnboardedFlow.value = true
                viewModelScope.launch {
                    profileRepository.completeOnboarding(
                        userName = intent.userName,
                        allergens = intent.allergens,
                        healthGoals = intent.healthGoals
                    )
                }
            }
        }
    }
}
