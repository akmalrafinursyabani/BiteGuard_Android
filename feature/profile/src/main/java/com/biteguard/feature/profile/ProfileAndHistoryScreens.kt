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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
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
import com.biteguard.core.ui.components.BiteGuardAccordion
import com.biteguard.core.ui.components.ClickableCapturedImageCard
import com.biteguard.core.ui.components.FlaggedIngredientCard
import com.biteguard.core.ui.components.HighlightedRawTextCard
import com.biteguard.core.ui.components.MedicalDisclaimerBanner
import com.biteguard.core.ui.components.SafetyLevelChip
import com.biteguard.core.ui.components.SafetyVerdictBanner
import com.biteguard.core.ui.components.VerdictFeedbackRow
import com.biteguard.core.ui.components.ZoomableImageDialog
import com.example.biteguardai.ui.theme.LocalBiteGuardSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * FR-01 & NFR-NT2: Uncluttered Material 3 Dietary & Health Profile Screen with M3 Accordions
 * for long lists and WCAG High-Contrast toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietaryProfileScreen(
    state: ProfileAndHistoryUiState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    var editingName by remember(state.profile.userName) { mutableStateOf(false) }
    var draftName by remember(state.profile.userName) { mutableStateOf(state.profile.userName) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Greeting & Guard Summary Header Card
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = semanticColors.safe.colorContainer,
                                contentColor = semanticColors.safe.onColorContainer,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (state.profile.userName.isNotBlank()) {
                                        "${state.profile.userName}'s Guard Profile"
                                    } else {
                                        "Dietary & Allergen Profile"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    text = "${state.profile.selectedAllergens.size} Allergen(s) • ${state.profile.healthGoals.size} Health Goal(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(onClick = { editingName = !editingName }) {
                            Text(
                                text = if (editingName) "Cancel" else "Edit Name",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (editingName) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it },
                                singleLine = true,
                                label = { Text("Your Name") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onIntent(ProfileIntent.UpdateUserName(draftName))
                                    editingName = false
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Severe Allergens Accordion (Expanded by default for fast one-tap access, compact M3 chips)
        item {
            BiteGuardAccordion(
                title = "Severe Allergen Alerts",
                subtitle = "Triggers immediate DANGER alerts on labels",
                badgeText = "${state.profile.selectedAllergens.size} active",
                leadingIcon = Icons.Default.WarningAmber,
                accentColor = semanticColors.danger.color,
                initiallyExpanded = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Allergen.entries.forEach { allergen ->
                            val isSelected = allergen in state.profile.selectedAllergens
                            FilterChip(
                                selected = isSelected,
                                onClick = { onIntent(ProfileIntent.ToggleAllergen(allergen)) },
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

                    // Nested Accordion for Hidden Chemical Derivatives reference list
                    BiteGuardAccordion(
                        title = "Common Hidden Derivatives Guide",
                        subtitle = "See chemical names audited for each allergen",
                        leadingIcon = Icons.Default.MenuBook,
                        accentColor = semanticColors.info.color,
                        initiallyExpanded = false
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Allergen.entries.forEach { allergen ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${allergen.displayName}: ",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = allergen.examples,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dietary & Health Goals Accordion (FR-01)
        item {
            BiteGuardAccordion(
                title = "Dietary & Health Goals",
                subtitle = "Flags hidden sugars, sodium & lifestyle conflicts",
                badgeText = "${state.profile.healthGoals.size} active",
                leadingIcon = Icons.Default.HealthAndSafety,
                accentColor = semanticColors.safe.color,
                initiallyExpanded = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = family?.color ?: MaterialTheme.colorScheme.outlineVariant
                            )
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
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = semanticColors.info.onColorContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ultra High-Contrast Accessibility Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = semanticColors.info.onColorContainer
                        )
                        Text(
                            text = "Maximizes WCAG 2.1 AAA contrast ratios and thickens border signage.",
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

        item {
            MedicalDisclaimerBanner()
        }
    }
}

/**
 * FR-05: Scan History & Encrypted Audit Log Screen with search, SafetyLevel filters,
 * and M3 Accordion detail view.
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
                    text = "${state.allHistory.size} saved audit(s) stored strictly on-device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.allHistory.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onIntent(ProfileIntent.RequestClearHistory(true)) },
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp),
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
                    Text("Clear", fontWeight = FontWeight.Bold)
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
                modifier = Modifier.heightIn(min = 40.dp),
                label = { Text("All (${state.allHistory.size})", fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
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
                    modifier = Modifier.heightIn(min = 40.dp),
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
                shape = RoundedCornerShape(18.dp),
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
                        modifier = Modifier.size(52.dp),
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
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Open Label Scanner", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onIntent(ProfileIntent.UpdateSearchQuery(""))
                                onIntent(ProfileIntent.FilterBySafetyLevel(null))
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Reset Search & Filters", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val latestScan = state.allHistory.firstOrNull()
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (latestScan != null && state.searchQuery.isBlank() && state.selectedSafetyFilter == null) {
                    item(key = "latest_featured_${latestScan.id}") {
                        LatestScannedSpotlightCard(
                            verdict = latestScan,
                            formattedDate = dateFormatter.format(latestScan.timestamp),
                            onInspectDetails = { onIntent(ProfileIntent.SelectVerdictDetail(latestScan)) }
                        )
                    }
                }
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

    // Detail Bottom Sheet with M3 Accordions + Click-to-Zoom Package Photo
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
                        text = "Scanned ${dateFormatter.format(selectedVerdict.timestamp)} • ${selectedVerdict.flaggedIngredients.size} flagged item(s) • TTFT ${selectedVerdict.ttftMs}ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedVerdict.snapshotThumbnailBytes != null) {
                    item {
                        ClickableCapturedImageCard(
                            thumbnailBytes = selectedVerdict.snapshotThumbnailBytes,
                            title = selectedVerdict.productName,
                            subtitle = "Scanned ${dateFormatter.format(selectedVerdict.timestamp)}"
                        )
                    }
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
                        BiteGuardAccordion(
                            title = "Flagged Ingredients",
                            subtitle = "Conflict details for this package",
                            badgeText = "${selectedVerdict.flaggedIngredients.size} flagged",
                            leadingIcon = Icons.Default.WarningAmber,
                            accentColor = semanticColors.forSafetyLevel(selectedVerdict.safetyLevel).color,
                            initiallyExpanded = true
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                selectedVerdict.flaggedIngredients.forEach { flag ->
                                    FlaggedIngredientCard(ingredient = flag)
                                }
                            }
                        }
                    }
                }
                item {
                    val highlights by produceState(
                        initialValue = emptyList(),
                        key1 = selectedVerdict,
                        key2 = state.profile
                    ) {
                        value = withContext(Dispatchers.Default) {
                            LocalFallbackAuditEngine.extractHighlightTerms(selectedVerdict, state.profile)
                        }
                    }
                    BiteGuardAccordion(
                        title = "Extracted OCR Text & Disclaimer",
                        subtitle = "Raw package text and medical notice",
                        leadingIcon = Icons.Default.Description,
                        initiallyExpanded = false
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HighlightedRawTextCard(
                                rawText = selectedVerdict.rawExtractedText,
                                highlightedKeywords = highlights
                            )
                            MedicalDisclaimerBanner()
                        }
                    }
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

/**
 * Spotlight card at the top of the Audit Log displaying the most recent scanned image (with click-to-zoom),
 * safety verdict, flagged count, and a quick button to open full details.
 */
@Composable
private fun LatestScannedSpotlightCard(
    verdict: AuditVerdict,
    formattedDate: String,
    onInspectDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalBiteGuardSemanticColors.current
    val family = semanticColors.forSafetyLevel(verdict.safetyLevel)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(2.dp, family.color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = family.colorContainer,
                    contentColor = family.onColorContainer
                ) {
                    Text(
                        text = "LATEST SCANNED PACKAGE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (verdict.snapshotThumbnailBytes != null) {
                ClickableCapturedImageCard(
                    thumbnailBytes = verdict.snapshotThumbnailBytes,
                    title = verdict.productName,
                    subtitle = "Latest scan • $formattedDate"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = verdict.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${verdict.flaggedIngredients.size} flagged ingredient(s) • ${if (verdict.isOfflineFallback) "Local Engine" else "Cloud AI"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SafetyLevelChip(safetyLevel = verdict.safetyLevel)
            }

            Text(
                text = verdict.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )

            OutlinedButton(
                onClick = onInspectDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Full Audit Details & OCR Text", fontWeight = FontWeight.Bold)
            }
        }
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
    var showZoomDialog by remember { mutableStateOf(false) }

    val thumbnailBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = verdict.snapshotThumbnailBytes
    ) {
        val bytes = verdict.snapshotThumbnailBytes
        value = if (bytes != null) {
            withContext(Dispatchers.Default) {
                runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        } else {
            null
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.5.dp, family.color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val loadedThumbnail = thumbnailBitmap
            if (loadedThumbnail != null) {
                Image(
                    bitmap = loadedThumbnail,
                    contentDescription = "Snapshot thumbnail of scanned label. Tap to zoom.",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showZoomDialog = true }
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
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

                Text(
                    text = "${verdict.flaggedIngredients.size} flagged • Tap card for details${if (loadedThumbnail != null) " • Tap photo to zoom" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    val loadedThumbnail = thumbnailBitmap
    if (showZoomDialog && loadedThumbnail != null) {
        ZoomableImageDialog(
            imageBitmap = loadedThumbnail,
            title = verdict.productName,
            subtitle = "Scanned $formattedDate",
            onDismiss = { showZoomDialog = false }
        )
    }
}

