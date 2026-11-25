package com.forensim.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    // Same structure as the light theme: a near-black page with charcoal cards and bars on top.
    // Light maroon is the accent (buttons, selection, links); everything else is neutral grey.
    primary = Palette.SoftMaroon,
    onPrimary = Color(0xFF1E0E13), // dark text on maroon reads better than white (4.9:1 vs 3.6:1)
    primaryContainer = Palette.Night3,
    onPrimaryContainer = Palette.TextHigh,
    secondary = Palette.MutedPeach,
    onSecondary = Color(0xFF2A1A1F),
    secondaryContainer = Palette.HighlightDark,
    onSecondaryContainer = Palette.TextHigh,
    inverseSurface = Palette.Surface,
    inverseOnSurface = Palette.Ink,
    inversePrimary = Palette.Maroon,
    tertiary = Palette.TextMuted,
    onTertiary = Palette.Night,
    tertiaryContainer = Palette.Night3,
    onTertiaryContainer = Palette.TextHigh,
    background = Palette.Night,
    onBackground = Palette.TextHigh,
    surface = Palette.NightSurface,
    onSurface = Palette.TextHigh,
    surfaceVariant = Palette.NightHair,
    onSurfaceVariant = Palette.TextMuted,
    surfaceTint = Palette.NightSurface,
    surfaceContainerLowest = Palette.Night0,
    surfaceContainerLow = Palette.NightSurface,
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.Night3,
    surfaceContainerHighest = Palette.Night4,
    outline = Palette.NightLine,
    outlineVariant = Palette.NightHair,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A08),
    errorContainer = Color(0xFF3B1C1C),
    onErrorContainer = Color(0xFFFFD6D1),
    scrim = Color(0xFF000000),
)

private val LightScheme = lightColorScheme(
    // Deep maroon where the old theme used near-black fills (buttons, tabs, switches, headers);
    // text stays near-black ink for readability and green stays the success accent.
    primary = Palette.DeepMaroon,
    onPrimary = Palette.White,
    primaryContainer = Palette.LightSurfaceHigh,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.DeeperMaroon,
    onSecondary = Palette.White,
    secondaryContainer = Palette.LightSurfaceHigh,
    onSecondaryContainer = Palette.Ink,
    inverseSurface = Palette.DeeperMaroon,
    inverseOnSurface = Palette.White,
    inversePrimary = Palette.SoftMaroon,
    tertiary = Palette.InkMuted,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.GreenTint,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Page,
    onBackground = Palette.Ink,
    surface = Palette.White,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.LightSurfaceHighest,
    onSurfaceVariant = Palette.InkMuted,
    surfaceTint = Palette.White,
    surfaceContainerLowest = Palette.White,
    surfaceContainerLow = Palette.White,
    surfaceContainer = Palette.White,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.LightSurfaceHigh,
    outline = Palette.LightOutline,
    outlineVariant = Palette.LightHair,
    error = Color(0xFFD93A3A),
    onError = Palette.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1414),
)

/** 8dp spacing rhythm (plan section 10). */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp

    /** Android minimum touch target. */
    val touchTarget: Dp = 48.dp

    /** Max readable width for long-form lesson text on tablets. */
    val readableWidth: Dp = 680.dp
}

/** Shared motion tokens. Callers skip animation when reduced motion is requested. */
object Motion {
    const val PRESS_MS = 120
    const val ENTER_MS = 220
    const val EXIT_MS = 160
    /** Staggered entrance of result/hero content. */
    const val REVEAL_MS = 420
    const val COUNT_UP_MS = 1100
}

/** Theme preference persisted in DataStore. */
enum class ThemePreference { SYSTEM, DARK, LIGHT }

val LocalForensimColors = staticCompositionLocalOf { DarkForensimColors }

/** True when the user asked for reduced motion (system animator scale 0 or in-app toggle). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Crisp, modest corners (cards 12 dp) in the marketplace style; pills stay fully round. */
private val ForensimShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun ForensimTheme(
    preference: ThemePreference = ThemePreference.DARK,
    reducedMotion: Boolean = false,
    font: AppFont = AppFont.DEFAULT,
    content: @Composable () -> Unit,
) {
    val typography = androidx.compose.runtime.remember(font) { forensimTypography(fontFamilyFor(font)) }
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }
    CompositionLocalProvider(
        LocalForensimColors provides if (dark) DarkForensimColors else LightForensimColors,
        LocalReducedMotion provides reducedMotion,
        // Android's autofill highlight is a square olive fill that ignores the fields' rounded
        // corners; autofilled fields look like any other field instead.
        androidx.compose.foundation.text.LocalAutofillHighlightColor provides Color.Transparent,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = typography,
            shapes = ForensimShapes,
            content = content,
        )
    }
}

/** Accessor for semantic colours: `ForensimTheme.colors.success`. */
object ForensimTheme {
    val colors: ForensimColors
        @Composable get() = LocalForensimColors.current
}
