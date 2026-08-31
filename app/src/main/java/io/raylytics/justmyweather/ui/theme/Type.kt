package io.raylytics.justmyweather.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale tuned for one giant glance: `displayLarge` is the hero
 * temperature, everything else recedes. The family is chosen by the
 * customization layer ([TypeChoice] → [FontFamily]); this stays the single
 * source for sizes/weights so swapping the face changes only the face.
 *
 * EVERY style carries the family, not just the four this app tunes. Material's
 * defaults are hard-wired to sans, and the glance draws a tile's value in a
 * different style at each width (hero when full, smaller when narrower) — so
 * a scale that only re-faced the hero made a Serif user's temperature change
 * font the moment they made it half-width. The same slip put sans labels
 * under a serif hero. Sizes and weights that are not overridden below are
 * Material's; only the face is replaced.
 */
fun appTypography(family: FontFamily): Typography {
    val base = Typography()

    fun TextStyle.faced() = copy(fontFamily = family)
    return Typography(
        displayLarge =
            TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Light,
                fontSize = 120.sp,
                lineHeight = 120.sp,
            ),
        displayMedium = base.displayMedium.faced(),
        displaySmall = base.displaySmall.faced(),
        headlineLarge = base.headlineLarge.faced(),
        headlineMedium = base.headlineMedium.faced(),
        headlineSmall = base.headlineSmall.faced(),
        titleLarge = base.titleLarge.faced(),
        titleMedium =
            TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleSmall = base.titleSmall.faced(),
        bodyLarge = base.bodyLarge.faced(),
        bodyMedium =
            TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        bodySmall = base.bodySmall.faced(),
        labelLarge = base.labelLarge.faced(),
        labelMedium =
            TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.8.sp,
            ),
        labelSmall = base.labelSmall.faced(),
    )
}
