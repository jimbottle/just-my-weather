package io.raylytics.justmyweather.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The type scale must carry the chosen face in EVERY style, because the glance
 * draws one value in different styles at different tile widths. A scale that
 * re-faced only the styles this app tunes let a Serif user's temperature turn
 * sans the moment they made its tile half-width — the regression this pins.
 */
class TypographyTest {
    /** Every style Material 3 defines, so a new one cannot slip through unfaced. */
    private fun Typography.allStyles(): Map<String, TextStyle> =
        mapOf(
            "displayLarge" to displayLarge,
            "displayMedium" to displayMedium,
            "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge,
            "titleMedium" to titleMedium,
            "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge,
            "bodyMedium" to bodyMedium,
            "bodySmall" to bodySmall,
            "labelLarge" to labelLarge,
            "labelMedium" to labelMedium,
            "labelSmall" to labelSmall,
        )

    @Test
    fun everyStyleWearsTheChosenFamily() {
        listOf(FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace).forEach { family ->
            appTypography(family).allStyles().forEach { (name, style) ->
                assertEquals(family, style.fontFamily, "$name should be $family")
            }
        }
    }

    @Test
    fun theHeroKeepsItsTunedSize() {
        // Re-facing the rest of the scale must not disturb the one style the
        // glance is built around.
        val hero = appTypography(FontFamily.Serif).displayLarge
        assertEquals(120f, hero.fontSize.value)
        assertEquals(120f, hero.lineHeight.value)
    }
}
