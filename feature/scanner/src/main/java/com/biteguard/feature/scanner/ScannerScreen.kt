package com.biteguard.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biteguard.core.data.fallback.LocalFallbackAuditEngine
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.StreamState
import com.biteguard.core.ui.components.ActionableFailureCard
import com.biteguard.core.ui.components.FlaggedIngredientCard
import com.biteguard.core.ui.components.HighlightedRawTextCard
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.ProgressiveScannerStatusBanner
import com.biteguard.core.ui.components.SafetyVerdictBanner
import com.biteguard.core.ui.components.VerdictFeedbackRow
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * FR-02, FR-03, FR-04, NFR-NT1, NFR-NT2: Real-Time CameraX Edge OCR Scanner,
 * Bounding Box Overlay, Typewriter Streaming Audit Result, and Accessible Decision Actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    onIntent: (ScannerIntent) -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semanticColors = LocalBiteGuardSemanticColors.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraXEdgePreviewWithOverlays(
                state = state,
                onFrameAnalyzed = { onIntent(ScannerIntent.OnEdgeFrameAnalyzed(it)) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CameraPermissionFallbackContent(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenManualInput = { onIntent(ScannerIntent.ToggleManualInputSheet(true)) },
                onRunPreset = { preset ->
                    onIntent(ScannerIntent.TriggerSampleOrManualAudit(preset.rawIngredients))
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top HUD: Active Profile Summary + Progressive Scanner Status (NFR-NT1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActiveProfileHeaderBar(
                allergenCount = state.profile.selectedAllergens.size,
                goalCount = state.profile.healthGoals.size,
                onConfigureClick = onNavigateToProfile
            )

            ProgressiveScannerStatusBanner(
                stageText = state.progressiveStatusText,
                detectedBlocksCount = state.liveBoxes.size,
                isReadyToScan = state.isReadyToScan,
                isStreaming = state.streamState is StreamState.Streaming,
                isThrottled = state.isThrottled
            )
        }

        // Bottom Controls & Live Audit Result Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Show active Streaming / Success / Error card
            when (val stream = state.streamState) {
                is StreamState.Streaming -> {
                    StreamingTypewriterCard(
                        stageMessage = stream.stageMessage,
                        accumulatedSummary = stream.partialVerdict.accumulatedSummary,
                        partialSafetyLevel = stream.partialVerdict.partialSafetyLevel,
                        onCancel = { onIntent(ScannerIntent.DismissAuditResult) }
                    )
                }

                is StreamState.Success -> {
                    CompletedVerdictSheetCard(
                        verdict = stream.data,
                        profile = state.profile,
                        onFeedback = { fb -> onIntent(ScannerIntent.SubmitFeedback(stream.data, fb)) },
                        onScanAnother = { onIntent(ScannerIntent.DismissAuditResult) }
                    )
                }

                is StreamState.Error -> {
                    ErrorAndFallbackResultCard(
                        errorState = stream,
                        profile = state.profile,
                        onRetryCloud = { onIntent(ScannerIntent.RetryFailedAuditWithBackoff) },
                        onKeepOfflineMatch = { onIntent(ScannerIntent.AcceptOfflineFallbackVerdict) },
                        onManualEdit = {
                            onIntent(ScannerIntent.ToggleManualInputSheet(true, stream.cachedRawText))
                        },
                        onDismiss = { onIntent(ScannerIntent.DismissAuditResult) }
                    )
                }

                StreamState.Idle, is StreamState.ScanningEdge -> {
                    // Ready-to-Scan Callout & Capture Action Bar
                    ScannerActionControls(
                        isReadyToScan = state.isReadyToScan,
                        hasDetectedText = state.latestOcrText.isNotBlank(),
                        hasCameraPermission = hasCameraPermission,
                        onAuditCameraFrame = { onIntent(ScannerIntent.TriggerCaptureAudit) },
                        onOpenManualInput = { onIntent(ScannerIntent.ToggleManualInputSheet(true)) },
                        onRunSamplePreset = { preset ->
                            onIntent(ScannerIntent.TriggerSampleOrManualAudit(preset.rawIngredients))
                        }
                    )
                }
            }
        }
    }

    // Manual Ingredient Entry / Simulator Bottom Sheet (Accessibility & Emulator support)
    if (state.showManualInputSheet) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(ScannerIntent.ToggleManualInputSheet(false)) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Manual Label Audit & Test Presets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Edit captured OCR text, paste an ingredient label, or tap a realistic package sample below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.manualInputText,
                        onValueChange = { onIntent(ScannerIntent.UpdateManualInputText(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp),
                        label = { Text("Package Ingredient Text") },
                        placeholder = {
                            Text("Ingredients: Enriched wheat flour, arachis oil, whey protein...")
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onIntent(ScannerIntent.TriggerSampleOrManualAudit(state.manualInputText))
                            },
                            enabled = state.manualInputText.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = semanticColors.safe.color,
                                contentColor = semanticColors.safe.onColor
                            )
                        ) {
                            Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Audit Label Now", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                item {
                    Text(
                        text = "Quick Sample Package Labels",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(ScannerViewModel.SAMPLE_PRESETS) { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onIntent(ScannerIntent.TriggerSampleOrManualAudit(preset.rawIngredients))
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = null,
                                    tint = semanticColors.info.color,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = semanticColors.info.color
                            )
                            Text(
                                text = preset.rawIngredients,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraXEdgePreviewWithOverlays(
    state: ScannerUiState,
    onFrameAnalyzed: (EdgeOcrFrameResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val semanticColors = LocalBiteGuardSemanticColors.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val analyzer = remember {
        EdgeTextAnalyzer(onResult = onFrameAnalyzed)
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // FR-02: Real-Time Bounding Box Overlay mapped from ImageProxy space to FILL_CENTER Canvas space
        val safeColor = semanticColors.safe.color
        val safeContainer = semanticColors.safe.colorContainer.copy(alpha = 0.25f)
        val infoColor = semanticColors.info.color
        val infoContainer = semanticColors.info.colorContainer.copy(alpha = 0.18f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val imgW = state.frameWidth.toFloat().coerceAtLeast(1f)
            val imgH = state.frameHeight.toFloat().coerceAtLeast(1f)
            val scale = max(size.width / imgW, size.height / imgH)
            val offsetX = (size.width - imgW * scale) / 2f
            val offsetY = (size.height - imgH * scale) / 2f

            for (box in state.liveBoxes) {
                val left = box.boundingBox.left * scale + offsetX
                val top = box.boundingBox.top * scale + offsetY
                val right = box.boundingBox.right * scale + offsetX
                val bottom = box.boundingBox.bottom * scale + offsetY
                val rectSize = Size((right - left).coerceAtLeast(8f), (bottom - top).coerceAtLeast(8f))

                val strokeColor = if (box.containsIngredientKeyword) safeColor else infoColor
                val fillColor = if (box.containsIngredientKeyword) safeContainer else infoContainer

                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(left, top),
                    size = rectSize,
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(left, top),
                    size = rectSize,
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(width = if (box.containsIngredientKeyword) 7f else 4f)
                )
            }
        }
    }
}

@Composable
private fun ActiveProfileHeaderBar(
    allergenCount: Int,
    goalCount: Int,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Guard Profile",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$allergenCount Allergen(s) • $goalCount Health Goal(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = onConfigureClick,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScannerActionControls(
    isReadyToScan: Boolean,
    hasDetectedText: Boolean,
    hasCameraPermission: Boolean,
    onAuditCameraFrame: () -> Unit,
    onOpenManualInput: () -> Unit,
    onRunSamplePreset: (SamplePackagePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val buttonFamily = if (isReadyToScan) semanticColors.safe else semanticColors.info

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        border = BorderStroke(2.dp, buttonFamily.color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasCameraPermission) {
                ExtendedFloatingActionButton(
                    onClick = onAuditCameraFrame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics {
                            contentDescription = if (isReadyToScan) {
                                "Ready to Scan. Tap to audit detected ingredient list with BiteGuard AI"
                            } else {
                                "Audit current camera text with BiteGuard AI"
                            }
                        },
                    containerColor = buttonFamily.color,
                    contentColor = buttonFamily.onColor,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    text = {
                        Text(
                            text = when {
                                isReadyToScan -> "READY TO SCAN — AUDIT INGREDIENTS"
                                hasDetectedText -> "AUDIT DETECTED TEXT NOW"
                                else -> "SCAN LABEL / CAPTURE FRAME"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenManualInput,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Type / Paste Label", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { onRunSamplePreset(ScannerViewModel.SAMPLE_PRESETS.first()) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Demo Sample", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * NFR-NT1: Streaming visual feedback with live typewriter text reveal.
 */
@Composable
private fun StreamingTypewriterCard(
    stageMessage: String,
    accumulatedSummary: String,
    partialSafetyLevel: com.biteguard.core.model.SafetyLevel?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visibleCharCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(accumulatedSummary) {
        while (visibleCharCount < accumulatedSummary.length) {
            val step = ((accumulatedSummary.length - visibleCharCount) / 4).coerceAtLeast(2)
            visibleCharCount = (visibleCharCount + step).coerceAtMost(accumulatedSummary.length)
            delay(18L)
        }
    }

    val typewriterText = accumulatedSummary.take(visibleCharCount)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
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
                Text(
                    text = stageMessage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onCancel) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel audit")
                }
            }

            if (partialSafetyLevel != null) {
                SafetyVerdictBanner(safetyLevel = partialSafetyLevel)
            }

            Text(
                text = if (typewriterText.isBlank()) {
                    "Analyzing ingredients against your allergen and health profile..."
                } else {
                    "$typewriterText▌"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CompletedVerdictSheetCard(
    verdict: AuditVerdict,
    profile: com.biteguard.core.model.UserDietaryProfile,
    onFeedback: ( com.biteguard.core.model.UserFeedback) -> Unit,
    onScanAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val family = semanticColors.forSafetyLevel(verdict.safetyLevel)

    var visibleChars by remember(verdict.id) { mutableIntStateOf(0) }
    LaunchedEffect(verdict.id, verdict.summary) {
        while (visibleChars < verdict.summary.length) {
            val step = ((verdict.summary.length - visibleChars) / 3).coerceAtLeast(4)
            visibleChars = (visibleChars + step).coerceAtMost(verdict.summary.length)
            delay(14L)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(2.5.dp, family.color)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SafetyVerdictBanner(
                    safetyLevel = verdict.safetyLevel,
                    isOfflineFallback = verdict.isOfflineFallback
                )
            }

            item {
                Text(
                    text = verdict.summary.take(visibleChars),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (verdict.flaggedIngredients.isNotEmpty()) {
                item {
                    Text(
                        text = "Flagged Ingredients (${verdict.flaggedIngredients.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                items(verdict.flaggedIngredients) { flag ->
                    FlaggedIngredientCard(ingredient = flag)
                }
            }

            item {
                val highlights = remember(verdict, profile) {
                    LocalFallbackAuditEngine.extractHighlightTerms(verdict, profile)
                }
                HighlightedRawTextCard(
                    rawText = verdict.rawExtractedText,
                    highlightedKeywords = highlights
                )
            }

            item {
                VerdictFeedbackRow(
                    ttftMs = verdict.ttftMs,
                    totalDurationMs = verdict.totalStreamDurationMs,
                    selectedFeedback = verdict.userFeedback,
                    onSubmitFeedback = onFeedback
                )
            }

            item {
                MedicalDisclaimerBanner()
            }

            item {
                Button(
                    onClick = onScanAnother,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = family.color,
                        contentColor = family.onColor
                    )
                ) {
                    Text("Scan Next Package", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * FR-04: Clear Error & Offline Fallback decision UI showing the ActionableFailureCard,
 * immediate local verdict, and highlighted raw text so the user can decide safely.
 */
@Composable
private fun ErrorAndFallbackResultCard(
    errorState: StreamState.Error,
    profile: com.biteguard.core.model.UserDietaryProfile,
    onRetryCloud: () -> Unit,
    onKeepOfflineMatch: () -> Unit,
    onManualEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fallback = errorState.fallbackVerdict

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 540.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ActionableFailureCard(
                    errorKind = errorState.errorKind,
                    errorMessage = errorState.throwable.message.orEmpty(),
                    retryAttempt = errorState.retryAttempt,
                    hasOfflineFallback = fallback != null,
                    onRetryCloudAudit = onRetryCloud,
                    onUseOfflineMatch = if (fallback != null) onKeepOfflineMatch else null,
                    onRetakeOrManualEdit = onManualEdit
                )
            }

            if (fallback != null) {
                item {
                    SafetyVerdictBanner(
                        safetyLevel = fallback.safetyLevel,
                        isOfflineFallback = true
                    )
                }

                item {
                    Text(
                        text = fallback.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (fallback.flaggedIngredients.isNotEmpty()) {
                    items(fallback.flaggedIngredients) { flag ->
                        FlaggedIngredientCard(ingredient = flag)
                    }
                }

                item {
                    val highlights = remember(fallback, profile) {
                        LocalFallbackAuditEngine.extractHighlightTerms(fallback, profile)
                    }
                    HighlightedRawTextCard(
                        rawText = fallback.rawExtractedText,
                        highlightedKeywords = highlights
                    )
                }
            }

            item {
                MedicalDisclaimerBanner()
            }

            item {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Back to Live Camera", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionFallbackContent(
    onRequestPermission: () -> Unit,
    onOpenManualInput: () -> Unit,
    onRunPreset: (SamplePackagePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 140.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ActionableFailureCard(
            errorKind = AuditErrorKind.CAMERA_UNAVAILABLE,
            errorMessage = "Grant camera permission for real-time OCR bounding boxes, or test immediately with manual text or sample labels.",
            retryAttempt = 0,
            hasOfflineFallback = true,
            onRetryCloudAudit = onRequestPermission,
            onRetakeOrManualEdit = onOpenManualInput
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = semanticColors.safe.color,
                contentColor = semanticColors.safe.onColor
            )
        ) {
            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Camera Permission", fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        FilledTonalButton(
            onClick = { onRunPreset(ScannerViewModel.SAMPLE_PRESETS.first()) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(imageVector = Icons.Default.Science, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run Sample Label Audit (No Camera Needed)", fontWeight = FontWeight.Bold)
        }
    }
}
