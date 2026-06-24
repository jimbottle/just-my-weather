package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot

/** A field resolved to what the screen shows: its label and its value string. */
data class DisplayValue(
    val field: WeatherField,
    val label: String,
    val value: String,
)

/**
 * The home view reduced to render-ready data: the [hero] (first visible field,
 * shown large) and the [rows] beneath it. Pure, so the mapping from "user's
 * config + latest weather" to "what's on screen" is testable without Compose.
 */
data class RenderedView(
    val hero: DisplayValue?,
    val rows: List<DisplayValue>,
)

/** Project a snapshot through the user's config. Missing data renders as "—"
 * rather than vanishing, so an enabled-but-empty field reads as honest absence
 * instead of a layout that silently dropped it. */
fun ViewConfig.render(snapshot: WeatherSnapshot): RenderedView {
    val values =
        visible.map { setting ->
            DisplayValue(
                field = setting.field,
                label = setting.label,
                value = setting.field.format(snapshot) ?: "—",
            )
        }
    return RenderedView(hero = values.firstOrNull(), rows = values.drop(1))
}
