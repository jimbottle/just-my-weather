package io.raylytics.justmyweather.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ModuleSpan

/**
 * The concrete look for each [Density] level: how big a full-width module's
 * value is and how much air sits between tiles and blocks. This is the UI half
 * of density — it speaks Compose, while the [Density] enum stays a pure,
 * persistable choice. Keeping it in one `when` makes dialing the spectrum a
 * single legible place.
 *
 * The hero style is built by resizing the theme's `displayLarge` so font family
 * and weight stay sourced from the type scale; only the size changes.
 * COMFORTABLE leaves it untouched, so the shipped default looks exactly as
 * before.
 */
data class DensitySpec(
    /** Value style for a FULL-span module — the hero of its row. Its size is
     * also the ceiling a fitted value may grow to at that width. */
    val heroStyle: TextStyle,
    /** Air between grid tiles, both axes. Bordered tiles need more of it than
     * the old bare rows did: two 1dp lines almost touching read as one thick
     * smudged line. */
    val moduleGap: Dp,
    val sectionSpacing: Dp,
) {
    /**
     * The largest a reading may draw at [span]. Width IS prominence, so the
     * ceilings step down with the span — but they are ceilings, not sizes: a
     * value that will not fit at its ceiling shrinks until it does (see
     * `FittedText`), so a long phrase in a narrow tile wraps to a smaller size
     * instead of breaking mid-word or spilling out of its border.
     *
     * Derived from the hero rather than fixed, so the whole spectrum moves
     * together when a density is dialed: a Compact half tile is smaller than a
     * Spacious one for the same reason its hero is.
     */
    fun valueCeiling(span: ModuleSpan): TextUnit =
        when (span) {
            ModuleSpan.FULL -> heroStyle.fontSize
            ModuleSpan.HALF -> heroStyle.fontSize * HALF_VALUE_SHARE
            ModuleSpan.QUARTER -> heroStyle.fontSize * QUARTER_VALUE_SHARE
        }

    private companion object {
        // A half tile is half the width; its value gets a bit under half the
        // size, so two halves side by side still read as a step below the hero
        // rather than as two heroes.
        const val HALF_VALUE_SHARE = 0.4f
        const val QUARTER_VALUE_SHARE = 0.25f
    }
}

/** Below this a reading stops being legible at arm's length; a value that
 * cannot fit even here is ellipsised rather than shrunk further. */
internal val VALUE_FLOOR = 14.sp

@Composable
@ReadOnlyComposable
fun Density.spec(): DensitySpec {
    val hero = MaterialTheme.typography.displayLarge
    return when (this) {
        Density.SPACIOUS ->
            DensitySpec(
                heroStyle = hero.copy(fontSize = 132.sp, lineHeight = 132.sp),
                moduleGap = 10.dp,
                sectionSpacing = 18.dp,
            )

        Density.COMFORTABLE ->
            DensitySpec(
                heroStyle = hero,
                moduleGap = 8.dp,
                sectionSpacing = 8.dp,
            )

        Density.COMPACT ->
            DensitySpec(
                heroStyle = hero.copy(fontSize = 96.sp, lineHeight = 100.sp),
                moduleGap = 6.dp,
                sectionSpacing = 4.dp,
            )
    }
}
