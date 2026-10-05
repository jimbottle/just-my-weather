package io.raylytics.justmyweather.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontFamily
import androidx.glance.unit.ColorProvider
import io.raylytics.justmyweather.ui.theme.FaintDark
import io.raylytics.justmyweather.ui.theme.FaintLight
import io.raylytics.justmyweather.ui.theme.InkDark
import io.raylytics.justmyweather.ui.theme.InkLight
import io.raylytics.justmyweather.ui.theme.MutedDark
import io.raylytics.justmyweather.ui.theme.MutedLight
import io.raylytics.justmyweather.ui.theme.SurfaceDark
import io.raylytics.justmyweather.ui.theme.SurfaceLight
import io.raylytics.justmyweather.ui.theme.accentColor
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ThemeMood
import io.raylytics.justmyweather.view.TypeChoice

/**
 * The glance's look, translated for the launcher.
 *
 * The app's theme is a Material colour scheme and a type scale; a widget has
 * neither, only colours it can name per element and a font family. So this
 * maps the same [ThemeConfig] onto the same palette (`ui/theme/Color.kt`) as
 * [androidx.glance.unit.ColorProvider]s: a mood that follows the system
 * becomes a day/night pair the launcher switches itself, a forced mood a
 * fixed colour. The accent is the user's; the face is the user's. Nothing
 * here is a new colour — a widget beside the open app should look like a
 * piece of it.
 */
data class WidgetPalette(
    val background: ColorProvider,
    val ink: ColorProvider,
    val muted: ColorProvider,
    val faint: ColorProvider,
    val accent: ColorProvider,
    val font: FontFamily,
) {
    companion object {
        fun of(theme: ThemeConfig): WidgetPalette {
            val accent = accentColor(theme.accent)
            return when (theme.mood) {
                ThemeMood.SYSTEM ->
                    WidgetPalette(
                        background = ColorProvider(day = SurfaceLight, night = SurfaceDark),
                        ink = ColorProvider(day = InkLight, night = InkDark),
                        muted = ColorProvider(day = MutedLight, night = MutedDark),
                        faint = ColorProvider(day = FaintLight, night = FaintDark),
                        accent = ColorProvider(accent),
                        font = font(theme.type),
                    )
                ThemeMood.LIGHT -> fixed(SurfaceLight, InkLight, MutedLight, FaintLight, accent, theme.type)
                ThemeMood.DARK -> fixed(SurfaceDark, InkDark, MutedDark, FaintDark, accent, theme.type)
            }
        }

        private fun fixed(
            background: Color,
            ink: Color,
            muted: Color,
            faint: Color,
            accent: Color,
            type: TypeChoice,
        ) = WidgetPalette(
            background = ColorProvider(background),
            ink = ColorProvider(ink),
            muted = ColorProvider(muted),
            faint = ColorProvider(faint),
            accent = ColorProvider(accent),
            font = font(type),
        )

        private fun font(type: TypeChoice): FontFamily =
            when (type) {
                TypeChoice.SANS -> FontFamily.SansSerif
                TypeChoice.SERIF -> FontFamily.Serif
                TypeChoice.MONO -> FontFamily.Monospace
            }
    }
}

/**
 * The widget half of density — the launcher's `DensitySpec`. The same three
 * levels, the same idea: the ceiling a value may grow to and the air around
 * it. The ceilings match the glance's hero sizes so a widget at the width of
 * the glance draws the number at the same size the glance would.
 */
data class WidgetDensitySpec(
    /** The largest a fitted value may draw, in sp. */
    val valueCeilingSp: Float,
    /** Inset from the widget's edge to its content. */
    val padding: Dp,
    /** Air between forecast tiles and sun rows. */
    val gap: Dp,
    /** Height of one forecast tile, before its element lines: the time, the
     * temperature and a two-line bottom, with a little air (verified on the
     * emulator at 88dp, where the air was most of the tile). */
    val tileHeight: Dp,
) {
    companion object {
        fun of(density: Density): WidgetDensitySpec =
            when (density) {
                Density.SPACIOUS ->
                    WidgetDensitySpec(valueCeilingSp = 132f, padding = 14.dp, gap = 8.dp, tileHeight = 80.dp)
                Density.COMFORTABLE ->
                    WidgetDensitySpec(valueCeilingSp = 120f, padding = 10.dp, gap = 6.dp, tileHeight = 72.dp)
                Density.COMPACT ->
                    WidgetDensitySpec(valueCeilingSp = 96f, padding = 6.dp, gap = 4.dp, tileHeight = 64.dp)
            }
    }
}
