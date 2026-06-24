package io.raylytics.justmyweather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
 * The app theme. Follows the system light/dark mood by default; the
 * customization layer will later let the user pin one or pick their own
 * accent. No dynamic (Material You) colour on purpose — the palette is part of
 * the app's calm, edited identity rather than the wallpaper's.
 */
@Composable
fun JustMyWeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
