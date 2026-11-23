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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biteguard.core.data.profile.ProfileRepository
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.UserDietaryProfile
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.SafetyVerdictBanner
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
    val title: String,
    val icon: ImageVector,
    val contentDesc: String
) {
    SCANNER("Scan Label", Icons.Default.DocumentScanner, "Scan package ingredient label"),
    HISTORY("Audit Log", Icons.Default.History, "View encrypted scan history"),
    PROFILE("My Profile", Icons.Default.Person, "Configure allergens and health goals")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiteGuardAppShell(
    profile: UserDietaryProfile,
    onboardingAccepted: Boolean,
    onToggleHighContrast: () -> Unit,
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
                                text = "BiteGuard AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (profile.userName.isNotBlank()) {
                                    "Guarding ${profile.userName} • Smart Food Auditor"
                                } else {
                                    "Smart Food & Allergen Auditor"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                contentDescription = dest.contentDesc
                            )
                        },
                        label = {
                            Text(
                                text = dest.title,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
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

