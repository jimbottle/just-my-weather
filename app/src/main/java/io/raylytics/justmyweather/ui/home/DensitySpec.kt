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
 * The concrete look for each [Density] level: how big the hero is and how much
 * air sits between rows and blocks. This is the UI half of density — it speaks
 * Compose, while the [Density] enum stays a pure, persistable choice. Keeping it
 * in one `when` makes dialing the spectrum a single legible place.
 *
 * The hero is built by resizing the theme's `displayLarge` so font family and
 * weight stay sourced from the type scale; only the size changes. COMFORTABLE
 * leaves it untouched, so the shipped default looks exactly as before.
 */
data class DensitySpec(
    val heroStyle: TextStyle,
    val rowSpacing: Dp,
    val sectionSpacing: Dp,
    val rowMaxWidth: Dp,
)

@Composable
@ReadOnlyComposable
fun Density.spec(): DensitySpec {
    val hero = MaterialTheme.typography.displayLarge
    return when (this) {
        Density.SPACIOUS ->
            DensitySpec(
                heroStyle = hero.copy(fontSize = 132.sp, lineHeight = 132.sp),
                rowSpacing = 10.dp,
                sectionSpacing = 18.dp,
                rowMaxWidth = 320.dp,
            )

        Density.COMFORTABLE ->
            DensitySpec(
                heroStyle = hero,
                rowSpacing = 6.dp,
                sectionSpacing = 8.dp,
                rowMaxWidth = 320.dp,
            )

        Density.COMPACT ->
            DensitySpec(
                heroStyle = hero.copy(fontSize = 96.sp, lineHeight = 100.sp),
                rowSpacing = 2.dp,
                sectionSpacing = 4.dp,
                rowMaxWidth = 360.dp,
            )
    }
}
