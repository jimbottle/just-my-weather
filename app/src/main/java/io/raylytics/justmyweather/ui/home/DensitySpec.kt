package io.raylytics.justmyweather.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.raylytics.justmyweather.view.Density

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
    /** Value size for a FULL-span module — the hero of its row. */
    val heroStyle: TextStyle,
    /** Air between grid tiles, both axes. Bordered tiles need more of it than
     * the old bare rows did: two 1dp lines almost touching read as one thick
     * smudged line. */
    val moduleGap: Dp,
    val sectionSpacing: Dp,
)

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
