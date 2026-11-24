package com.biteguard.core.ui.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.biteguard.core.model.Allergen
import com.biteguard.core.model.HealthGoal
import com.biteguard.core.model.SafetyLevel
import com.biteguard.core.model.Severity
import com.biteguard.core.ui.R
import java.util.Locale

@Composable
fun Allergen.localizedDisplayName(): String = when (this) {
    Allergen.PEANUTS -> stringResource(R.string.allergen_peanuts)
    Allergen.TREE_NUTS -> stringResource(R.string.allergen_tree_nuts)
    Allergen.DAIRY -> stringResource(R.string.allergen_dairy)
    Allergen.GLUTEN -> stringResource(R.string.allergen_gluten)
    Allergen.SHELLFISH -> stringResource(R.string.allergen_shellfish)
    Allergen.SOY -> stringResource(R.string.allergen_soy)
    Allergen.EGGS -> stringResource(R.string.allergen_eggs)
    Allergen.FISH -> stringResource(R.string.allergen_fish)
    Allergen.SESAME -> stringResource(R.string.allergen_sesame)
}

@Composable
fun Allergen.localizedExamples(): String = when (this) {
    Allergen.PEANUTS -> stringResource(R.string.allergen_peanuts_ex)
    Allergen.TREE_NUTS -> stringResource(R.string.allergen_tree_nuts_ex)
    Allergen.DAIRY -> stringResource(R.string.allergen_dairy_ex)
    Allergen.GLUTEN -> stringResource(R.string.allergen_gluten_ex)
    Allergen.SHELLFISH -> stringResource(R.string.allergen_shellfish_ex)
    Allergen.SOY -> stringResource(R.string.allergen_soy_ex)
    Allergen.EGGS -> stringResource(R.string.allergen_eggs_ex)
    Allergen.FISH -> stringResource(R.string.allergen_fish_ex)
    Allergen.SESAME -> stringResource(R.string.allergen_sesame_ex)
}

@Composable
fun HealthGoal.localizedDisplayName(): String = when (this) {
    HealthGoal.LOW_SUGAR -> stringResource(R.string.goal_low_sugar)
    HealthGoal.LOW_SODIUM -> stringResource(R.string.goal_low_sodium)
    HealthGoal.HALAL -> stringResource(R.string.goal_halal)
    HealthGoal.VEGAN -> stringResource(R.string.goal_vegan)
    HealthGoal.VEGETARIAN -> stringResource(R.string.goal_vegetarian)
    HealthGoal.KETO_FRIENDLY -> stringResource(R.string.goal_keto)
}

@Composable
fun HealthGoal.localizedDescription(): String = when (this) {
    HealthGoal.LOW_SUGAR -> stringResource(R.string.goal_low_sugar_desc)
    HealthGoal.LOW_SODIUM -> stringResource(R.string.goal_low_sodium_desc)
    HealthGoal.HALAL -> stringResource(R.string.goal_halal_desc)
    HealthGoal.VEGAN -> stringResource(R.string.goal_vegan_desc)
    HealthGoal.VEGETARIAN -> stringResource(R.string.goal_vegetarian_desc)
    HealthGoal.KETO_FRIENDLY -> stringResource(R.string.goal_keto_desc)
}

@Composable
fun SafetyLevel.localizedLabel(): String = when (this) {
    SafetyLevel.SAFE -> stringResource(R.string.safety_safe_label)
    SafetyLevel.CAUTION -> stringResource(R.string.safety_caution_label)
    SafetyLevel.DANGER -> stringResource(R.string.safety_danger_label)
}

@Composable
fun SafetyLevel.localizedActionTitle(): String = when (this) {
    SafetyLevel.SAFE -> stringResource(R.string.safety_safe_title)
    SafetyLevel.CAUTION -> stringResource(R.string.safety_caution_title)
    SafetyLevel.DANGER -> stringResource(R.string.safety_danger_title)
}

@Composable
fun Severity.localizedLabel(): String = when (this) {
    Severity.HIGH -> stringResource(R.string.severity_high)
    Severity.MEDIUM -> stringResource(R.string.severity_medium)
    Severity.LOW -> stringResource(R.string.severity_low)
}

fun resolveLocale(languageCode: String): Locale? = when (languageCode.lowercase()) {
    "in", "id" -> Locale.forLanguageTag("in")
    "ko" -> Locale.KOREAN
    "en" -> Locale.ENGLISH
    else -> null // System default
}

fun getLocalizedConfiguration(baseConfig: Configuration, languageCode: String): Configuration {
    val targetLocale = resolveLocale(languageCode) ?: return baseConfig
    val config = Configuration(baseConfig)
    config.setLocale(targetLocale)
    return config
}

fun getLocalizedContext(baseContext: Context, languageCode: String): Context {
    val targetLocale = resolveLocale(languageCode) ?: return baseContext
    val config = Configuration(baseContext.resources.configuration)
    config.setLocale(targetLocale)
    return baseContext.createConfigurationContext(config)
}
