package com.fireflysylestia.virtualgamepadextended.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fireflysylestia.virtualgamepadextended.data.BaseColor
import com.fireflysylestia.virtualgamepadextended.data.ColorScheme
import com.fireflysylestia.virtualgamepadextended.data.getColorFromBaseColor
import androidx.compose.material3.ColorScheme as MaterialColorScheme

/**
 * The surface roles of a palette, tinted with the base colour.
 *
 * Material leaves every one of these at a neutral grey, and this theme only set
 * the button roles. That is why the app — and most visibly the gamepad, which
 * draws itself on `surface` and its controls on `surfaceVariant` — never picked
 * up the chosen colour: the buttons changed, the screens did not.
 */
private class TintedSurfaces(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
)

/**
 * Builds the surfaces for one palette: the base colour taken almost all the way
 * to black in the dark theme and almost all the way to white in the light one,
 * with each role's content taken back the other way so a label never ends up
 * the same shade as the surface it sits on.
 */
private fun tintedSurfaces(base: Color, dark: Boolean): TintedSurfaces {
    fun surface(fraction: Float) = if (dark) darken(base, fraction) else lighten(base, fraction)
    fun content(fraction: Float) = if (dark) lighten(base, fraction) else darken(base, fraction)

    return TintedSurfaces(
        background = surface(0.96f),
        onBackground = content(0.85f),
        surface = surface(0.93f),
        onSurface = content(0.8f),
        surfaceVariant = surface(0.78f),
        onSurfaceVariant = content(0.72f),
        surfaceContainerLowest = if (dark) surface(0.97f) else PristineWhite,
        surfaceContainerLow = surface(0.95f),
        surfaceContainer = surface(0.93f),
        surfaceContainerHigh = surface(0.88f),
        surfaceContainerHighest = surface(0.82f),
        surfaceDim = surface(0.95f),
        surfaceBright = surface(0.7f),
        inverseSurface = content(0.8f),
        inverseOnSurface = surface(0.85f),
        inversePrimary = surface(0.65f),
    )
}

/**
 * Creates a dark mode color palette for the given base color.
 */
private fun createDarkColorPalette(baseColor: BaseColor): MaterialColorScheme {
    val darkColorPrimary = getColorFromBaseColor(baseColor, true)
    val onDarkColorPrimary = contrasting(darkColorPrimary)
    val darkColorSecondary = darken(shift(darkColorPrimary, -45), 0.1f)
    val darkColorTertiary = darken(shift(darkColorPrimary, -75), 0.25f)
    val surfaces = tintedSurfaces(darkColorPrimary, dark = true)

    return darkColorScheme(
        primary = darkColorPrimary,
        secondary = darkColorSecondary,
        tertiary = darkColorTertiary,
        onPrimary = onDarkColorPrimary,
        onSecondary = lighten(onDarkColorPrimary, 0.1f),
        onTertiary = lighten(onDarkColorPrimary, 0.2f),
        primaryContainer = darken(darkColorPrimary, 0.6f),
        secondaryContainer = darken(darkColorSecondary, 0.6f),
        tertiaryContainer = darken(darkColorTertiary, 0.6f),
        onPrimaryContainer = lighten(darkColorPrimary, 0.6f),
        onSecondaryContainer = lighten(darkColorSecondary, 0.6f),
        onTertiaryContainer = lighten(darkColorTertiary, 0.6f),
        background = surfaces.background,
        onBackground = surfaces.onBackground,
        surface = surfaces.surface,
        onSurface = surfaces.onSurface,
        surfaceVariant = surfaces.surfaceVariant,
        onSurfaceVariant = surfaces.onSurfaceVariant,
        surfaceContainerLowest = surfaces.surfaceContainerLowest,
        surfaceContainerLow = surfaces.surfaceContainerLow,
        surfaceContainer = surfaces.surfaceContainer,
        surfaceContainerHigh = surfaces.surfaceContainerHigh,
        surfaceContainerHighest = surfaces.surfaceContainerHighest,
        surfaceDim = surfaces.surfaceDim,
        surfaceBright = surfaces.surfaceBright,
        inverseSurface = surfaces.inverseSurface,
        inverseOnSurface = surfaces.inverseOnSurface,
        inversePrimary = surfaces.inversePrimary,
        outline = Silver,
        outlineVariant = darken(Silver, 0.65f),
    )
}

/**
 * Creates a light mode color palette for the given base color.
 */
private fun createLightColorPalette(baseColor: BaseColor): MaterialColorScheme {
    val lightColorPrimary = getColorFromBaseColor(baseColor, false)
    val onLightColorPrimary = contrasting(lightColorPrimary)
    val lightColorSecondary = lighten(shift(lightColorPrimary, 45), 0.1f)
    val lightColorTertiary = lighten(shift(lightColorPrimary, 75), 0.25f)
    val surfaces = tintedSurfaces(lightColorPrimary, dark = false)

    return lightColorScheme(
        primary = lightColorPrimary,
        secondary = lightColorSecondary,
        tertiary = lightColorTertiary,
        onPrimary = onLightColorPrimary,
        onSecondary = darken(onLightColorPrimary, 0.1f),
        onTertiary = darken(onLightColorPrimary, 0.2f),
        primaryContainer = lighten(lightColorPrimary, 0.6f),
        secondaryContainer = lighten(lightColorSecondary, 0.6f),
        tertiaryContainer = lighten(lightColorTertiary, 0.6f),
        onPrimaryContainer = darken(lightColorPrimary, 0.6f),
        onSecondaryContainer = darken(lightColorSecondary, 0.6f),
        onTertiaryContainer = darken(lightColorTertiary, 0.6f),
        background = surfaces.background,
        onBackground = surfaces.onBackground,
        surface = surfaces.surface,
        onSurface = surfaces.onSurface,
        surfaceVariant = surfaces.surfaceVariant,
        onSurfaceVariant = surfaces.onSurfaceVariant,
        surfaceContainerLowest = surfaces.surfaceContainerLowest,
        surfaceContainerLow = surfaces.surfaceContainerLow,
        surfaceContainer = surfaces.surfaceContainer,
        surfaceContainerHigh = surfaces.surfaceContainerHigh,
        surfaceContainerHighest = surfaces.surfaceContainerHighest,
        surfaceDim = surfaces.surfaceDim,
        surfaceBright = surfaces.surfaceBright,
        inverseSurface = surfaces.inverseSurface,
        inverseOnSurface = surfaces.inverseOnSurface,
        inversePrimary = surfaces.inversePrimary,
        outline = Gold,
        outlineVariant = lighten(Gold, 0.45f),
    )
}

@Composable
fun VirtualGamePadExtendedTheme(
    darkMode: ColorScheme,
    baseColor: BaseColor,
    content: @Composable () -> Unit
) {

    val darkTheme: Boolean = when (darkMode) {
        ColorScheme.LIGHT -> false
        ColorScheme.DARK -> true
        ColorScheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) {
        createDarkColorPalette(baseColor)
    } else {
        createLightColorPalette(baseColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
