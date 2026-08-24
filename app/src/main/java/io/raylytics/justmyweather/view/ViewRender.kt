package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import java.time.ZoneId

/**
 * What a module actually has to draw.
 *
 * Most modules are one string, and were the only kind until sun times moved
 * onto the grid: sunrise and sunset are a small table, not a value, and
 * flattening them into "next sunrise / next sunset" would have thrown away the
 * reason day rows were chosen in the first place — between sunrise and sunset,
 * "the next sunrise" and "the next sunset" fall on different dates, and a row
 * carrying its own date says which is which without hanging a "tomorrow" off a
 * time.
 *
 * Exhaustive, so a new kind of module forces the tile to decide how it draws.
 */
sealed interface ModuleContent {
    /** A single reading, already formatted ("72°", "Calm", "—"). */
    data class Reading(val text: String) : ModuleContent

    /**
     * Sun times, today first, with the zone they are to be read in — the
     * PLACE's, not the device's. The zone travels with the days because they
     * are instants: the same sunrise formats as two different clock times
     * depending on where you ask from, and the answer the user wants is the
     * one local to the place they are looking at.
     *
     * The tile decides how much of this it can show at its width; the days
     * themselves are not the tile's to choose.
     */
    data class Sun(val days: List<SunDay>, val zone: ZoneId) : ModuleContent
}

/** A module resolved to what the screen shows: its label, its content, and how
 * wide it sits. */
data class ModuleValue(
    val module: ModuleKey,
    val label: String,
    val span: ModuleSpan,
    val content: ModuleContent,
)

/**
 * The glance reduced to render-ready data: the visible modules, in the user's
 * order. Pure, so the mapping from "user's config + latest weather" to "what's
 * on screen" is testable without Compose. There is no special hero slot — a
 * module's prominence is its width, which is the whole idea of the grid.
 */
data class RenderedView(
    val modules: List<ModuleValue>,
)

/**
 * Project a snapshot through the user's config. Missing data renders as "—"
 * rather than vanishing, so an enabled-but-empty module reads as honest
 * absence instead of a layout that silently dropped it.
 *
 * [sunDays] comes in separately because it is not measured at a station: it is
 * worked out on the device from the date and where you are, which is why the
 * sun module keeps working with no signal.
 */
fun ViewConfig.render(
    snapshot: WeatherSnapshot,
    sunDays: List<SunDay> = emptyList(),
    /** Which zone the sun times read in. Defaults to the device's, which is
     * the honest fallback while the place's own zone is unknown. */
    zone: ZoneId = ZoneId.systemDefault(),
): RenderedView =
    RenderedView(
        visible.map { setting ->
            ModuleValue(
                module = setting.module,
                label = setting.label,
                span = setting.span,
                content =
                    when (val module = setting.module) {
                        is ModuleKey.Reading ->
                            ModuleContent.Reading(module.field.format(snapshot) ?: "—")
                        ModuleKey.Sun -> ModuleContent.Sun(sunDays, zone)
                    },
            )
        },
    )
