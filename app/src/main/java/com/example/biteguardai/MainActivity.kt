package com.example.biteguardai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.biteguard.core.data.profile.ProfileRepository
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.SafetyVerdictBanner
import com.biteguard.core.ui.localization.getLocalizedConfiguration
import com.biteguard.core.ui.localization.getLocalizedContext
import com.biteguard.feature.profile.DietaryProfileScreen
import com.biteguard.feature.profile.ProfileViewModel
import com.biteguard.feature.profile.ScanHistoryScreen
import com.biteguard.feature.scanner.ScannerScreen
import com.biteguard.feature.scanner.ScannerViewModel
import com.example.biteguardai.ui.onboarding.BiteGuardOnboardingFlow
import com.example.biteguardai.ui.theme.BiteGuardAITheme
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

enum class BiteGuardDestination(
    val titleRes: Int,
    val icon: ImageVector,
    val descRes: Int
) {
    SCANNER(
        com.biteguard.core.ui.R.string.nav_scanner,
        Icons.Default.DocumentScanner,
        com.biteguard.core.ui.R.string.nav_scanner_desc
    ),
    HISTORY(
        com.biteguard.core.ui.R.string.nav_history,
        Icons.Default.History,
        com.biteguard.core.ui.R.string.nav_history_desc
    ),
    PROFILE(
        com.biteguard.core.ui.R.string.nav_profile,
        Icons.Default.Person,
        com.biteguard.core.ui.R.string.nav_profile_desc
    )
}

class MainActivity : ComponentActivity() {

    private val profileRepository: ProfileRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val initialOnboardingState = androidx.compose.runtime.remember {
                profileRepository.isOnboardingCompletedSync()
            }
            var sessionOnboardingCompleted by rememberSaveable {
                mutableStateOf(initialOnboardingState)
            }
            val profile by profileRepository.userProfileFlow.collectAsStateWithLifecycle(
                initialValue = UserDietaryProfile()
            )
            val onboardingAcceptedFromFlow by profileRepository.onboardingAcceptedFlow.collectAsStateWithLifecycle(
                initialValue = initialOnboardingState
            )
            val isOnboarded = sessionOnboardingCompleted || onboardingAcceptedFromFlow || profile.userName.isNotBlank()
            val scope = rememberCoroutineScope()

            val baseConfig = LocalConfiguration.current
            val baseContext = LocalContext.current
            val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current ?: this
            val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current ?: this
            val localizedConfig = androidx.compose.runtime.remember(baseConfig, profile.selectedLanguage) {
                getLocalizedConfiguration(baseConfig, profile.selectedLanguage)
            }
            val localizedContext = androidx.compose.runtime.remember(baseContext, profile.selectedLanguage) {
                getLocalizedContext(baseContext, profile.selectedLanguage)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
                LocalOnBackPressedDispatcherOwner provides onBackPressedDispatcherOwner
            ) {
                BiteGuardAITheme(
                    highContrast = profile.highContrastMode
                ) {
                    BiteGuardAppShell(
                        profile = profile,
                        onboardingAccepted = isOnboarded,
                        onToggleHighContrast = {
                            scope.launch {
                                profileRepository.setHighContrastMode(!profile.highContrastMode)
                            }
                        },
                        onSelectLanguage = { lang ->
                            scope.launch {
                                profileRepository.setLanguage(lang)
                            }
                        },
                        onCompleteOnboarding = { userName, allergens, goals ->
                            sessionOnboardingCompleted = true
                            scope.launch {
                                profileRepository.completeOnboarding(
                                    userName = userName,
                                    allergens = allergens,
                                    healthGoals = goals
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiteGuardAppShell(
    profile: UserDietaryProfile,
    onboardingAccepted: Boolean,
    onToggleHighContrast: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onCompleteOnboarding: (String, Set<Allergen>, Set<HealthGoal>) -> Unit
) {
    Crossfade(
        targetState = onboardingAccepted,
        animationSpec = tween(durationMillis = 360),
        label = "onboardingToHomeCrossfade"
    ) { isOnboarded ->
        if (!isOnboarded) {
            BiteGuardOnboardingFlow(
                initialName = profile.userName,
                initialAllergens = profile.selectedAllergens,
                initialHealthGoals = profile.healthGoals,
                selectedLanguage = profile.selectedLanguage,
                onSelectLanguage = onSelectLanguage,
                onCompleteOnboarding = onCompleteOnboarding
            )
        } else {
            BiteGuardMainScaffold(
                profile = profile,
                onToggleHighContrast = onToggleHighContrast
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiteGuardMainScaffold(
    profile: UserDietaryProfile,
    onToggleHighContrast: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(BiteGuardDestination.SCANNER) }
    val semanticColors = LocalBiteGuardSemanticColors.current

    val scannerViewModel: ScannerViewModel = koinViewModel()
    val profileViewModel: ProfileViewModel = koinViewModel()

    val scannerState by scannerViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = semanticColors.safe.color
                        )
                        Column {
                            Text(
                                text = stringResource(com.biteguard.core.ui.R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (profile.userName.isNotBlank()) {
                                    stringResource(com.biteguard.core.ui.R.string.header_guarding, profile.userName)
                                } else {
                                    stringResource(com.biteguard.core.ui.R.string.header_subtitle)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (profile.highContrastMode) {
                            semanticColors.info.color
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (profile.highContrastMode) {
                            semanticColors.info.onColor
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        border = BorderStroke(1.dp, semanticColors.info.color),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = onToggleHighContrast,
                            modifier = Modifier.semantics {
                                contentDescription = if (profile.highContrastMode) {
                                    "Disable ultra high-contrast accessibility mode"
                                } else {
                                    "Enable ultra high-contrast accessibility mode"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                BiteGuardDestination.entries.forEach { dest ->
                    val selected = currentTab == dest
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = dest },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = stringResource(dest.descRes)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(dest.titleRes),
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            BiteGuardDestination.SCANNER -> {
                ScannerScreen(
                    state = scannerState,
                    onIntent = scannerViewModel::onIntent,
                    onNavigateToProfile = { currentTab = BiteGuardDestination.PROFILE },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            BiteGuardDestination.HISTORY -> {
                ScanHistoryScreen(
                    state = profileState,
                    onIntent = profileViewModel::onIntent,
                    onNavigateToScanner = { currentTab = BiteGuardDestination.SCANNER },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            BiteGuardDestination.PROFILE -> {
                DietaryProfileScreen(
                    state = profileState,
                    onIntent = profileViewModel::onIntent,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BiteGuardPreview() {
    BiteGuardAITheme(highContrast = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SafetyVerdictBanner(safetyLevel = SafetyLevel.DANGER)
            SafetyVerdictBanner(safetyLevel = SafetyLevel.CAUTION, isOfflineFallback = true)
            SafetyVerdictBanner(safetyLevel = SafetyLevel.SAFE)
            MedicalDisclaimerBanner()
        }
    }
}

