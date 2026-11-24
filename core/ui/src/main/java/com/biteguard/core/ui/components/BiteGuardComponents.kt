package com.biteguard.core.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.graphics.Rect
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.FlaggedIngredient
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.model.UserFeedback
import com.biteguard.core.ui.R
import com.biteguard.core.ui.localization.localizedActionTitle
import com.biteguard.core.ui.localization.localizedLabel
import androidx.compose.ui.res.stringResource
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors
import kotlin.math.cos
import kotlin.math.sin

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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = safetyLevel.localizedLabel(),
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
                                text = stringResource(R.string.local_match),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = safetyLevel.localizedActionTitle(),
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
                text = safetyLevel.localizedLabel(),
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
            "Cloud AI Key Invalid or Missing — Local Mode Active",
            errorMessage.ifBlank {
                "GEMINI_API_KEY in local.properties is missing or rejected by Google AI Studio (supports both new 'AQ.' Authentication Keys and 'AIza' keys). BiteGuard AI is running in 100% offline rule-based mode."
            },
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
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Keep Local Match",
                            fontWeight = FontWeight.Bold
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
 * Supports optional [badgeNumber] that corresponds to clustered highlight boxes on the package image.
 */
@Composable
fun FlaggedIngredientCard(
    ingredient: FlaggedIngredient,
    modifier: Modifier = Modifier,
    badgeNumber: Int? = null
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
            if (badgeNumber != null) {
                Surface(
                    shape = CircleShape,
                    color = family.color,
                    contentColor = family.onColor,
                    modifier = Modifier
                        .size(26.dp)
                        .padding(top = 1.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$badgeNumber",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = family.onColor
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = family.onColorContainer,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                )
            }
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
                            text = ingredient.severity.localizedLabel().uppercase(),
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

    val annotatedString = androidx.compose.runtime.remember(
        rawText,
        highlightedKeywords,
        dangerFamily.colorContainer,
        dangerFamily.onColorContainer
    ) {
        buildAnnotatedString {
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
 * Uses a clean vertical Column layout so the title and buttons never crowd each other horizontally.
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (selectedFeedback == null) stringResource(R.string.was_verdict_accurate) else stringResource(R.string.feedback_queued),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "TTFT: ${ttftMs}ms • Total: ${totalDurationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val upSelected = selectedFeedback == UserFeedback.THUMBS_UP
                val downSelected = selectedFeedback == UserFeedback.THUMBS_DOWN

                OutlinedButton(
                    onClick = { onSubmitFeedback(UserFeedback.THUMBS_UP) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
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
                    Text(
                        text = stringResource(R.string.btn_helpful),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = { onSubmitFeedback(UserFeedback.THUMBS_DOWN) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
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
                    Text(
                        text = stringResource(R.string.btn_report),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
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
                contentDescription = "Medical Disclaimer notice"
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
                text = stringResource(R.string.medical_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Reusable Robot Lottie Animation (`res/raw/robot.json`) used on the Welcome Screen
 * and in the Offline / Model Not Loading AlertDialog.
 */
@Composable
fun RobotLottieAnimation(
    modifier: Modifier = Modifier,
    contentDescriptionLabel: String = "BiteGuard AI Guardian Robot Animation"
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.robot)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    Box(
        modifier = modifier.semantics {
            contentDescription = contentDescriptionLabel
        },
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Adaptive Material 3 Background with soft radial-modern ambient accents for Welcome and Onboarding screens.
 * Automatically adapts to system Light Mode, Dark Mode, and Ultra High-Contrast accessibility settings.
 */
@Composable
fun RadialModernAnimatedBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val baseBackground = MaterialTheme.colorScheme.background
    val primaryGlow = semanticColors.safe.color.copy(
        alpha = if (semanticColors.isHighContrast) 0.14f else 0.10f
    )
    val secondaryGlow = semanticColors.info.color.copy(
        alpha = if (semanticColors.isHighContrast) 0.12f else 0.08f
    )

    val infiniteTransition = rememberInfiniteTransition(label = "radialModernBg")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension.coerceAtLeast(1f)
            val baseRadius = minDim * 0.65f * pulseScale

            val center1 = Offset(
                x = size.width * (0.30f + 0.10f * cos(orbitAngle)),
                y = size.height * (0.28f + 0.08f * sin(orbitAngle))
            )
            val center2 = Offset(
                x = size.width * (0.72f + 0.10f * sin(orbitAngle)),
                y = size.height * (0.52f + 0.08f * cos(orbitAngle))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = center1,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center1
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = center2,
                    radius = baseRadius * 0.9f
                ),
                radius = baseRadius * 0.9f,
                center = center2
            )
        }

        content()
    }
}

/**
 * Material 3 Expandable Accordion Card for organizing long lists without crowding the screen.
 */
@Composable
fun BiteGuardAccordion(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    leadingIcon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "accordionChevron"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (expanded) 1.5.dp else 1.dp,
            color = if (expanded) accentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics {
                        heading()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (leadingIcon != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accentColor.copy(alpha = 0.14f),
                            contentColor = accentColor,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = leadingIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            contentColor = accentColor
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse section" else "Expand section",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(240)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(2.dp))
                    content()
                }
            }
        }
    }
}

/**
 * Material 3 AlertDialog with Robot Lottie Animation (`robot.json`) displayed when the cloud AI model
 * is offline or fails to load, reassuring the user that BiteGuard's on-device local guardian is active.
 */
@Composable
fun OfflineModelLottieAlertDialog(
    errorKind: AuditErrorKind,
    fallbackVerdict: AuditVerdict?,
    retryAttempt: Int,
    onAcceptLocalVerdict: () -> Unit,
    onRetryCloudModel: () -> Unit,
    onDismiss: () -> Unit
) {
    val semanticColors = LocalBiteGuardSemanticColors.current

    val title = when (errorKind) {
        AuditErrorKind.API_KEY_MISSING -> "Cloud AI Offline — Local Guardian Active"
        AuditErrorKind.MALFORMED_AI_RESPONSE -> "AI Stream Interrupted — Local Backup Ready"
        else -> "BiteGuard Cloud AI is Offline"
    }

    val message = when (errorKind) {
        AuditErrorKind.API_KEY_MISSING ->
            "Our cloud AI model isn't connected right now, so BiteGuard's on-device allergen engine audited this label offline to keep every bite safe."
        AuditErrorKind.MALFORMED_AI_RESPONSE ->
            "The cloud AI response didn't finish loading cleanly. Don't worry — our built-in allergen & dietary rule engine evaluated the ingredients on your device."
        else ->
            "We couldn't reach the BiteGuard AI cloud model in time. Your safety comes first, so our offline allergen guardian audited the label directly on your device."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(124.dp)
            ) {
                RobotLottieAnimation(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentDescriptionLabel = "BiteGuard Robot Offline Guardian Animation"
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (fallbackVerdict != null) {
                    SafetyVerdictBanner(
                        safetyLevel = fallbackVerdict.safetyLevel,
                        isOfflineFallback = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = if (fallbackVerdict != null) onAcceptLocalVerdict else onRetryCloudModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = semanticColors.safe.color,
                    contentColor = semanticColors.safe.onColor
                )
            ) {
                Text(
                    text = if (fallbackVerdict != null) {
                        "Use On-Device Safety Verdict"
                    } else {
                        "Retry Cloud AI"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Inspect Details")
                }
                if (fallbackVerdict != null) {
                    OutlinedButton(
                        onClick = onRetryCloudModel,
                        modifier = Modifier.heightIn(min = 42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (retryAttempt > 0) "Retry Cloud (#${retryAttempt + 1})" else "Retry Cloud",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}

/**
 * Represents a highlighted allergen or warning cluster projected onto the captured package image.
 */
data class ImageHighlightBox(
    val index: Int,
    val label: String,
    val boundingBox: Rect,
    val color: Color
)

/**
 * Interactive full-screen Click-to-Zoom & Pinch-to-Zoom Dialog for inspecting captured package photos
 * in the Audit Log and Audit Result screens, with optional numbered allergen/warning highlight boxes.
 */
@Composable
fun ZoomableImageDialog(
    imageBitmap: ImageBitmap,
    title: String,
    subtitle: String? = null,
    highlightBoxes: List<ImageHighlightBox> = emptyList(),
    imageWidth: Int = 1080,
    imageHeight: Int = 1920,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle ?: "Pinch or double-tap to zoom (${String.format("%.1fx", scale)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (scale > 1.05f) {
                            OutlinedButton(
                                onClick = {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                                modifier = Modifier.heightIn(min = 38.dp)
                            ) {
                                Text("Reset 1x", fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close zoomed image"
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.3f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Zoomable package image for $title",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (highlightBoxes.isNotEmpty()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val imgW = imageWidth.toFloat().coerceAtLeast(1f)
                                val imgH = imageHeight.toFloat().coerceAtLeast(1f)
                                val scaleFactor = minOf(size.width / imgW, size.height / imgH)
                                val oX = (size.width - imgW * scaleFactor) / 2f
                                val oY = (size.height - imgH * scaleFactor) / 2f

                                highlightBoxes.forEach { box ->
                                    val left = box.boundingBox.left * scaleFactor + oX
                                    val top = box.boundingBox.top * scaleFactor + oY
                                    val right = box.boundingBox.right * scaleFactor + oX
                                    val bottom = box.boundingBox.bottom * scaleFactor + oY
                                    val rectSize = Size((right - left).coerceAtLeast(24f), (bottom - top).coerceAtLeast(16f))

                                    drawRoundRect(
                                        color = box.color.copy(alpha = 0.28f),
                                        topLeft = Offset(left, top),
                                        size = rectSize,
                                        cornerRadius = CornerRadius(8f, 8f)
                                    )
                                    drawRoundRect(
                                        color = box.color,
                                        topLeft = Offset(left, top),
                                        size = rectSize,
                                        cornerRadius = CornerRadius(8f, 8f),
                                        style = Stroke(width = 4f)
                                    )
                                }
                            }

                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val imgW = imageWidth.toFloat().coerceAtLeast(1f)
                                val imgH = imageHeight.toFloat().coerceAtLeast(1f)
                                val scaleFactor = minOf(maxWidth.value / imgW, maxHeight.value / imgH)
                                val oX = (maxWidth.value - imgW * scaleFactor) / 2f
                                val oY = (maxHeight.value - imgH * scaleFactor) / 2f

                                highlightBoxes.forEach { box ->
                                    val badgeX = (box.boundingBox.left * scaleFactor + oX - 8f).coerceAtLeast(4f)
                                    val badgeY = (box.boundingBox.top * scaleFactor + oY - 8f).coerceAtLeast(4f)
                                    Surface(
                                        shape = CircleShape,
                                        color = box.color,
                                        contentColor = Color.White,
                                        border = BorderStroke(1.5.dp, Color.White),
                                        modifier = Modifier
                                            .offset(x = badgeX.dp, y = badgeY.dp)
                                            .size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${box.index}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tip: Double-tap or pinch with two fingers to inspect fine print",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onDismiss) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Reusable card showing a captured package snapshot with a "Tap to Zoom" badge overlay
 * and optional numbered colored highlight clusters mapped directly to flagged allergens.
 * Decodes byte arrays on `Dispatchers.Default` and opens [ZoomableImageDialog] when clicked.
 */
@Composable
fun ClickableCapturedImageCard(
    thumbnailBytes: ByteArray?,
    title: String,
    subtitle: String? = null,
    highlightBoxes: List<ImageHighlightBox> = emptyList(),
    imageWidth: Int = 1080,
    imageHeight: Int = 1920,
    modifier: Modifier = Modifier
) {
    val decodedBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = thumbnailBytes
    ) {
        value = if (thumbnailBytes != null && thumbnailBytes.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                runCatching {
                    BitmapFactory.decodeByteArray(thumbnailBytes, 0, thumbnailBytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        } else {
            null
        }
    }

    val bitmap = decodedBitmap ?: return
    var showZoomDialog by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { showZoomDialog = true }
            .semantics {
                contentDescription = "Captured package photo for $title. Tap to open full-screen zoom."
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Captured package snapshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (highlightBoxes.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val imgW = imageWidth.toFloat().coerceAtLeast(1f)
                    val imgH = imageHeight.toFloat().coerceAtLeast(1f)
                    val scale = maxOf(size.width / imgW, size.height / imgH)
                    val offsetX = (size.width - imgW * scale) / 2f
                    val offsetY = (size.height - imgH * scale) / 2f

                    highlightBoxes.forEach { box ->
                        val left = box.boundingBox.left * scale + offsetX
                        val top = box.boundingBox.top * scale + offsetY
                        val right = box.boundingBox.right * scale + offsetX
                        val bottom = box.boundingBox.bottom * scale + offsetY
                        val rectSize = Size((right - left).coerceAtLeast(24f), (bottom - top).coerceAtLeast(16f))

                        drawRoundRect(
                            color = box.color.copy(alpha = 0.28f),
                            topLeft = Offset(left, top),
                            size = rectSize,
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawRoundRect(
                            color = box.color,
                            topLeft = Offset(left, top),
                            size = rectSize,
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = 4f)
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val imgW = imageWidth.toFloat().coerceAtLeast(1f)
                    val imgH = imageHeight.toFloat().coerceAtLeast(1f)
                    val scale = maxOf(maxWidth.value / imgW, maxHeight.value / imgH)
                    val offsetX = (maxWidth.value - imgW * scale) / 2f
                    val offsetY = (maxHeight.value - imgH * scale) / 2f

                    highlightBoxes.forEach { box ->
                        val badgeX = (box.boundingBox.left * scale + offsetX - 8f).coerceAtLeast(4f)
                        val badgeY = (box.boundingBox.top * scale + offsetY - 8f).coerceAtLeast(4f)
                        Surface(
                            shape = CircleShape,
                            color = box.color,
                            contentColor = Color.White,
                            border = BorderStroke(1.5.dp, Color.White),
                            modifier = Modifier
                                .offset(x = badgeX.dp, y = badgeY.dp)
                                .size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${box.index}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (highlightBoxes.isNotEmpty()) "${highlightBoxes.size} Warnings Mapped • Tap to Zoom" else "Tap to Zoom Image",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showZoomDialog) {
        ZoomableImageDialog(
            imageBitmap = bitmap,
            title = title,
            subtitle = subtitle,
            highlightBoxes = highlightBoxes,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            onDismiss = { showZoomDialog = false }
        )
    }
}


