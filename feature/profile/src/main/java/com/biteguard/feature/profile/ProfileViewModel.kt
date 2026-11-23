package com.biteguard.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biteguard.core.data.audit.AuditRepository
import com.biteguard.core.data.profile.ProfileRepository
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileAndHistoryUiState(
    val profile: UserDietaryProfile = UserDietaryProfile(),
    val allHistory: List<AuditVerdict> = emptyList(),
    val filteredHistory: List<AuditVerdict> = emptyList(),
    val searchQuery: String = "",
    val selectedSafetyFilter: SafetyLevel? = null,
    val selectedVerdictForDetail: AuditVerdict? = null,
    val showClearConfirmation: Boolean = false
)

sealed interface ProfileIntent {
    data class ToggleAllergen(val allergen: Allergen) : ProfileIntent
    data class ToggleHealthGoal(val goal: HealthGoal) : ProfileIntent
    data class SetHighContrast(val enabled: Boolean) : ProfileIntent
    data class UpdateSearchQuery(val query: String) : ProfileIntent
    data class FilterBySafetyLevel(val level: SafetyLevel?) : ProfileIntent
    data class SelectVerdictDetail(val verdict: AuditVerdict?) : ProfileIntent
    data class SubmitVerdictFeedback(val verdict: AuditVerdict, val feedback: UserFeedback) : ProfileIntent
    data class RequestClearHistory(val show: Boolean) : ProfileIntent
    data object ConfirmClearHistory : ProfileIntent
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val auditRepository: AuditRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val safetyFilterFlow = MutableStateFlow<SafetyLevel?>(null)
    private val selectedVerdictIdFlow = MutableStateFlow<String?>(null)
    private val showClearConfirmFlow = MutableStateFlow(false)

    val uiState: StateFlow<ProfileAndHistoryUiState> = combine(
        profileRepository.userProfileFlow,
        auditRepository.scanHistoryFlow,
        searchQueryFlow,
        safetyFilterFlow,
        combine(selectedVerdictIdFlow, showClearConfirmFlow) { id, showClear -> id to showClear }
    ) { profile, history, query, safetyFilter, (selectedId, showClear) ->
        val filtered = history.filter { verdict ->
            val matchesLevel = safetyFilter == null || verdict.safetyLevel == safetyFilter
            val matchesQuery = query.isBlank() ||
                verdict.productName.contains(query, ignoreCase = true) ||
                verdict.summary.contains(query, ignoreCase = true) ||
                verdict.rawExtractedText.contains(query, ignoreCase = true) ||
                verdict.flaggedIngredients.any {
                    it.name.contains(query, ignoreCase = true) ||
                        it.riskReason.contains(query, ignoreCase = true)
                }
            matchesLevel && matchesQuery
        }

        ProfileAndHistoryUiState(
            profile = profile,
            allHistory = history,
            filteredHistory = filtered,
            searchQuery = query,
            selectedSafetyFilter = safetyFilter,
            selectedVerdictForDetail = history.firstOrNull { it.id == selectedId },
            showClearConfirmation = showClear
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileAndHistoryUiState()
    )

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.ToggleAllergen -> viewModelScope.launch {
                profileRepository.toggleAllergen(intent.allergen)
            }
            is ProfileIntent.ToggleHealthGoal -> viewModelScope.launch {
                profileRepository.toggleHealthGoal(intent.goal)
            }
            is ProfileIntent.SetHighContrast -> viewModelScope.launch {
                profileRepository.setHighContrastMode(intent.enabled)
            }
            is ProfileIntent.UpdateSearchQuery -> {
                searchQueryFlow.value = intent.query
            }
            is ProfileIntent.FilterBySafetyLevel -> {
                safetyFilterFlow.value = if (safetyFilterFlow.value == intent.level) null else intent.level
            }
            is ProfileIntent.SelectVerdictDetail -> {
                selectedVerdictIdFlow.value = intent.verdict?.id
            }
            is ProfileIntent.SubmitVerdictFeedback -> viewModelScope.launch {
                auditRepository.submitFeedback(intent.verdict, intent.feedback)
            }
            is ProfileIntent.RequestClearHistory -> {
                showClearConfirmFlow.value = intent.show
            }
            ProfileIntent.ConfirmClearHistory -> viewModelScope.launch {
                showClearConfirmFlow.value = false
                selectedVerdictIdFlow.value = null
                auditRepository.clearScanHistory()
            }
        }
    }
}
