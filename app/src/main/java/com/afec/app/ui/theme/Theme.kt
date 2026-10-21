package com.afec.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Primary600, onPrimary = Slate50,
    primaryContainer = Primary100, onPrimaryContainer = Primary900,
    secondary = Accent500, onSecondary = Slate50,
    secondaryContainer = Accent50, onSecondaryContainer = Accent700,
    tertiary = ComfortableGreen, onTertiary = Slate50,
    tertiaryContainer = ComfortableGreenContainer,
    onTertiaryContainer = ComfortableOnGreenContainer,
    background = Slate50, onBackground = Slate900,
    surface = Slate50, onSurface = Slate900,
    surfaceVariant = Slate100, onSurfaceVariant = Slate700,
    surfaceContainer = Slate100, surfaceContainerHigh = Slate200,
    outline = Slate300, outlineVariant = Slate200,
    error = UrgentRed, errorContainer = UrgentRedContainer,
    onErrorContainer = UrgentOnRedContainer
)

private val DarkColors = darkColorScheme(
    primary = Primary300, onPrimary = Primary900,
    primaryContainer = Primary700, onPrimaryContainer = Primary100,
    secondary = Accent300, onSecondary = Accent700,
    secondaryContainer = Accent700, onSecondaryContainer = Accent50,
    tertiary = ComfortableGreen, onTertiary = DarkBase,
    tertiaryContainer = DarkComfortableGreenContainer,
    onTertiaryContainer = ComfortableGreenContainer,
    background = DarkBase, onBackground = DarkOnSurface,
    surface = DarkBase, onSurface = DarkOnSurface,
    surfaceVariant = DarkRaised, onSurfaceVariant = DarkOnSurface,
    surfaceContainer = DarkRaised, surfaceContainerHigh = DarkOverlay,
    outline = DarkOverlay, outlineVariant = DarkRaised,
    error = UrgentRed, errorContainer = DarkUrgentRedContainer,
    onErrorContainer = UrgentRedContainer
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** FR-14: full light/dark theming with Material 3 tokens (PRD §06). */
@Composable
fun AfecTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // FR-14: Material You dynamic color on Android 12+ (API 31), PRD tokens otherwise.
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AfecTypography,
        shapes = AfecShapes,
        content = content
    )
}
