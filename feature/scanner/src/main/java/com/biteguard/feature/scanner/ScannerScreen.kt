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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biteguard.core.model.AuditErrorKind
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.StreamState
import com.biteguard.core.ui.components.ActionableFailureCard
import com.biteguard.core.ui.components.BiteGuardAccordion
import com.biteguard.core.ui.components.ClickableCapturedImageCard
import com.biteguard.core.ui.components.FlaggedIngredientCard
import com.biteguard.core.ui.components.HighlightedRawTextCard
import com.biteguard.core.ui.components.ImageHighlightBox
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.OfflineModelLottieAlertDialog
import com.biteguard.core.ui.components.SafetyVerdictBanner
import com.biteguard.core.ui.components.VerdictFeedbackRow
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.max

private enum class ScannerStage {
    LIVE_CAMERA,
    SELECT_BLOCKS,
    AUDIT_RESULT
}

/**
 * FR-02, FR-03, FR-04, NFR-NT1, NFR-NT2:
 * 3-Stage Scanning Flow:
 * 1. LIVE_CAMERA: Calm viewfinder without flickering text boxes; user clicks Scan button to capture & freeze blocks.
 * 2. SELECT_BLOCKS: User chooses which frozen text block(s) to scan (via interactive image boxes or checklist).
 * 3. AUDIT_RESULT: Dedicated Result & Filtered Words Screen with click-to-zoom image and Offline Robot Lottie AlertDialog.
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

    val currentStage = when {
        state.streamState is StreamState.Streaming ||
            state.streamState is StreamState.Success ||
            state.streamState is StreamState.Error -> ScannerStage.AUDIT_RESULT
        state.isSelectingBlocks -> ScannerStage.SELECT_BLOCKS
        else -> ScannerStage.LIVE_CAMERA
    }

    AnimatedContent(
        targetState = currentStage,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
            }
        },
        label = "scannerThreeStageTransition",
        modifier = modifier.fillMaxSize()
    ) { stage ->
        when (stage) {
            ScannerStage.LIVE_CAMERA -> {
                // STAGE 1: Calm, uncluttered Live Camera Viewfinder (no shifting/flickering boxes)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
                        CameraXEdgePreviewWithOverlays(
                            isReadyToScan = state.isReadyToScan,
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

                    // Single Uncluttered Top HUD Pill: Live Scanner Status + Guard Profile Chip
                    CompactScannerTopHud(
                        stageText = state.progressiveStatusText,
                        allergenCount = state.profile.selectedAllergens.size,
                        goalCount = state.profile.healthGoals.size,
                        isReadyToScan = state.isReadyToScan,
                        isStreaming = false,
                        onProfileClick = onNavigateToProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    // Floating Bottom Capture & Sample Controls (only when camera permission is granted)
                    if (hasCameraPermission) {
                        ScannerActionControls(
                            isReadyToScan = state.isReadyToScan,
                            hasDetectedText = state.latestOcrText.isNotBlank(),
                            hasCameraPermission = true,
                            onAuditCameraFrame = { onIntent(ScannerIntent.CaptureFrameForBlockSelection) },
                            onOpenManualInput = { onIntent(ScannerIntent.ToggleManualInputSheet(true)) },
                            onRunSamplePreset = { preset ->
                                onIntent(ScannerIntent.TriggerSampleOrManualAudit(preset.rawIngredients))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }

            ScannerStage.SELECT_BLOCKS -> {
                // STAGE 2: Frozen Captured Image + Interactive Text Block Picker
                InteractiveBlockSelectionScreen(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ScannerStage.AUDIT_RESULT -> {
                // STAGE 3: Dedicated Audit Result & Filtered Words Screen
                DedicatedAuditResultScreen(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Manual Ingredient Entry / Simulator Bottom Sheet with M3 Accordion for presets
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
                        text = "Manual Label Audit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Edit captured OCR text or paste an ingredient label below.",
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
                            .heightIn(min = 120.dp),
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Package Ingredient Text") },
                        placeholder = {
                            Text("Ingredients: Enriched wheat flour, arachis oil, whey protein...")
                        }
                    )
                }

                item {
                    Button(
                        onClick = {
                            onIntent(ScannerIntent.TriggerSampleOrManualAudit(state.manualInputText))
                        },
                        enabled = state.manualInputText.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
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

                item {
                    BiteGuardAccordion(
                        title = "Sample Package Presets",
                        subtitle = "Test BiteGuard AI without a physical package",
                        badgeText = "${ScannerViewModel.SAMPLE_PRESETS.size} samples",
                        leadingIcon = Icons.Default.Science,
                        accentColor = semanticColors.info.color,
                        initiallyExpanded = false
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScannerViewModel.SAMPLE_PRESETS.forEach { preset ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onIntent(
                                                ScannerIntent.TriggerSampleOrManualAudit(
                                                    preset.rawIngredients
                                                )
                                            )
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = preset.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semanticColors.info.color
                                        )
                                    }
                                }
                            }
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
    isReadyToScan: Boolean,
    onFrameAnalyzed: (EdgeOcrFrameResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val semanticColors = LocalBiteGuardSemanticColors.current
    val latestOnFrameAnalyzed by rememberUpdatedState(onFrameAnalyzed)

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView mode ensures smooth slide/fade transitions inside AnimatedContent
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val analyzer = remember {
        EdgeTextAnalyzer(onResult = { result -> latestOnFrameAnalyzed(result) })
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
            analyzer.close()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        val safeColor = semanticColors.safe.color
        val guideColor = if (isReadyToScan) safeColor else Color.White.copy(alpha = 0.72f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Calm corner focus reticle in the middle of the live viewfinder (no flickering live boxes)
            val frameW = size.width * 0.78f
            val frameH = size.height * 0.34f
            val frameLeft = (size.width - frameW) / 2f
            val frameTop = (size.height - frameH) * 0.42f
            val cornerLen = 40f
            val strokeW = 7f

            // Top-left corner
            drawLine(guideColor, Offset(frameLeft, frameTop), Offset(frameLeft + cornerLen, frameTop), strokeW, StrokeCap.Round)
            drawLine(guideColor, Offset(frameLeft, frameTop), Offset(frameLeft, frameTop + cornerLen), strokeW, StrokeCap.Round)
            // Top-right corner
            drawLine(guideColor, Offset(frameLeft + frameW, frameTop), Offset(frameLeft + frameW - cornerLen, frameTop), strokeW, StrokeCap.Round)
            drawLine(guideColor, Offset(frameLeft + frameW, frameTop), Offset(frameLeft + frameW, frameTop + cornerLen), strokeW, StrokeCap.Round)
            // Bottom-left corner
            drawLine(guideColor, Offset(frameLeft, frameTop + frameH), Offset(frameLeft + cornerLen, frameTop + frameH), strokeW, StrokeCap.Round)
            drawLine(guideColor, Offset(frameLeft, frameTop + frameH), Offset(frameLeft, frameTop + frameH - cornerLen), strokeW, StrokeCap.Round)
            // Bottom-right corner
            drawLine(guideColor, Offset(frameLeft + frameW, frameTop + frameH), Offset(frameLeft + frameW - cornerLen, frameTop + frameH), strokeW, StrokeCap.Round)
            drawLine(guideColor, Offset(frameLeft + frameW, frameTop + frameH), Offset(frameLeft + frameW, frameTop + frameH - cornerLen), strokeW, StrokeCap.Round)
        }
    }
}

/**
 * STAGE 2: Interactive Frozen Block Picker Screen.
 * Uses clean vertical stacked headers and toolbars so nothing gets squished horizontally.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractiveBlockSelectionScreen(
    state: ScannerUiState,
    onIntent: (ScannerIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val frozenBitmap = remember(state.capturedFrameBitmap) {
        state.capturedFrameBitmap?.asImageBitmap()
    }
    val allSelected = state.capturedBoxes.isNotEmpty() &&
        state.selectedBlockIndices.size == state.capturedBoxes.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header Bar (Stacked vertically for clean alignment matching Audit Result screen)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            OutlinedButton(
                onClick = { onIntent(ScannerIntent.CancelBlockSelection) },
                modifier = Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(com.biteguard.core.ui.R.string.retake_photo),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(com.biteguard.core.ui.R.string.retake_photo),
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = stringResource(com.biteguard.core.ui.R.string.choose_text_blocks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(
                    com.biteguard.core.ui.R.string.blocks_selected_format,
                    state.selectedBlockIndices.size,
                    state.capturedBoxes.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Frozen Captured Frame with Interactive Tap-to-Toggle Bounding Boxes
        if (frozenBitmap != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = frozenBitmap,
                        contentDescription = "Frozen captured package frame. Tap highlighted boxes to toggle selection.",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    val safeColor = semanticColors.safe.color
                    val safeContainer = semanticColors.safe.colorContainer.copy(alpha = 0.34f)
                    val unselectedStroke = Color.White.copy(alpha = 0.70f)
                    val unselectedFill = Color.Black.copy(alpha = 0.25f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(state.capturedBoxes, state.capturedFrameWidth, state.capturedFrameHeight) {
                                detectTapGestures { tapOffset ->
                                    val imgW = state.capturedFrameWidth.toFloat().coerceAtLeast(1f)
                                    val imgH = state.capturedFrameHeight.toFloat().coerceAtLeast(1f)
                                    val scale = max(size.width / imgW, size.height / imgH)
                                    val offsetX = (size.width - imgW * scale) / 2f
                                    val offsetY = (size.height - imgH * scale) / 2f

                                    val tappedIdx = state.capturedBoxes.indexOfFirst { box ->
                                        val left = box.boundingBox.left * scale + offsetX
                                        val top = box.boundingBox.top * scale + offsetY
                                        val right = box.boundingBox.right * scale + offsetX
                                        val bottom = box.boundingBox.bottom * scale + offsetY
                                        tapOffset.x in left..right && tapOffset.y in top..bottom
                                    }
                                    if (tappedIdx != -1) {
                                        onIntent(ScannerIntent.ToggleBlockSelection(tappedIdx))
                                    }
                                }
                            }
                    ) {
                        val imgW = state.capturedFrameWidth.toFloat().coerceAtLeast(1f)
                        val imgH = state.capturedFrameHeight.toFloat().coerceAtLeast(1f)
                        val scale = max(size.width / imgW, size.height / imgH)
                        val offsetX = (size.width - imgW * scale) / 2f
                        val offsetY = (size.height - imgH * scale) / 2f

                        state.capturedBoxes.forEachIndexed { idx, box ->
                            val isSelected = idx in state.selectedBlockIndices
                            val left = box.boundingBox.left * scale + offsetX
                            val top = box.boundingBox.top * scale + offsetY
                            val right = box.boundingBox.right * scale + offsetX
                            val bottom = box.boundingBox.bottom * scale + offsetY
                            val rectSize = Size((right - left).coerceAtLeast(12f), (bottom - top).coerceAtLeast(12f))

                            drawRoundRect(
                                color = if (isSelected) safeContainer else unselectedFill,
                                topLeft = Offset(left, top),
                                size = rectSize,
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                            drawRoundRect(
                                color = if (isSelected) safeColor else unselectedStroke,
                                topLeft = Offset(left, top),
                                size = rectSize,
                                cornerRadius = CornerRadius(10f, 10f),
                                style = Stroke(width = if (isSelected) 6f else 3f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Tap boxes on photo or check cards below",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Clean Stacked Action Toolbar (Select All / Edit Text side-by-side with equal weight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (allSelected) {
                        onIntent(ScannerIntent.ClearCapturedBlockSelection)
                    } else {
                        onIntent(ScannerIntent.SelectAllCapturedBlocks)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (allSelected) stringResource(com.biteguard.core.ui.R.string.clear_all) else stringResource(com.biteguard.core.ui.R.string.select_all),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    val currentSelectionText = state.selectedBlockIndices
                        .sorted()
                        .mapNotNull { state.capturedBoxes.getOrNull(it)?.text }
                        .joinToString("\n")
                    onIntent(ScannerIntent.ToggleManualInputSheet(true, currentSelectionText))
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(com.biteguard.core.ui.R.string.edit_text),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Scrollable List of Frozen OCR Blocks (Vertical Card Layout)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            itemsIndexed(
                items = state.capturedBoxes,
                key = { index, box -> "$index-${box.boundingBox.left}-${box.boundingBox.top}" }
            ) { index, box ->
                val isSelected = index in state.selectedBlockIndices
                val borderColor = when {
                    isSelected -> semanticColors.safe.color
                    box.containsIngredientKeyword -> semanticColors.info.color
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                val containerColor = if (isSelected) {
                    semanticColors.safe.colorContainer.copy(alpha = 0.40f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIntent(ScannerIntent.ToggleBlockSelection(index)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                                Text(
                                    text = "Block #${index + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (box.containsIngredientKeyword) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = semanticColors.safe.color,
                                    contentColor = semanticColors.safe.onColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "INGREDIENTS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = box.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Confirm & Scan Selected Blocks CTA
        Button(
            onClick = { onIntent(ScannerIntent.ConfirmBlockSelectionAndAudit) },
            enabled = state.selectedBlockIndices.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = semanticColors.safe.color,
                contentColor = semanticColors.safe.onColor
            )
        ) {
            Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(com.biteguard.core.ui.R.string.scan_selected_blocks, state.selectedBlockIndices.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Single sleek M3 Top HUD combining progressive scanner status and active guard chip.
 * Replaces the old double-stacked top boxes so the camera preview stays open and focused.
 */
@Composable
private fun CompactScannerTopHud(
    stageText: String,
    allergenCount: Int,
    goalCount: Int,
    isReadyToScan: Boolean,
    isStreaming: Boolean,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val statusFamily = when {
        isReadyToScan -> semanticColors.safe
        isStreaming -> semanticColors.info
        else -> null
    }

    Surface(
        modifier = modifier
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = stageText
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(
            width = 1.5.dp,
            color = statusFamily?.color ?: MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isReadyToScan) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = statusFamily?.color ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stageText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onProfileClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${allergenCount + goalCount} Guards",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isStreaming) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = semanticColors.info.color
                )
            }
        }
    }
}

/**
 * Clean, floating M3 bottom controls without a heavy outer card blocking the camera preview.
 */
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

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                shape = RoundedCornerShape(18.dp),
                containerColor = buttonFamily.color,
                contentColor = buttonFamily.onColor,
                icon = {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                },
                text = {
                    Text(
                        text = when {
                            isReadyToScan -> stringResource(com.biteguard.core.ui.R.string.btn_ready_capture)
                            hasDetectedText -> stringResource(com.biteguard.core.ui.R.string.btn_capture_choose)
                            else -> stringResource(com.biteguard.core.ui.R.string.btn_scan_label)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenManualInput,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(com.biteguard.core.ui.R.string.btn_manual_text),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalButton(
                onClick = { onRunSamplePreset(ScannerViewModel.SAMPLE_PRESETS.first()) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(com.biteguard.core.ui.R.string.btn_demo_sample),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dedicated "Next Screen" for Audit Results:
 * 1. Moves all post-scan results and the Offline Robot Lottie AlertDialog out of the live camera viewfinder.
 * 2. Clearly separates the Overall Safety Verdict from the "Filtered & Flagged Words" breakdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DedicatedAuditResultScreen(
    state: ScannerUiState,
    onIntent: (ScannerIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStream = state.streamState
    var offlineDialogDismissed by rememberSaveable(currentStream.hashCode()) {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Result Screen Top Header Bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            OutlinedButton(
                onClick = { onIntent(ScannerIntent.DismissAuditResult) },
                modifier = Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(com.biteguard.core.ui.R.string.back_to_camera),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(com.biteguard.core.ui.R.string.back_to_camera),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(com.biteguard.core.ui.R.string.label_audit_result),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Main Result Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (currentStream) {
                is StreamState.Streaming -> {
                    StreamingTypewriterCard(
                        stageMessage = currentStream.stageMessage,
                        accumulatedSummary = currentStream.partialVerdict.accumulatedSummary,
                        partialSafetyLevel = currentStream.partialVerdict.partialSafetyLevel,
                        onCancel = { onIntent(ScannerIntent.DismissAuditResult) }
                    )
                }

                is StreamState.Success -> {
                    CompletedVerdictSheetCard(
                        verdict = currentStream.data,
                        filteredKeywords = state.highlightedTerms,
                        capturedBoxes = state.capturedBoxes,
                        imageWidth = state.capturedFrameWidth,
                        imageHeight = state.capturedFrameHeight,
                        onFeedback = { fb -> onIntent(ScannerIntent.SubmitFeedback(currentStream.data, fb)) },
                        onScanAnother = { onIntent(ScannerIntent.DismissAuditResult) }
                    )
                }

                is StreamState.Error -> {
                    ErrorAndFallbackResultCard(
                        errorState = currentStream,
                        highlights = state.highlightedTerms,
                        onRetryCloud = { onIntent(ScannerIntent.RetryFailedAuditWithBackoff) },
                        onKeepOfflineMatch = { onIntent(ScannerIntent.AcceptOfflineFallbackVerdict) },
                        onManualEdit = {
                            onIntent(ScannerIntent.ToggleManualInputSheet(true, currentStream.cachedRawText))
                        },
                        onDismiss = { onIntent(ScannerIntent.DismissAuditResult) }
                    )
                }

                else -> Unit
            }
        }
    }

    // Offline / Model Not Loading Lottie AlertDialog hosted on the dedicated Result Screen
    if (currentStream is StreamState.Error) {
        val isModelOfflineOrFailed = currentStream.errorKind in setOf(
            AuditErrorKind.NETWORK_TIMEOUT,
            AuditErrorKind.API_KEY_MISSING,
            AuditErrorKind.MALFORMED_AI_RESPONSE,
            AuditErrorKind.UNKNOWN
        )
        if (isModelOfflineOrFailed && !offlineDialogDismissed) {
            OfflineModelLottieAlertDialog(
                errorKind = currentStream.errorKind,
                fallbackVerdict = currentStream.fallbackVerdict,
                retryAttempt = currentStream.retryAttempt,
                onAcceptLocalVerdict = {
                    offlineDialogDismissed = true
                    onIntent(ScannerIntent.AcceptOfflineFallbackVerdict)
                },
                onRetryCloudModel = {
                    offlineDialogDismissed = true
                    onIntent(ScannerIntent.RetryFailedAuditWithBackoff)
                },
                onDismiss = {
                    offlineDialogDismissed = true
                }
            )
        }
    }
}

/**
 * NFR-NT1: Streaming visual feedback with live typewriter text reveal on the Result Screen.
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
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

            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletedVerdictSheetCard(
    verdict: AuditVerdict,
    filteredKeywords: List<String>,
    capturedBoxes: List<DetectedTextBox> = emptyList(),
    imageWidth: Int = 1080,
    imageHeight: Int = 1920,
    onFeedback: (com.biteguard.core.model.UserFeedback) -> Unit,
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

    // Correlate flagged ingredients with OCR cluster bounding boxes for numbered pins
    val highlightBoxes = remember(verdict.flaggedIngredients, capturedBoxes, semanticColors) {
        if (capturedBoxes.isEmpty()) return@remember emptyList()
        verdict.flaggedIngredients.mapIndexedNotNull { index, flag ->
            val matchingBox = capturedBoxes.firstOrNull { box ->
                box.text.contains(flag.name, ignoreCase = true) ||
                    flag.name.split(" ", "-", ",").any { part ->
                        part.length >= 3 && box.text.contains(part, ignoreCase = true)
                    }
            } ?: capturedBoxes.getOrNull(index % capturedBoxes.size)

            if (matchingBox != null) {
                val boxColor = when (flag.severity) {
                    com.biteguard.core.model.Severity.HIGH -> semanticColors.danger.color
                    com.biteguard.core.model.Severity.MEDIUM -> semanticColors.caution.color
                    com.biteguard.core.model.Severity.LOW -> semanticColors.info.color
                }
                ImageHighlightBox(
                    index = index + 1,
                    label = flag.name,
                    boundingBox = matchingBox.boundingBox,
                    color = boxColor
                )
            } else null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // SECTION 1: Overall Verdict & AI Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(2.dp, family.color)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SafetyVerdictBanner(
                        safetyLevel = verdict.safetyLevel,
                        isOfflineFallback = verdict.isOfflineFallback
                    )

                    Text(
                        text = verdict.summary.take(visibleChars),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Captured Package Image with Click-to-Zoom and Cluster Pins
        if (verdict.snapshotThumbnailBytes != null) {
            item {
                ClickableCapturedImageCard(
                    thumbnailBytes = verdict.snapshotThumbnailBytes,
                    title = verdict.productName,
                    subtitle = if (highlightBoxes.isNotEmpty()) {
                        "Scanned package photo • ${highlightBoxes.size} cluster(s) mapped • Tap to zoom"
                    } else {
                        "Scanned package photo • Tap to zoom"
                    },
                    highlightBoxes = highlightBoxes,
                    imageWidth = if (imageWidth > 0) imageWidth else 1080,
                    imageHeight = if (imageHeight > 0) imageHeight else 1920
                )
            }
        }

        // SECTION 2: Separated "Filtered & Flagged Words" Card
        if (verdict.flaggedIngredients.isNotEmpty() || filteredKeywords.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = family.color
                            )
                            Text(
                                text = stringResource(com.biteguard.core.ui.R.string.filtered_and_flagged_title, verdict.flaggedIngredients.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }
                            )
                        }

                        if (filteredKeywords.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filteredKeywords.forEach { word ->
                                    val matchingFlagIdx = verdict.flaggedIngredients.indexOfFirst { flag ->
                                        flag.name.contains(word, ignoreCase = true) || word.contains(flag.name, ignoreCase = true)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = family.colorContainer,
                                        contentColor = family.onColorContainer,
                                        border = BorderStroke(1.dp, family.color)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            if (matchingFlagIdx >= 0) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = family.color,
                                                    contentColor = family.onColor,
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${matchingFlagIdx + 1}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = word,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val primaryFlags = verdict.flaggedIngredients.take(2)
                        val overflowFlags = verdict.flaggedIngredients.drop(2)

                        primaryFlags.forEachIndexed { idx, flag ->
                            FlaggedIngredientCard(
                                ingredient = flag,
                                badgeNumber = idx + 1
                            )
                        }

                        if (overflowFlags.isNotEmpty()) {
                            BiteGuardAccordion(
                                title = stringResource(com.biteguard.core.ui.R.string.more_flagged_ingredients),
                                subtitle = stringResource(com.biteguard.core.ui.R.string.more_flagged_subtitle, overflowFlags.size),
                                badgeText = "+${overflowFlags.size}",
                                accentColor = family.color,
                                initiallyExpanded = false
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    overflowFlags.forEachIndexed { overflowIdx, flag ->
                                        FlaggedIngredientCard(
                                            ingredient = flag,
                                            badgeNumber = primaryFlags.size + overflowIdx + 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 3: Accuracy Feedback Row
        item {
            VerdictFeedbackRow(
                ttftMs = verdict.ttftMs,
                totalDurationMs = verdict.totalStreamDurationMs,
                selectedFeedback = verdict.userFeedback,
                onSubmitFeedback = onFeedback
            )
        }

        // SECTION 4: Accordion for Raw Extracted Label Text & Medical Disclaimer
        item {
            BiteGuardAccordion(
                title = stringResource(com.biteguard.core.ui.R.string.extracted_ocr_disclaimer),
                subtitle = stringResource(com.biteguard.core.ui.R.string.extracted_ocr_subtitle),
                leadingIcon = Icons.Default.Description,
                accentColor = MaterialTheme.colorScheme.primary,
                initiallyExpanded = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HighlightedRawTextCard(
                        rawText = verdict.rawExtractedText,
                        highlightedKeywords = filteredKeywords
                    )
                    MedicalDisclaimerBanner()
                }
            }
        }

        item {
            Button(
                onClick = onScanAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(com.biteguard.core.ui.R.string.scan_next_package),
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * FR-04: Clear Error & Offline Fallback decision UI on the dedicated Result Screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorAndFallbackResultCard(
    errorState: StreamState.Error,
    highlights: List<String>,
    onRetryCloud: () -> Unit,
    onKeepOfflineMatch: () -> Unit,
    onManualEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val fallback = errorState.fallbackVerdict

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
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
            val family = semanticColors.forSafetyLevel(fallback.safetyLevel)

            item {
                SafetyVerdictBanner(
                    safetyLevel = fallback.safetyLevel,
                    isOfflineFallback = true
                )
            }

            if (fallback.snapshotThumbnailBytes != null) {
                item {
                    ClickableCapturedImageCard(
                        thumbnailBytes = fallback.snapshotThumbnailBytes,
                        title = fallback.productName,
                        subtitle = "Captured package photo • Tap to zoom"
                    )
                }
            }

            item {
                BiteGuardAccordion(
                    title = "Filtered Words & Local Rule Details",
                    subtitle = fallback.summary,
                    badgeText = "${fallback.flaggedIngredients.size} flagged",
                    leadingIcon = Icons.Default.FilterAlt,
                    accentColor = family.color,
                    initiallyExpanded = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (highlights.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                highlights.forEach { word ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = family.colorContainer,
                                        contentColor = family.onColorContainer,
                                        border = BorderStroke(1.dp, family.color)
                                    ) {
                                        Text(
                                            text = word,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        fallback.flaggedIngredients.forEach { flag ->
                            FlaggedIngredientCard(ingredient = flag)
                        }

                        HighlightedRawTextCard(
                            rawText = fallback.rawExtractedText,
                            highlightedKeywords = highlights
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Back to Live Camera", fontWeight = FontWeight.Bold)
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
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = semanticColors.safe.colorContainer,
                        contentColor = semanticColors.safe.onColorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(com.biteguard.core.ui.R.string.camera_access_needed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(com.biteguard.core.ui.R.string.camera_scanner_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(com.biteguard.core.ui.R.string.camera_access_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semanticColors.safe.color,
                        contentColor = semanticColors.safe.onColor
                    )
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(com.biteguard.core.ui.R.string.grant_camera_permission),
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = { onRunPreset(ScannerViewModel.SAMPLE_PRESETS.first()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(com.biteguard.core.ui.R.string.run_sample_audit),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedButton(
                    onClick = onOpenManualInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(com.biteguard.core.ui.R.string.enter_manual_text),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

