package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot

/** A field resolved to what its module shows: label, value string, and how
 * wide the module sits on the grid. */
data class ModuleValue(
    val field: WeatherField,
    val label: String,
    val value: String,
    val span: ModuleSpan,
)

/**
 * The glance reduced to render-ready data: the visible fields, in the user's
 * order, as grid modules. Pure, so the mapping from "user's config + latest
 * weather" to "what's on screen" is testable without Compose. There is no
 * special hero slot — a module's prominence is its width, which is the whole
 * idea of the grid.
 */
data class RenderedView(
    val modules: List<ModuleValue>,
)

/** Project a snapshot through the user's config. Missing data renders as "—"
 * rather than vanishing, so an enabled-but-empty field reads as honest absence
 * instead of a layout that silently dropped it. */
fun ViewConfig.render(snapshot: WeatherSnapshot): RenderedView =
    RenderedView(
        visible.map { setting ->
            ModuleValue(
                field = setting.field,
                label = setting.label,
                value = setting.field.format(snapshot) ?: "—",
                span = setting.span,
            )
        },
    )
