package com.biteguard.core.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.UserDietaryProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.biteGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "biteguard_user_profile_prefs"
)

interface ProfileRepository {
    val userProfileFlow: Flow<UserDietaryProfile>
    val onboardingAcceptedFlow: Flow<Boolean>

    suspend fun toggleAllergen(allergen: Allergen)
    suspend fun toggleHealthGoal(goal: HealthGoal)
    suspend fun setHighContrastMode(enabled: Boolean)
    suspend fun setOnboardingAccepted(accepted: Boolean)
    suspend fun updateProfile(profile: UserDietaryProfile)
}

class DataStoreProfileRepository(
    private val context: Context
) : ProfileRepository {

    private object Keys {
        val ALLERGENS = stringSetPreferencesKey("selected_allergens")
        val HEALTH_GOALS = stringSetPreferencesKey("selected_health_goals")
        val LAST_UPDATED = longPreferencesKey("last_updated_epoch_milli")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast_mode")
        val ONBOARDING_ACCEPTED = booleanPreferencesKey("onboarding_disclaimer_accepted")
    }

    override val userProfileFlow: Flow<UserDietaryProfile> =
        context.biteGuardDataStore.data.map { prefs ->
            val allergens = prefs[Keys.ALLERGENS]
                ?.mapNotNull { name -> runCatching { Allergen.valueOf(name) }.getOrNull() }
                ?.toSet()
                ?: emptySet()

            val goals = prefs[Keys.HEALTH_GOALS]
                ?.mapNotNull { name -> runCatching { HealthGoal.valueOf(name) }.getOrNull() }
                ?.toSet()
                ?: emptySet()

            val updatedEpoch = prefs[Keys.LAST_UPDATED] ?: System.currentTimeMillis()
            val highContrast = prefs[Keys.HIGH_CONTRAST] ?: false

            UserDietaryProfile(
                selectedAllergens = allergens,
                healthGoals = goals,
                lastUpdated = Instant.ofEpochMilli(updatedEpoch),
                highContrastMode = highContrast
            )
        }

    override val onboardingAcceptedFlow: Flow<Boolean> =
        context.biteGuardDataStore.data.map { prefs ->
            prefs[Keys.ONBOARDING_ACCEPTED] ?: false
        }

    override suspend fun toggleAllergen(allergen: Allergen) {
        context.biteGuardDataStore.edit { prefs ->
            val current = prefs[Keys.ALLERGENS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(allergen.name)) {
                current.remove(allergen.name)
            }
            prefs[Keys.ALLERGENS] = current
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun toggleHealthGoal(goal: HealthGoal) {
        context.biteGuardDataStore.edit { prefs ->
            val current = prefs[Keys.HEALTH_GOALS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(goal.name)) {
                current.remove(goal.name)
            }
            prefs[Keys.HEALTH_GOALS] = current
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun setHighContrastMode(enabled: Boolean) {
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.HIGH_CONTRAST] = enabled
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun setOnboardingAccepted(accepted: Boolean) {
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_ACCEPTED] = accepted
        }
    }

    override suspend fun updateProfile(profile: UserDietaryProfile) {
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.ALLERGENS] = profile.selectedAllergens.map { it.name }.toSet()
            prefs[Keys.HEALTH_GOALS] = profile.healthGoals.map { it.name }.toSet()
            prefs[Keys.HIGH_CONTRAST] = profile.highContrastMode
            prefs[Keys.LAST_UPDATED] = Instant.now().toEpochMilli()
        }
    }
}

/**
 * In-memory implementation for deterministic JVM unit testing with Turbine (NFR-T6).
 */
class InMemoryProfileRepository(
    initialProfile: UserDietaryProfile = UserDietaryProfile()
) : ProfileRepository {
    private val _profile = MutableStateFlow(initialProfile)
    private val _onboarding = MutableStateFlow(true)

    override val userProfileFlow: Flow<UserDietaryProfile> = _profile.asStateFlow()
    override val onboardingAcceptedFlow: Flow<Boolean> = _onboarding.asStateFlow()

    override suspend fun toggleAllergen(allergen: Allergen) {
        val current = _profile.value
        val updated = if (allergen in current.selectedAllergens) {
            current.selectedAllergens - allergen
        } else {
            current.selectedAllergens + allergen
        }
        _profile.value = current.copy(selectedAllergens = updated, lastUpdated = Instant.now())
    }

    override suspend fun toggleHealthGoal(goal: HealthGoal) {
        val current = _profile.value
        val updated = if (goal in current.healthGoals) {
            current.healthGoals - goal
        } else {
            current.healthGoals + goal
        }
        _profile.value = current.copy(healthGoals = updated, lastUpdated = Instant.now())
    }

    override suspend fun setHighContrastMode(enabled: Boolean) {
        _profile.value = _profile.value.copy(highContrastMode = enabled, lastUpdated = Instant.now())
    }

    override suspend fun setOnboardingAccepted(accepted: Boolean) {
        _onboarding.value = accepted
    }

    override suspend fun updateProfile(profile: UserDietaryProfile) {
        _profile.value = profile.copy(lastUpdated = Instant.now())
    }
}
