package com.biteguard.core.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.UserDietaryProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

private val Context.biteGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "biteguard_user_profile_prefs"
)

interface ProfileRepository {
    val userProfileFlow: Flow<UserDietaryProfile>
    val onboardingAcceptedFlow: Flow<Boolean>

    fun isOnboardingCompletedSync(): Boolean
    suspend fun setUserName(name: String)
    suspend fun toggleAllergen(allergen: Allergen)
    suspend fun toggleHealthGoal(goal: HealthGoal)
    suspend fun setHighContrastMode(enabled: Boolean)
    suspend fun setOnboardingAccepted(accepted: Boolean)
    suspend fun completeOnboarding(
        userName: String,
        allergens: Set<Allergen>,
        healthGoals: Set<HealthGoal> = emptySet()
    )
    suspend fun updateProfile(profile: UserDietaryProfile)
}

class DataStoreProfileRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProfileRepository {

    private val syncPrefs by lazy {
        context.getSharedPreferences("biteguard_onboarding_sync_prefs", Context.MODE_PRIVATE)
    }

    private object Keys {
        const val SYNC_ONBOARDING_DONE = "sync_onboarding_completed"
        val USER_NAME = stringPreferencesKey("user_name")
        val ALLERGENS = stringSetPreferencesKey("selected_allergens")
        val HEALTH_GOALS = stringSetPreferencesKey("selected_health_goals")
        val LAST_UPDATED = longPreferencesKey("last_updated_epoch_milli")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast_mode")
        val ONBOARDING_ACCEPTED = booleanPreferencesKey("onboarding_disclaimer_accepted")
    }

    override fun isOnboardingCompletedSync(): Boolean {
        return syncPrefs.getBoolean(Keys.SYNC_ONBOARDING_DONE, false)
    }

    override val userProfileFlow: Flow<UserDietaryProfile> =
        context.biteGuardDataStore.data.map { prefs ->
            val userName = prefs[Keys.USER_NAME]?.trim().orEmpty()
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

            if (prefs[Keys.ONBOARDING_ACCEPTED] == true || userName.isNotBlank()) {
                syncPrefs.edit().putBoolean(Keys.SYNC_ONBOARDING_DONE, true).apply()
            }

            UserDietaryProfile(
                userName = userName,
                selectedAllergens = allergens,
                healthGoals = goals,
                lastUpdated = Instant.ofEpochMilli(updatedEpoch),
                highContrastMode = highContrast
            )
        }.flowOn(ioDispatcher)

    override val onboardingAcceptedFlow: Flow<Boolean> =
        context.biteGuardDataStore.data.map { prefs ->
            val accepted = (prefs[Keys.ONBOARDING_ACCEPTED] ?: false) ||
                !prefs[Keys.USER_NAME].isNullOrBlank() ||
                syncPrefs.getBoolean(Keys.SYNC_ONBOARDING_DONE, false)
            if (accepted) {
                syncPrefs.edit().putBoolean(Keys.SYNC_ONBOARDING_DONE, true).apply()
            }
            accepted
        }.flowOn(ioDispatcher)

    override suspend fun setUserName(name: String): Unit = withContext(ioDispatcher) {
        if (name.trim().isNotBlank()) {
            syncPrefs.edit().putBoolean(Keys.SYNC_ONBOARDING_DONE, true).apply()
        }
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name.trim()
            if (name.trim().isNotBlank()) {
                prefs[Keys.ONBOARDING_ACCEPTED] = true
            }
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun toggleAllergen(allergen: Allergen): Unit = withContext(ioDispatcher) {
        context.biteGuardDataStore.edit { prefs ->
            val current = prefs[Keys.ALLERGENS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(allergen.name)) {
                current.remove(allergen.name)
            }
            prefs[Keys.ALLERGENS] = current
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun toggleHealthGoal(goal: HealthGoal): Unit = withContext(ioDispatcher) {
        context.biteGuardDataStore.edit { prefs ->
            val current = prefs[Keys.HEALTH_GOALS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(goal.name)) {
                current.remove(goal.name)
            }
            prefs[Keys.HEALTH_GOALS] = current
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun setHighContrastMode(enabled: Boolean): Unit = withContext(ioDispatcher) {
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.HIGH_CONTRAST] = enabled
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun setOnboardingAccepted(accepted: Boolean): Unit = withContext(ioDispatcher) {
        syncPrefs.edit().putBoolean(Keys.SYNC_ONBOARDING_DONE, accepted).commit()
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_ACCEPTED] = accepted
        }
    }

    override suspend fun completeOnboarding(
        userName: String,
        allergens: Set<Allergen>,
        healthGoals: Set<HealthGoal>
    ): Unit = withContext(ioDispatcher) {
        syncPrefs.edit().putBoolean(Keys.SYNC_ONBOARDING_DONE, true).commit()
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = userName.trim()
            prefs[Keys.ALLERGENS] = allergens.map { it.name }.toSet()
            if (healthGoals.isNotEmpty()) {
                prefs[Keys.HEALTH_GOALS] = healthGoals.map { it.name }.toSet()
            }
            prefs[Keys.ONBOARDING_ACCEPTED] = true
            prefs[Keys.LAST_UPDATED] = System.currentTimeMillis()
        }
    }

    override suspend fun updateProfile(profile: UserDietaryProfile): Unit = withContext(ioDispatcher) {
        if (profile.userName.isNotBlank()) {
            syncPrefs.edit().putBoolean(Keys.SYNC_ONBOARDING_DONE, true).apply()
        }
        context.biteGuardDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = profile.userName.trim()
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

    override fun isOnboardingCompletedSync(): Boolean = _onboarding.value

    override suspend fun setUserName(name: String) {
        _profile.value = _profile.value.copy(userName = name.trim(), lastUpdated = Instant.now())
    }

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

    override suspend fun completeOnboarding(
        userName: String,
        allergens: Set<Allergen>,
        healthGoals: Set<HealthGoal>
    ) {
        _profile.value = _profile.value.copy(
            userName = userName.trim(),
            selectedAllergens = allergens,
            healthGoals = if (healthGoals.isNotEmpty()) healthGoals else _profile.value.healthGoals,
            lastUpdated = Instant.now()
        )
        _onboarding.value = true
    }

    override suspend fun updateProfile(profile: UserDietaryProfile) {
        _profile.value = profile.copy(lastUpdated = Instant.now())
    }
}
