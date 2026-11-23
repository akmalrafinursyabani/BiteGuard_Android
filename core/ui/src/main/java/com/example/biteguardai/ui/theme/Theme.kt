package com.example.biteguardai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.biteguard.core.model.SafetyLevel

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

private val highContrastLightColorScheme = lightColorScheme(
    primary = primaryLightHighContrast,
    onPrimary = onPrimaryLightHighContrast,
    primaryContainer = primaryContainerLightHighContrast,
    onPrimaryContainer = onPrimaryContainerLightHighContrast,
    secondary = secondaryLightHighContrast,
    onSecondary = onSecondaryLightHighContrast,
    secondaryContainer = secondaryContainerLightHighContrast,
    onSecondaryContainer = onSecondaryContainerLightHighContrast,
    tertiary = tertiaryLightHighContrast,
    onTertiary = onTertiaryLightHighContrast,
    tertiaryContainer = tertiaryContainerLightHighContrast,
    onTertiaryContainer = onTertiaryContainerLightHighContrast,
    error = errorLightHighContrast,
    onError = onErrorLightHighContrast,
    errorContainer = errorContainerLightHighContrast,
    onErrorContainer = onErrorContainerLightHighContrast,
    background = backgroundLightHighContrast,
    onBackground = onBackgroundLightHighContrast,
    surface = surfaceLightHighContrast,
    onSurface = onSurfaceLightHighContrast,
    surfaceVariant = surfaceVariantLightHighContrast,
    onSurfaceVariant = onSurfaceVariantLightHighContrast,
    outline = outlineLightHighContrast,
    outlineVariant = outlineVariantLightHighContrast,
    scrim = scrimLightHighContrast,
    inverseSurface = inverseSurfaceLightHighContrast,
    inverseOnSurface = inverseOnSurfaceLightHighContrast,
    inversePrimary = inversePrimaryLightHighContrast,
    surfaceDim = surfaceDimLightHighContrast,
    surfaceBright = surfaceBrightLightHighContrast,
    surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
    surfaceContainerLow = surfaceContainerLowLightHighContrast,
    surfaceContainer = surfaceContainerLightHighContrast,
    surfaceContainerHigh = surfaceContainerHighLightHighContrast,
    surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)

private val highContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkHighContrast,
    onPrimary = onPrimaryDarkHighContrast,
    primaryContainer = primaryContainerDarkHighContrast,
    onPrimaryContainer = onPrimaryContainerDarkHighContrast,
    secondary = secondaryDarkHighContrast,
    onSecondary = onSecondaryDarkHighContrast,
    secondaryContainer = secondaryContainerDarkHighContrast,
    onSecondaryContainer = onSecondaryContainerDarkHighContrast,
    tertiary = tertiaryDarkHighContrast,
    onTertiary = onTertiaryDarkHighContrast,
    tertiaryContainer = tertiaryContainerDarkHighContrast,
    onTertiaryContainer = onTertiaryContainerDarkHighContrast,
    error = errorDarkHighContrast,
    onError = onErrorDarkHighContrast,
    errorContainer = errorContainerDarkHighContrast,
    onErrorContainer = onErrorContainerDarkHighContrast,
    background = backgroundDarkHighContrast,
    onBackground = onBackgroundDarkHighContrast,
    surface = surfaceDarkHighContrast,
    onSurface = onSurfaceDarkHighContrast,
    surfaceVariant = surfaceVariantDarkHighContrast,
    onSurfaceVariant = onSurfaceVariantDarkHighContrast,
    outline = outlineDarkHighContrast,
    outlineVariant = outlineVariantDarkHighContrast,
    scrim = scrimDarkHighContrast,
    inverseSurface = inverseSurfaceDarkHighContrast,
    inverseOnSurface = inverseOnSurfaceDarkHighContrast,
    inversePrimary = inversePrimaryDarkHighContrast,
    surfaceDim = surfaceDimDarkHighContrast,
    surfaceBright = surfaceBrightDarkHighContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
    surfaceContainerLow = surfaceContainerLowDarkHighContrast,
    surfaceContainer = surfaceContainerDarkHighContrast,
    surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color,
)

/**
 * Semantic WCAG 2.1 AA+ Safety Colors derived strictly from Color.kt palettes:
 * - SAFE uses Primary (Emerald Green)
 * - CAUTION uses Secondary (Amber Gold)
 * - DANGER uses Error (Crimson Red)
 * - INFO / OFFLINE uses Tertiary (Ocean Slate Blue)
 */
@Immutable
data class BiteGuardSemanticColors(
    val safe: ColorFamily,
    val caution: ColorFamily,
    val danger: ColorFamily,
    val info: ColorFamily,
    val isHighContrast: Boolean
) {
    fun forSafetyLevel(level: SafetyLevel): ColorFamily = when (level) {
        SafetyLevel.SAFE -> safe
        SafetyLevel.CAUTION -> caution
        SafetyLevel.DANGER -> danger
    }
}

val LocalBiteGuardSemanticColors = staticCompositionLocalOf {
    BiteGuardSemanticColors(
        safe = ColorFamily(primaryLight, onPrimaryLight, primaryContainerLight, onPrimaryContainerLight),
        caution = ColorFamily(secondaryLight, onSecondaryLight, secondaryContainerLight, onSecondaryContainerLight),
        danger = ColorFamily(errorLight, onErrorLight, errorContainerLight, onErrorContainerLight),
        info = ColorFamily(tertiaryLight, onTertiaryLight, tertiaryContainerLight, onTertiaryContainerLight),
        isHighContrast = false
    )
}

@Composable
fun BiteGuardAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    // Disabled by default so WCAG safety signage colors (SAFE=Primary, CAUTION=Secondary, DANGER=Error) remain deterministic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        highContrast && darkTheme -> highContrastDarkColorScheme
        highContrast && !darkTheme -> highContrastLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkScheme
        else -> lightScheme
    }

    val semanticColors = when {
        highContrast && darkTheme -> BiteGuardSemanticColors(
            safe = ColorFamily(primaryDarkHighContrast, onPrimaryDarkHighContrast, primaryContainerDarkHighContrast, onPrimaryContainerDarkHighContrast),
            caution = ColorFamily(secondaryDarkHighContrast, onSecondaryDarkHighContrast, secondaryContainerDarkHighContrast, onSecondaryContainerDarkHighContrast),
            danger = ColorFamily(errorDarkHighContrast, onErrorDarkHighContrast, errorContainerDarkHighContrast, onErrorContainerDarkHighContrast),
            info = ColorFamily(tertiaryDarkHighContrast, onTertiaryDarkHighContrast, tertiaryContainerDarkHighContrast, onTertiaryContainerDarkHighContrast),
            isHighContrast = true
        )
        highContrast && !darkTheme -> BiteGuardSemanticColors(
            safe = ColorFamily(primaryLightHighContrast, onPrimaryLightHighContrast, primaryContainerLightHighContrast, onPrimaryContainerLightHighContrast),
            caution = ColorFamily(secondaryLightHighContrast, onSecondaryLightHighContrast, secondaryContainerLightHighContrast, onSecondaryContainerLightHighContrast),
            danger = ColorFamily(errorLightHighContrast, onErrorLightHighContrast, errorContainerLightHighContrast, onErrorContainerLightHighContrast),
            info = ColorFamily(tertiaryLightHighContrast, onTertiaryLightHighContrast, tertiaryContainerLightHighContrast, onTertiaryContainerLightHighContrast),
            isHighContrast = true
        )
        darkTheme -> BiteGuardSemanticColors(
            safe = ColorFamily(primaryDark, onPrimaryDark, primaryContainerDark, onPrimaryContainerDark),
            caution = ColorFamily(secondaryDark, onSecondaryDark, secondaryContainerDark, onSecondaryContainerDark),
            danger = ColorFamily(errorDark, onErrorDark, errorContainerDark, onErrorContainerDark),
            info = ColorFamily(tertiaryDark, onTertiaryDark, tertiaryContainerDark, onTertiaryContainerDark),
            isHighContrast = false
        )
        else -> BiteGuardSemanticColors(
            safe = ColorFamily(primaryLight, onPrimaryLight, primaryContainerLight, onPrimaryContainerLight),
            caution = ColorFamily(secondaryLight, onSecondaryLight, secondaryContainerLight, onSecondaryContainerLight),
            danger = ColorFamily(errorLight, onErrorLight, errorContainerLight, onErrorContainerLight),
            info = ColorFamily(tertiaryLight, onTertiaryLight, tertiaryContainerLight, onTertiaryContainerLight),
            isHighContrast = false
        )
    }

    CompositionLocalProvider(LocalBiteGuardSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
