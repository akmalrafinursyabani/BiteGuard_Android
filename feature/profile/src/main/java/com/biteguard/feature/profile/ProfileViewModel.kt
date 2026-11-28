package com.biteguard.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biteguard.core.domain.repository.AuditRepository
import com.biteguard.core.domain.repository.ProfileRepository
import com.biteguard.core.domain.usecase.ExtractHighlightTermsUseCase
import com.biteguard.core.domain.usecase.FilterScanHistoryUseCase
import com.biteguard.core.domain.usecase.SubmitAuditFeedbackUseCase
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.model.UserFeedback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileAndHistoryUiState(
    val profile: UserDietaryProfile = UserDietaryProfile(),
    val allHistory: List<AuditVerdict> = emptyList(),
    val filteredHistory: List<AuditVerdict> = emptyList(),
    val searchQuery: String = "",
    val selectedSafetyFilter: SafetyLevel? = null,
    val selectedVerdictForDetail: AuditVerdict? = null,
    val selectedVerdictHighlights: List<String> = emptyList(),
    val showClearConfirmation: Boolean = false,
    val selectedIdsForBulkDelete: Set<String> = emptySet(),
    val showBulkDeleteConfirmation: Boolean = false
) {
    val isSelectionMode: Boolean
        get() = selectedIdsForBulkDelete.isNotEmpty()
}

sealed interface ProfileIntent {
    data class UpdateUserName(val name: String) : ProfileIntent
    data class ToggleAllergen(val allergen: Allergen) : ProfileIntent
    data class ToggleHealthGoal(val goal: HealthGoal) : ProfileIntent
    data class SetHighContrast(val enabled: Boolean) : ProfileIntent
    data class SetLanguage(val languageCode: String) : ProfileIntent
    data class UpdateSearchQuery(val query: String) : ProfileIntent
    data class FilterBySafetyLevel(val level: SafetyLevel?) : ProfileIntent
    data class SelectVerdictDetail(val verdict: AuditVerdict?) : ProfileIntent
    data class SubmitVerdictFeedback(val verdict: AuditVerdict, val feedback: UserFeedback) : ProfileIntent
    data class RequestClearHistory(val show: Boolean) : ProfileIntent
    data object ConfirmClearHistory : ProfileIntent

    // Bulk deletion & Multi-select intents
    data class StartSelectionMode(val initialVerdictId: String) : ProfileIntent
    data class ToggleSelectVerdict(val verdictId: String) : ProfileIntent
    data object SelectAllFilteredVerdicts : ProfileIntent
    data object ClearSelectionMode : ProfileIntent
    data class RequestBulkDelete(val show: Boolean) : ProfileIntent
    data object ConfirmBulkDelete : ProfileIntent
}

private data class SelectionDialogState(
    val selectedId: String?,
    val showClear: Boolean,
    val selectedIds: Set<String>,
    val showBulkDelete: Boolean
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val auditRepository: AuditRepository,
    private val filterScanHistoryUseCase: FilterScanHistoryUseCase,
    private val submitAuditFeedbackUseCase: SubmitAuditFeedbackUseCase,
    private val extractHighlightTermsUseCase: ExtractHighlightTermsUseCase,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val safetyFilterFlow = MutableStateFlow<SafetyLevel?>(null)
    private val selectedVerdictIdFlow = MutableStateFlow<String?>(null)
    private val showClearConfirmFlow = MutableStateFlow(false)
    private val selectedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val showBulkDeleteConfirmFlow = MutableStateFlow(false)

    private val selectionStateFlow = combine(
        selectedVerdictIdFlow,
        showClearConfirmFlow,
        selectedIdsFlow,
        showBulkDeleteConfirmFlow
    ) { selectedId, showClear, selectedIds, showBulkDelete ->
        SelectionDialogState(selectedId, showClear, selectedIds, showBulkDelete)
    }

    val uiState: StateFlow<ProfileAndHistoryUiState> = combine(
        profileRepository.userProfileFlow,
        auditRepository.scanHistoryFlow,
        searchQueryFlow,
        safetyFilterFlow,
        selectionStateFlow
    ) { profile, history, query, safetyFilter, selState ->
        val filtered = if (query.isBlank() && safetyFilter == null) {
            history
        } else {
            withContext(defaultDispatcher) {
                filterScanHistoryUseCase(history, query, safetyFilter)
            }
        }
        val selectedVerdict = history.firstOrNull { it.id == selState.selectedId }
        val selectedHighlights = if (selectedVerdict != null) {
            withContext(defaultDispatcher) {
                extractHighlightTermsUseCase(selectedVerdict, profile)
            }
        } else {
            emptyList()
        }

        ProfileAndHistoryUiState(
            profile = profile,
            allHistory = history,
            filteredHistory = filtered,
            searchQuery = query,
            selectedSafetyFilter = safetyFilter,
            selectedVerdictForDetail = selectedVerdict,
            selectedVerdictHighlights = selectedHighlights,
            showClearConfirmation = selState.showClear,
            selectedIdsForBulkDelete = selState.selectedIds,
            showBulkDeleteConfirmation = selState.showBulkDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileAndHistoryUiState()
    )

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.UpdateUserName -> viewModelScope.launch {
                profileRepository.setUserName(intent.name)
            }
            is ProfileIntent.ToggleAllergen -> viewModelScope.launch {
                profileRepository.toggleAllergen(intent.allergen)
            }
            is ProfileIntent.ToggleHealthGoal -> viewModelScope.launch {
                profileRepository.toggleHealthGoal(intent.goal)
            }
            is ProfileIntent.SetHighContrast -> viewModelScope.launch {
                profileRepository.setHighContrastMode(intent.enabled)
            }
            is ProfileIntent.SetLanguage -> viewModelScope.launch {
                profileRepository.setLanguage(intent.languageCode)
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
                submitAuditFeedbackUseCase(intent.verdict, intent.feedback)
            }
            is ProfileIntent.RequestClearHistory -> {
                showClearConfirmFlow.value = intent.show
            }
            ProfileIntent.ConfirmClearHistory -> viewModelScope.launch {
                showClearConfirmFlow.value = false
                selectedVerdictIdFlow.value = null
                selectedIdsFlow.value = emptySet()
                auditRepository.clearScanHistory()
            }
            is ProfileIntent.StartSelectionMode -> {
                selectedIdsFlow.value = setOf(intent.initialVerdictId)
            }
            is ProfileIntent.ToggleSelectVerdict -> {
                val current = selectedIdsFlow.value
                selectedIdsFlow.value = if (intent.verdictId in current) {
                    current - intent.verdictId
                } else {
                    current + intent.verdictId
                }
            }
            ProfileIntent.SelectAllFilteredVerdicts -> {
                val currentFiltered = uiState.value.filteredHistory
                selectedIdsFlow.value = if (selectedIdsFlow.value.size == currentFiltered.size && currentFiltered.isNotEmpty()) {
                    emptySet()
                } else {
                    currentFiltered.map { it.id }.toSet()
                }
            }
            ProfileIntent.ClearSelectionMode -> {
                selectedIdsFlow.value = emptySet()
                showBulkDeleteConfirmFlow.value = false
            }
            is ProfileIntent.RequestBulkDelete -> {
                showBulkDeleteConfirmFlow.value = intent.show
            }
            ProfileIntent.ConfirmBulkDelete -> viewModelScope.launch {
                val idsToDelete = selectedIdsFlow.value.toList()
                selectedIdsFlow.value = emptySet()
                showBulkDeleteConfirmFlow.value = false
                if (idsToDelete.isNotEmpty()) {
                    auditRepository.deleteVerdicts(idsToDelete)
                }
            }
        }
    }
}
