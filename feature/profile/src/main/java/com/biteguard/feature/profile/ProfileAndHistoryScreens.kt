package com.biteguard.feature.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biteguard.core.data.fallback.LocalFallbackAuditEngine
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.AuditVerdict
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.ui.components.FlaggedIngredientCard
import com.biteguard.core.ui.components.HighlightedRawTextCard
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.SafetyLevelChip
import com.biteguard.core.ui.components.SafetyVerdictBanner
import com.biteguard.core.ui.components.VerdictFeedbackRow
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * FR-01 & NFR-NT2: Dietary & Health Profile Configuration Screen with WCAG High-Contrast toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietaryProfileScreen(
    state: ProfileAndHistoryUiState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Dietary & Allergen Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Changes immediately update real-time camera scans and offline fallback rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Accessibility & Visual Contrast Card (NFR-NT2)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = semanticColors.info.colorContainer,
                    contentColor = semanticColors.info.onColorContainer
                ),
                border = BorderStroke(1.5.dp, semanticColors.info.color)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) {
                            onIntent(ProfileIntent.SetHighContrast(!state.profile.highContrastMode))
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = semanticColors.info.onColorContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ultra High-Contrast Accessibility Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = semanticColors.info.onColorContainer
                        )
                        Text(
                            text = "Maximizes WCAG 2.1 AAA contrast ratios and thickens border signage for low-vision & color-blind shoppers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.info.onColorContainer
                        )
                    }
                    Switch(
                        checked = state.profile.highContrastMode,
                        onCheckedChange = { onIntent(ProfileIntent.SetHighContrast(it)) }
                    )
                }
            }
        }

        // Severe Allergens Section (FR-01)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = semanticColors.danger.color
                        )
                        Column {
                            Text(
                                text = "Severe Allergen Alerts (${state.profile.selectedAllergens.size} active)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Triggers immediate DANGER alerts when explicit or chemical derivatives are found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Allergen.entries.forEach { allergen ->
                            val isSelected = allergen in state.profile.selectedAllergens
                            FilterChip(
                                selected = isSelected,
                                onClick = { onIntent(ProfileIntent.ToggleAllergen(allergen)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = allergen.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = allergen.examples,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
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
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = semanticColors.danger.color,
                                    selectedBorderWidth = 2.dp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Dietary & Health Goals Section (FR-01)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = semanticColors.safe.color
                        )
                        Column {
                            Text(
                                text = "Dietary & Health Goals (${state.profile.healthGoals.size} active)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "Flags hidden sugars, sodium enhancers, and lifestyle/religious dietary conflicts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HealthGoal.entries.forEach { goal ->
                            val isSelected = goal in state.profile.healthGoals
                            val family = if (isSelected) semanticColors.safe else null

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Checkbox) {
                                        onIntent(ProfileIntent.ToggleHealthGoal(goal))
                                    }
                                    .semantics {
                                        contentDescription = "${goal.displayName}, ${if (isSelected) "Enabled" else "Disabled"}. ${goal.description}"
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = family?.colorContainer ?: MaterialTheme.colorScheme.surface,
                                contentColor = family?.onColorContainer ?: MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = family?.color ?: MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = goal.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = goal.description,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = isSelected,
                                        onCheckedChange = { onIntent(ProfileIntent.ToggleHealthGoal(goal)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            MedicalDisclaimerBanner()
        }
    }
}

/**
 * FR-05: Scan History & Encrypted Audit Log Screen with search, SafetyLevel filters, and detail view.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(
    state: ProfileAndHistoryUiState,
    onIntent: (ProfileIntent) -> Unit,
    onNavigateToScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Encrypted Audit Log",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "${state.allHistory.size} saved package audit(s) stored strictly on-device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.allHistory.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onIntent(ProfileIntent.RequestClearHistory(true)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = semanticColors.danger.color
                    ),
                    border = BorderStroke(1.5.dp, semanticColors.danger.color)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search input (FR-05)
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onIntent(ProfileIntent.UpdateSearchQuery(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search ingredient, summary, or product") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (state.searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onIntent(ProfileIntent.UpdateSearchQuery("")) }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            } else null,
            shape = RoundedCornerShape(14.dp)
        )

        // Filter chips by SafetyLevel (SAFE, CAUTION, DANGER)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = state.selectedSafetyFilter == null,
                onClick = { onIntent(ProfileIntent.FilterBySafetyLevel(null)) },
                modifier = Modifier.heightIn(min = 44.dp),
                label = { Text("All (${state.allHistory.size})", fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            SafetyLevel.entries.forEach { level ->
                val isSelected = state.selectedSafetyFilter == level
                val family = semanticColors.forSafetyLevel(level)
                val count = state.allHistory.count { it.safetyLevel == level }
                FilterChip(
                    selected = isSelected,
                    onClick = { onIntent(ProfileIntent.FilterBySafetyLevel(level)) },
                    modifier = Modifier.heightIn(min = 44.dp),
                    label = {
                        Text(
                            text = "${level.label} ($count)",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = family.colorContainer,
                        selectedLabelColor = family.onColorContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = family.color,
                        selectedBorderColor = family.color,
                        selectedBorderWidth = 2.dp
                    )
                )
            }
        }

        if (state.filteredHistory.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (state.allHistory.isEmpty()) {
                            "No Scanned Packages Yet"
                        } else {
                            "No Scans Match Your Filter"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (state.allHistory.isEmpty()) {
                            "Completed label audits are automatically encrypted and saved here for offline review."
                        } else {
                            "Try clearing your search query or switching the safety level filter above."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (state.allHistory.isEmpty()) {
                        Button(
                            onClick = onNavigateToScanner,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Open Label Scanner", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onIntent(ProfileIntent.UpdateSearchQuery(""))
                                onIntent(ProfileIntent.FilterBySafetyLevel(null))
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Reset Search & Filters", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.filteredHistory, key = { it.id }) { verdict ->
                    HistoryVerdictCard(
                        verdict = verdict,
                        formattedDate = dateFormatter.format(verdict.timestamp),
                        onClick = { onIntent(ProfileIntent.SelectVerdictDetail(verdict)) }
                    )
                }
            }
        }
    }

    // Detail Bottom Sheet
    val selectedVerdict = state.selectedVerdictForDetail
    if (selectedVerdict != null) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(ProfileIntent.SelectVerdictDetail(null)) }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SafetyVerdictBanner(
                        safetyLevel = selectedVerdict.safetyLevel,
                        isOfflineFallback = selectedVerdict.isOfflineFallback
                    )
                }
                item {
                    Text(
                        text = selectedVerdict.productName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scanned ${dateFormatter.format(selectedVerdict.timestamp)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Text(
                        text = selectedVerdict.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (selectedVerdict.flaggedIngredients.isNotEmpty()) {
                    item {
                        Text(
                            text = "Flagged Ingredients (${selectedVerdict.flaggedIngredients.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    items(selectedVerdict.flaggedIngredients) { flag ->
                        FlaggedIngredientCard(ingredient = flag)
                    }
                }
                item {
                    val highlights = remember(selectedVerdict, state.profile) {
                        LocalFallbackAuditEngine.extractHighlightTerms(selectedVerdict, state.profile)
                    }
                    HighlightedRawTextCard(
                        rawText = selectedVerdict.rawExtractedText,
                        highlightedKeywords = highlights
                    )
                }
                item {
                    VerdictFeedbackRow(
                        ttftMs = selectedVerdict.ttftMs,
                        totalDurationMs = selectedVerdict.totalStreamDurationMs,
                        selectedFeedback = selectedVerdict.userFeedback,
                        onSubmitFeedback = { fb ->
                            onIntent(ProfileIntent.SubmitVerdictFeedback(selectedVerdict, fb))
                        }
                    )
                }
                item {
                    MedicalDisclaimerBanner()
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (state.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { onIntent(ProfileIntent.RequestClearHistory(false)) },
            title = {
                Text("Clear Encrypted Scan History?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("This permanently removes all ${state.allHistory.size} saved label audits and thumbnails from your device.")
            },
            confirmButton = {
                Button(
                    onClick = { onIntent(ProfileIntent.ConfirmClearHistory) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semanticColors.danger.color,
                        contentColor = semanticColors.danger.onColor
                    )
                ) {
                    Text("Delete All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(ProfileIntent.RequestClearHistory(false)) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryVerdictCard(
    verdict: AuditVerdict,
    formattedDate: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val family = semanticColors.forSafetyLevel(verdict.safetyLevel)

    val thumbnailBitmap = remember(verdict.snapshotThumbnailBytes) {
        verdict.snapshotThumbnailBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${verdict.productName}, verdict ${verdict.safetyLevel.label}, scanned on $formattedDate. ${verdict.summary}"
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.5.dp, family.color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = "Snapshot thumbnail of scanned label",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = family.colorContainer,
                    contentColor = family.onColorContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = verdict.safetyLevel.label.take(4),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SafetyLevelChip(safetyLevel = verdict.safetyLevel)
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = verdict.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = verdict.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )

                if (verdict.flaggedIngredients.isNotEmpty()) {
                    Text(
                        text = "Flagged: " + verdict.flaggedIngredients.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = family.color
                    )
                }
            }
        }
    }
}
