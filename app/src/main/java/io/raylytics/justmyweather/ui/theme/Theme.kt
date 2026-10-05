package io.raylytics.justmyweather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import io.raylytics.justmyweather.view.AccentChoice
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ThemeMood
import io.raylytics.justmyweather.view.TypeChoice

private val LightColors =
    lightColorScheme(
        primary = Accent,
        background = SurfaceLight,
        surface = SurfaceLight,
        surfaceVariant = FaintLight,
        onBackground = InkLight,
        onSurface = InkLight,
        onSurfaceVariant = MutedLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = Accent,
        background = SurfaceDark,
        surface = SurfaceDark,
        surfaceVariant = FaintDark,
        onBackground = InkDark,
        onSurface = InkDark,
        onSurfaceVariant = MutedDark,
    )

/**
 * The app theme, driven by the user's [ThemeConfig]. Mood picks light/dark (or
 * follows the system), accent recolours the single primary hue, and the type
 * choice swaps the face. No dynamic (Material You) colour on purpose — the
 * palette is part of the app's calm, edited identity rather than the
 * wallpaper's.
 *
 * The enum→pixels mapping lives here in one place; the [ThemeConfig] itself
 * stays pure data so it persists and tests without Compose.
 */
@Composable
fun JustMyWeatherTheme(
    config: ThemeConfig = ThemeConfig.DEFAULT,
    content: @Composable () -> Unit,
) {
    val base = if (themeResolvesToDark(config)) DarkColors else LightColors
    MaterialTheme(
        colorScheme = base.withAccent(accentColor(config.accent)),
        typography = appTypography(fontFamily(config.type)),
        content = content,
    )
}

/** Whether [config] resolves to the dark palette right now. Exposed (not just
 * internal to [JustMyWeatherTheme]) because the system-bar icon style must
 * follow the same resolution — the user can force a mood against the system
 * setting, and the bars sit on the app-painted background. */
@Composable
fun themeResolvesToDark(config: ThemeConfig): Boolean =
    when (config.mood) {
        ThemeMood.SYSTEM -> isSystemInDarkTheme()
        ThemeMood.LIGHT -> false
        ThemeMood.DARK -> true
    }

/**
 * The scheme with every accent-carrying role set from [accent] — not just
 * `primary`. Material's defaults for the others are its own purple, and that
 * is exactly what showed through: a selected chip's `secondaryContainer`, a
 * slider's inactive track (the same role), and a switch's checked thumb
 * (`onPrimary`, purple in the dark scheme). Each is derived here so a new
 * accent recolours all of them at once.
 */
private fun ColorScheme.withAccent(accent: Color): ColorScheme {
    val tint = accentTint(accent, surface)
    return copy(
        primary = accent,
        onPrimary = onAccent(accent),
        primaryContainer = tint,
        onPrimaryContainer = onSurface,
        secondary = accent,
        onSecondary = onAccent(accent),
        secondaryContainer = tint,
        onSecondaryContainer = onSurface,
    )
}

/** The accent, faint, over the surface: what a selected chip sits on. */
fun accentTint(accent: Color, surface: Color): Color = accent.copy(alpha = ACCENT_TINT_ALPHA).compositeOver(surface)

/**
 * Black or white, whichever reads better on [accent]. 0.179 is the
 * relative-luminance point where black and white text have equal WCAG
 * contrast — above it black wins, below it white does. (0.5 would hand most
 * of this palette the lower-contrast label.)
 */
fun onAccent(accent: Color): Color = if (accent.luminance() > ON_ACCENT_LUMINANCE) Color.Black else Color.White

private const val ACCENT_TINT_ALPHA = 0.22f
private const val ON_ACCENT_LUMINANCE = 0.179f

/** The pixel colour for an accent choice. Public because the customize
 * screen paints each accent chip with its own colour. */
fun accentColor(accent: AccentChoice): Color =
    when (accent) {
        AccentChoice.AMBER -> Accent
        AccentChoice.TANGERINE -> AccentTangerine
        AccentChoice.ROSE -> AccentRose
        AccentChoice.SKY -> AccentSky
        AccentChoice.SAGE -> AccentSage
        AccentChoice.VIOLET -> AccentViolet
    }

private fun fontFamily(type: TypeChoice): FontFamily =
    when (type) {
        TypeChoice.SANS -> FontFamily.SansSerif
        TypeChoice.SERIF -> FontFamily.Serif
        TypeChoice.MONO -> FontFamily.Monospace
    }
