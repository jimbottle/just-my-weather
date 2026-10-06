package io.raylytics.justmyweather.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import io.raylytics.justmyweather.view.Conventions

/**
 * The conventions every composable formats through — units, date order,
 * clock — provided once at the root of the app (MainActivity) and of each
 * widget (WeatherWidget), from the region (region/RegionRepository). Static:
 * it changes only when the region or a unit choice does, and then
 * everything below should redraw anyway.
 *
 * The default is what the app showed before regions, so a preview or test
 * that provides nothing reads as it always did.
 */
val LocalConventions = staticCompositionLocalOf { Conventions.US }
