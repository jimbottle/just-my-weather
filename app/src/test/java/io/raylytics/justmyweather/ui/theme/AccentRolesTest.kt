package io.raylytics.justmyweather.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The accent-derived roles are pure colour maths, so they test here; the
 * scheme that uses them is Compose and is not. */
class AccentRolesTest {
    @Test
    fun `labels are black on every accent in the palette, white only on a truly dark colour`() {
        // The whole palette is kept bright enough to read against the
        // near-monochrome surface, so black wins everywhere — rose and violet
        // included, which sit just above the 0.179 line.
        for (accent in listOf(Accent, AccentTangerine, AccentRose, AccentSky, AccentSage, AccentViolet)) {
            assertEquals(Color.Black, onAccent(accent), "on $accent")
        }
        assertEquals(Color.White, onAccent(InkLight))
        assertEquals(Color.White, onAccent(Color(0xFF1B4FBF)))
    }

    @Test
    fun `the tint is a faint wash of the accent over the surface, in both moods`() {
        val light = accentTint(Accent, SurfaceLight)
        val dark = accentTint(Accent, SurfaceDark)
        assertEquals(1f, light.alpha)
        assertNotEquals(SurfaceLight, light)
        assertNotEquals(SurfaceDark, dark)

        // Still a surface, not a block of colour: nearer the surface than the accent.
        fun distance(
            a: Color,
            b: Color,
        ) = Math.abs(a.red - b.red) + Math.abs(a.green - b.green) + Math.abs(a.blue - b.blue)
        assertTrue(distance(light, SurfaceLight) < distance(light, Accent))
        assertTrue(distance(dark, SurfaceDark) < distance(dark, Accent))
        assertTrue(dark.luminance() < 0.2f, "a dark mood stays dark under the wash")
    }
}
