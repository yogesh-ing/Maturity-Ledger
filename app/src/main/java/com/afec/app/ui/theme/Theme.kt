package com.afec.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * Every M3 colour role is specified explicitly. Leaving a role unset makes M3
 * fall back to the baseline (purple-tinted) palette, which silently mixed a
 * second design system into the app — most visibly in the bottom sheet, the
 * date picker and the navigation bar.
 */
private val LightColors = lightColorScheme(
    primary = Primary600, onPrimary = Color.White,
    primaryContainer = Primary100, onPrimaryContainer = Primary900,
    inversePrimary = Primary300,
    secondary = Accent500, onSecondary = Color.White,
    secondaryContainer = Accent50, onSecondaryContainer = Accent700,
    tertiary = ComfortableGreen, onTertiary = Color.White,
    tertiaryContainer = ComfortableGreenContainer, onTertiaryContainer = ComfortableOnGreenContainer,
    background = Slate50, onBackground = Slate900,
    surface = Color.White, onSurface = Slate900,
    surfaceVariant = Slate100, onSurfaceVariant = Slate600,
    surfaceTint = Primary600,
    inverseSurface = Slate700, inverseOnSurface = Slate50,
    error = UrgentRed, onError = Color.White,
    errorContainer = UrgentRedContainer, onErrorContainer = UrgentOnRedContainer,
    outline = Slate300, outlineVariant = Slate200,
    scrim = Color.Black,
    surfaceBright = Color.White, surfaceDim = Slate100,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate50,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Slate200,
    surfaceContainerHighest = Slate200
)

private val DarkColors = darkColorScheme(
    primary = Primary300, onPrimary = Primary900,
    primaryContainer = Primary700, onPrimaryContainer = Primary100,
    inversePrimary = Primary600,
    secondary = Accent300, onSecondary = Accent700,
    secondaryContainer = Accent700, onSecondaryContainer = Accent50,
    tertiary = ComfortableGreenDark, onTertiary = Color(0xFF022C22),
    tertiaryContainer = DarkComfortableGreenContainer,
    onTertiaryContainer = DarkOnComfortableContainer,
    background = DarkBase, onBackground = DarkOnSurface,
    surface = DarkContainer, onSurface = DarkOnSurface,
    surfaceVariant = DarkContainerHigh, onSurfaceVariant = Slate300,
    surfaceTint = Primary300,
    inverseSurface = DarkOnSurface, inverseOnSurface = DarkBase,
    error = DarkError, onError = DarkOnError,
    errorContainer = DarkErrorContainer, onErrorContainer = DarkOnUrgentContainer,
    outline = Slate600, outlineVariant = DarkContainerHighest,
    scrim = Color.Black,
    surfaceBright = DarkSurfaceBright, surfaceDim = DarkBase,
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Human labels for the theme selector (enum constants are not UI copy). */
fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

/**
 * FR-14: light/dark theming with the PRD §06 token set.
 *
 * [dynamicColor] is opt-in ("Use wallpaper colours" in Settings, Android 12+).
 * It used to be always-on, which meant the indigo/teal brand system — and the
 * urgency palette the dashboard hierarchy depends on — was never visible on
 * most devices. Brand tokens are now the default; Material You is a choice.
 */
@Composable
fun AfecTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
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
