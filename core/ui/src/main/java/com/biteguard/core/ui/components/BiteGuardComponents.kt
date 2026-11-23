package com.biteguard.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.FlaggedIngredient
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.UserFeedback
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors

/**
 * NFR-NT2: Multimodal Safety Signage.
 * Never relies on color alone — combines High-Contrast Color from Color.kt,
 * Accessible System Icon (Checkmark, Warning Triangle, Octagonal Stop), and Explicit Text Label.
 */
@Composable
fun SafetyVerdictBanner(
    safetyLevel: SafetyLevel,
    isOfflineFallback: Boolean = false,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val family = semanticColors.forSafetyLevel(safetyLevel)
    val icon: ImageVector = when (safetyLevel) {
        SafetyLevel.SAFE -> Icons.Default.CheckCircle
        SafetyLevel.CAUTION -> Icons.Default.WarningAmber
        SafetyLevel.DANGER -> Icons.Default.Dangerous
    }

    val containerColor by animateColorAsState(targetValue = family.colorContainer, label = "verdictContainer")
    val contentColor by animateColorAsState(targetValue = family.onColorContainer, label = "verdictContent")

    val accessibilityLabel = buildString {
        append("Safety Verdict: ${safetyLevel.label}. ${safetyLevel.shortActionTitle}.")
        if (isOfflineFallback) {
            append(" Evaluated using local offline keyword matcher.")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                liveRegion = LiveRegionMode.Assertive
                heading()
            },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(2.dp, family.color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = family.color,
                contentColor = family.onColor,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = safetyLevel.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor
                    )
                    if (isOfflineFallback) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = family.color,
                            contentColor = family.onColor
                        ) {
                            Text(
                                text = "LOCAL MATCH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = safetyLevel.shortActionTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Compact multimodal safety badge for lists and history cards (NFR-NT2).
 */
@Composable
fun SafetyLevelChip(
    safetyLevel: SafetyLevel,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val family = semanticColors.forSafetyLevel(safetyLevel)
    val icon: ImageVector = when (safetyLevel) {
        SafetyLevel.SAFE -> Icons.Default.CheckCircle
        SafetyLevel.CAUTION -> Icons.Default.WarningAmber
        SafetyLevel.DANGER -> Icons.Default.Dangerous
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = "Safety level ${safetyLevel.label}"
        },
        shape = RoundedCornerShape(8.dp),
        color = family.colorContainer,
        contentColor = family.onColorContainer,
        border = BorderStroke(1.5.dp, family.color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = family.onColorContainer
            )
            Text(
                text = safetyLevel.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = family.onColorContainer
            )
        }
    }
}

/**
 * NFR-NT1: Eliminates static loading spinners.
 * Communicates progressive scanner states ("Focusing on label...", "Extracting text...", "Auditing with BiteGuard AI...").
 */
@Composable
fun ProgressiveScannerStatusBanner(
    stageText: String,
    detectedBlocksCount: Int = 0,
    isReadyToScan: Boolean = false,
    isStreaming: Boolean = false,
    isThrottled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val family = when {
        isReadyToScan -> semanticColors.safe
        isStreaming -> semanticColors.info
        else -> semanticColors.caution
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = stageText
            },
        shape = RoundedCornerShape(14.dp),
        color = family.colorContainer,
        contentColor = family.onColorContainer,
        border = BorderStroke(1.5.dp, family.color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isReadyToScan) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = family.onColorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stageText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = family.onColorContainer
                    )
                }

                if (detectedBlocksCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = family.color,
                        contentColor = family.onColor
                    ) {
                        Text(
                            text = if (isThrottled) "$detectedBlocksCount blocks • Eco 5 FPS" else "$detectedBlocksCount blocks",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (isStreaming) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = family.color,
                    trackColor = family.onColor
                )
            }
        }
    }
}

/**
 * FR-04 & Accessibility: Clear, actionable Error / Offline Fallback decision card.
 * Helps users with disabilities immediately understand what happened, why, and choose a clear next action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionableFailureCard(
    errorKind: AuditErrorKind,
    errorMessage: String,
    retryAttempt: Int,
    hasOfflineFallback: Boolean,
    onRetryCloudAudit: () -> Unit,
    onUseOfflineMatch: (() -> Unit)? = null,
    onRetakeOrManualEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val family = if (hasOfflineFallback) semanticColors.caution else semanticColors.danger

    val (title, explanation, icon) = when (errorKind) {
        AuditErrorKind.NETWORK_TIMEOUT -> Triple(
            "Network Offline — Showing Basic Local Match",
            "Cloud AI did not respond within 5 seconds. We automatically audited your label on-device using your saved allergen & health rules.",
            Icons.Default.CloudOff
        )
        AuditErrorKind.API_KEY_MISSING -> Triple(
            "Cloud AI Key Not Configured — Local Mode Active",
            "GEMINI_API_KEY is not set in local.properties. BiteGuard AI is running in 100% offline rule-based mode.",
            Icons.Default.KeyOff
        )
        AuditErrorKind.NO_TEXT_DETECTED -> Triple(
            "No Ingredient Text Detected",
            "The camera could not find a clear 'Ingredients:' section. Move closer (15–20 cm), improve lighting, or enter ingredients manually.",
            Icons.Default.CameraAlt
        )
        AuditErrorKind.MALFORMED_AI_RESPONSE -> Triple(
            "AI Response Was Incomplete",
            "The streaming response was interrupted. We recovered the scan using local allergen rules so you can still make a safe decision.",
            Icons.Default.Build
        )
        AuditErrorKind.CAMERA_UNAVAILABLE -> Triple(
            "Camera Permission or Hardware Needed",
            "BiteGuard AI needs camera access to scan labels in real time, or you can paste/type an ingredient list directly.",
            Icons.Default.ErrorOutline
        )
        AuditErrorKind.UNKNOWN -> Triple(
            "Cloud Audit Interrupted",
            errorMessage.ifBlank { "An unexpected error occurred during cloud evaluation. Local rule matching is available below." },
            Icons.Default.ErrorOutline
        )
    }

    val backoffSeconds = if (retryAttempt <= 0) 0 else (1 shl retryAttempt.coerceAtMost(4))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "$title. $explanation"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = family.colorContainer,
            contentColor = family.onColorContainer
        ),
        border = BorderStroke(2.dp, family.color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = family.color,
                    contentColor = family.onColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = family.onColorContainer
                    )
                    if (retryAttempt > 0) {
                        Text(
                            text = "Retry attempt #$retryAttempt (Exponential backoff: ${backoffSeconds}s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = family.onColorContainer
                        )
                    }
                }
            }

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = family.onColorContainer
            )

            // Action buttons with min 48.dp touch target height for motor accessibility
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRetryCloudAudit,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = family.color,
                        contentColor = family.onColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (retryAttempt > 0) "Retry Cloud AI (#${retryAttempt + 1})" else "Retry Cloud AI",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onUseOfflineMatch != null) {
                    FilledTonalButton(
                        onClick = onUseOfflineMatch,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Keep Local Match",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (onRetakeOrManualEdit != null) {
                    OutlinedButton(
                        onClick = onRetakeOrManualEdit,
                        modifier = Modifier.heightIn(min = 48.dp),
                        border = BorderStroke(1.5.dp, family.onColorContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = family.onColorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit / Paste Text",
                            color = family.onColorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Itemized FlaggedIngredient row with multimodal severity badge (FR-03 & NFR-NT2).
 */
@Composable
fun FlaggedIngredientCard(
    ingredient: FlaggedIngredient,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val (family, icon) = when (ingredient.severity) {
        Severity.HIGH -> semanticColors.danger to Icons.Default.Dangerous
        Severity.MEDIUM -> semanticColors.caution to Icons.Default.WarningAmber
        Severity.LOW -> semanticColors.info to Icons.Default.Info
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${ingredient.name}, ${ingredient.severity.label}. Reason: ${ingredient.riskReason}"
            },
        shape = RoundedCornerShape(12.dp),
        color = family.colorContainer,
        contentColor = family.onColorContainer,
        border = BorderStroke(1.5.dp, family.color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = family.onColorContainer,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ingredient.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = family.onColorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = family.color,
                        contentColor = family.onColor
                    ) {
                        Text(
                            text = ingredient.severity.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ingredient.riskReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = family.onColorContainer
                )
            }
        }
    }
}

/**
 * FR-04: Displays raw MLKit extracted text with high-contrast keyword highlights
 * corresponding to flagged ingredients / allergens.
 */
@Composable
fun HighlightedRawTextCard(
    rawText: String,
    highlightedKeywords: List<String>,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val dangerFamily = semanticColors.danger

    val annotatedString = buildAnnotatedString {
        if (rawText.isBlank()) {
            append("No raw OCR text captured.")
            return@buildAnnotatedString
        }
        val cleanKeywords = highlightedKeywords
            .map { it.trim() }
            .filter { it.length >= 3 }
            .sortedByDescending { it.length }

        if (cleanKeywords.isEmpty()) {
            append(rawText)
        } else {
            val regex = Regex(
                cleanKeywords.joinToString("|") { Regex.escape(it) },
                RegexOption.IGNORE_CASE
            )
            var lastIndex = 0
            for (match in regex.findAll(rawText)) {
                if (match.range.first > lastIndex) {
                    append(rawText.substring(lastIndex, match.range.first))
                }
                withStyle(
                    SpanStyle(
                        background = dangerFamily.colorContainer,
                        color = dangerFamily.onColorContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append(" [${match.value.uppercase()}] ")
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < rawText.length) {
                append(rawText.substring(lastIndex))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Extracted Label Text (Flagged terms bracketed & highlighted)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * FR-06: Binary feedback card (Thumbs Up / Thumbs Down) with latency telemetry display.
 */
@Composable
fun VerdictFeedbackRow(
    ttftMs: Long,
    totalDurationMs: Long,
    selectedFeedback: UserFeedback?,
    onSubmitFeedback: (UserFeedback) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedFeedback == null) "Was this verdict accurate?" else "Feedback queued for telemetry",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "TTFT: ${ttftMs}ms • Total: ${totalDurationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val upSelected = selectedFeedback == UserFeedback.THUMBS_UP
                val downSelected = selectedFeedback == UserFeedback.THUMBS_DOWN

                OutlinedButton(
                    onClick = { onSubmitFeedback(UserFeedback.THUMBS_UP) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (upSelected) semanticColors.safe.colorContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (upSelected) semanticColors.safe.onColorContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        width = if (upSelected) 2.dp else 1.dp,
                        color = if (upSelected) semanticColors.safe.color else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "Accurate verdict, thumbs up",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Helpful")
                }

                OutlinedButton(
                    onClick = { onSubmitFeedback(UserFeedback.THUMBS_DOWN) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (downSelected) semanticColors.danger.colorContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (downSelected) semanticColors.danger.onColorContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        width = if (downSelected) 2.dp else 1.dp,
                        color = if (downSelected) semanticColors.danger.color else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = "Inaccurate verdict, thumbs down",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report")
                }
            }
        }
    }
}

/**
 * NFR-NT4: Mandatory Persistent Legal & Medical Disclaimer.
 */
@Composable
fun MedicalDisclaimerBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Medical Disclaimer: BiteGuard AI is an informational decision-support tool. It does not replace professional medical advice, diagnosis, or official product package verification."
            },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "BiteGuard AI is an informational decision-support tool. It does not replace professional medical advice, diagnosis, or official product package verification.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
