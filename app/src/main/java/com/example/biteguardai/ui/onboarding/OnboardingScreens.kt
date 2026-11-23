package com.example.biteguardai.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.ui.components.BiteGuardAccordion
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.RadialModernAnimatedBackground
import com.biteguard.core.ui.components.RobotLottieAnimation
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors

/**
 * Two-Step Material 3 Adaptive Onboarding Flow:
 * 1. Welcome Screen: Adaptive soft radial-modern background animation, vertical structure:
 *    [Welcome Message] -> robot.json Lottie -> [Ready to make every bite safer and healthier?] + User's Name input.
 *    Adapts smoothly across compact phones, landscape orientations, tablets/foldables, and Light/Dark/High-Contrast themes.
 * 2. Pick Allergies Screen: Uncluttered allergen chip selector + M3 Accordions for optional health goals & derivatives.
 */
@Composable
fun BiteGuardOnboardingFlow(
    initialName: String,
    initialAllergens: Set<Allergen>,
    initialHealthGoals: Set<HealthGoal>,
    onCompleteOnboarding: (userName: String, allergens: Set<Allergen>, healthGoals: Set<HealthGoal>) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var userName by rememberSaveable { mutableStateOf(initialName) }
    var selectedAllergens by remember { mutableStateOf(initialAllergens) }
    var selectedGoals by remember { mutableStateOf(initialHealthGoals) }

    RadialModernAnimatedBackground(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Modern M3 Step Progress Pill
                OnboardingStepIndicator(
                    currentStep = currentStep,
                    totalSteps = 2
                )

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(animationSpec = tween(320)) { width -> width / 3 } +
                                fadeIn(animationSpec = tween(280))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(280)) { width -> -width / 3 } +
                                    fadeOut(animationSpec = tween(220)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(320)) { width -> -width / 3 } +
                                fadeIn(animationSpec = tween(280))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(280)) { width -> width / 3 } +
                                    fadeOut(animationSpec = tween(220)))
                        }
                    },
                    label = "onboardingStepTransition",
                    modifier = Modifier.weight(1f)
                ) { step ->
                    when (step) {
                        0 -> WelcomeNameStep(
                            userName = userName,
                            onUserNameChange = { userName = it },
                            onNextStep = { currentStep = 1 }
                        )

                        else -> PickAllergiesStep(
                            userName = userName,
                            selectedAllergens = selectedAllergens,
                            selectedGoals = selectedGoals,
                            onToggleAllergen = { allergen ->
                                selectedAllergens = if (allergen in selectedAllergens) {
                                    selectedAllergens - allergen
                                } else {
                                    selectedAllergens + allergen
                                }
                            },
                            onClearAllergens = { selectedAllergens = emptySet() },
                            onToggleGoal = { goal ->
                                selectedGoals = if (goal in selectedGoals) {
                                    selectedGoals - goal
                                } else {
                                    selectedGoals + goal
                                }
                            },
                            onBack = { currentStep = 0 },
                            onComplete = {
                                onCompleteOnboarding(userName.trim(), selectedAllergens, selectedGoals)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = semanticColors.safe.color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Step ${currentStep + 1} of $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Screen 1: Adaptive Welcome Screen with vertical structure:
 * [Welcome Message] -> robot.json Lottie -> [Ready to make every bite safer and healthier?] + Name Input
 * Adapts sizing and spacing dynamically to screen height/width and system theme (Light, Dark, High-Contrast).
 */
@Composable
private fun WelcomeNameStep(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onNextStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val focusManager = LocalFocusManager.current
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appeared = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "robotHalo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < 640.dp
        val isShortHeight = maxHeight < 520.dp
        val robotContainerSize: Dp = when {
            isShortHeight -> 128.dp
            isCompactHeight -> 164.dp
            maxHeight > 820.dp -> 224.dp
            else -> 196.dp
        }
        val verticalSpacing: Dp = if (isCompactHeight) 10.dp else 16.dp
        val cardPadding: Dp = if (isCompactHeight) 16.dp else 20.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. [Welcome Message]
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { -it / 4 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Welcome to BiteGuard AI",
                        style = if (isCompactHeight) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Your personal AI label auditor that spots hidden allergens and chemical derivatives in real time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(verticalSpacing))

            // 2. [robot.json Lottie Animation]
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(robotContainerSize)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                    }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        width = if (semanticColors.isHighContrast) 2.5.dp else 1.5.dp,
                        color = semanticColors.safe.color.copy(
                            alpha = if (semanticColors.isHighContrast) 0.85f else 0.35f
                        )
                    ),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    RobotLottieAnimation(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isCompactHeight) 12.dp else 16.dp),
                        contentDescriptionLabel = "BiteGuard AI Welcome Robot Animation"
                    )
                }
            }

            Spacer(modifier = Modifier.height(verticalSpacing))

            // 3. [Ready to make you healthier / Name Input & CTA]
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(550)) + slideInVertically(tween(550)) { it / 3 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        width = if (semanticColors.isHighContrast) 2.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(cardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 14.dp)
                    ) {
                        Text(
                            text = "Ready to make every bite safer and healthier?",
                            style = if (isCompactHeight) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Tell us your name so we can personalize your on-device dietary guardian.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = userName,
                            onValueChange = onUserNameChange,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("Your Name") },
                            placeholder = { Text("e.g., Alex") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = semanticColors.safe.color,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = semanticColors.safe.color,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedLeadingIconColor = semanticColors.safe.color,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                cursorColor = semanticColors.safe.color
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    onNextStep()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onNextStep()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = semanticColors.safe.color,
                                contentColor = semanticColors.safe.onColor
                            )
                        ) {
                            Text(
                                text = "Continue to Allergen Setup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 2: Pick Allergies Screen (with optional M3 Accordions for Health Goals & Disclaimer).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickAllergiesStep(
    userName: String,
    selectedAllergens: Set<Allergen>,
    selectedGoals: Set<HealthGoal>,
    onToggleAllergen: (Allergen) -> Unit,
    onClearAllergens: () -> Unit,
    onToggleGoal: (HealthGoal) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val displayName = userName.trim().ifBlank { "Friend" }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Hi $displayName, pick your allergies",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Select any allergens you need BiteGuard AI to flag immediately, or skip if you have none.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Primary Allergen Picker Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = semanticColors.danger.color
                            )
                            Text(
                                text = "Allergens to Guard (${selectedAllergens.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (selectedAllergens.isNotEmpty()) {
                            FilterChip(
                                selected = false,
                                onClick = onClearAllergens,
                                label = { Text("Clear All", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Allergen.entries.forEach { allergen ->
                            val isSelected = allergen in selectedAllergens
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleAllergen(allergen) },
                                modifier = Modifier.heightIn(min = 44.dp),
                                label = {
                                    Text(
                                        text = allergen.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected"
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = semanticColors.danger.colorContainer,
                                    selectedLabelColor = semanticColors.danger.onColorContainer,
                                    selectedLeadingIconColor = semanticColors.danger.onColorContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = semanticColors.danger.color,
                                    selectedBorderWidth = 2.dp
                                )
                            )
                        }
                    }
                }
            }

            // Optional M3 Accordion for Dietary & Health Goals so the screen stays clean
            BiteGuardAccordion(
                title = "Dietary & Health Goals (Optional)",
                subtitle = "Low Sugar, Halal, Vegan, Keto & more",
                badgeText = if (selectedGoals.isNotEmpty()) "${selectedGoals.size} picked" else "Optional",
                leadingIcon = Icons.Default.HealthAndSafety,
                accentColor = semanticColors.safe.color,
                initiallyExpanded = false
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HealthGoal.entries.forEach { goal ->
                        val isSelected = goal in selectedGoals
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleGoal(goal) },
                            modifier = Modifier.heightIn(min = 42.dp),
                            label = {
                                Text(
                                    text = goal.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected"
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semanticColors.safe.colorContainer,
                                selectedLabelColor = semanticColors.safe.onColorContainer,
                                selectedLeadingIconColor = semanticColors.safe.onColorContainer
                            )
                        )
                    }
                }
            }

            // Medical Disclaimer Accordion
            BiteGuardAccordion(
                title = "Safety & Medical Disclaimer",
                subtitle = "Important informational decision-support notice",
                leadingIcon = Icons.Default.Info,
                accentColor = semanticColors.info.color,
                initiallyExpanded = false
            ) {
                MedicalDisclaimerBanner()
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to welcome screen"
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = semanticColors.safe.color,
                    contentColor = semanticColors.safe.onColor
                )
            ) {
                Text(
                    text = if (selectedAllergens.isNotEmpty()) {
                        "Start Guarding (${selectedAllergens.size})"
                    } else {
                        "Start Scanning"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
