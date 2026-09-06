package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Detail
import io.raylytics.justmyweather.view.Details
import io.raylytics.justmyweather.view.ForecastElement
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleContent
import io.raylytics.justmyweather.view.degrees
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The forecast module's drawing: a grid of hour or day tiles inside a tile of
 * the glance, with its own Hourly/Daily toggle in its header.
 *
 * It used to be the screen's second grid, a fixed section under the glance
 * that could be switched off but not moved or sized. It is a module now so
 * that every tile carries the same interaction — drag to move, drag the
 * corner to resize — and the size does real work: the inner grid packs
 * exactly as many columns as the module is wide, so an hour tile is one
 * lattice cell wide wherever the module sits, and a taller module simply
 * shows more rows before it scrolls.
 *
 * The Hourly/Daily choice lives HERE, on the forecast, rather than as a
 * screen-wide mode: the forecast is one thing on the page with its own
 * option, instead of the page having three states of which two happen to be
 * forecasts.
 *
 * How many hours the Hourly framing offers is the user's setting
 * (ViewConfig.hourlyHours, 4–168, default a day), carried in on the content.
 * The grid is drawn eagerly, not lazily, so the top of that range composes
 * ~40 rows of tiles whether or not anyone scrolls to them — acceptable for
 * four short texts a tile, and the ceiling is the user's to choose. Every
 * tile names its day ("7 pm 9/6"): the strip crosses midnight, and a tile
 * scrolled into view on its own has no neighbour to tell it from tomorrow's.
 */

/** Hours are terse enough for one cell; a period's name ("Monday Night",
 * "This Afternoon") needs two to survive without ellipsis. */
private const val HOUR_COLUMNS = 1
private const val DAY_COLUMNS = 2

/** Below this many cells across, the header has no room for the word
 * "Forecast" beside its toggle — the toggle alone says what the tile is. */
private const val LABELLED_HEADER_MIN_COLUMNS = 3

/**
 * The forecast, filling the tile it was given.
 *
 * The header is the tile's own label row: "Forecast" and the framing toggle.
 * Below it the framing's grid scrolls inside whatever height is left — the
 * module is a BOX, not a run of content, so its footprint is its cells
 * whatever NWS returned. That inner scroll is switched off while arranging:
 * a child's scroll would otherwise take the very drag the grid needs to move
 * or resize this tile.
 */
@Composable
internal fun ForecastModuleContent(
    content: ModuleContent.Forecast,
    /** The module's width in cells, and so the inner grid's column count. */
    columns: Int,
    gap: Dp,
    arranging: Boolean,
    onSetMode: (ForecastMode) -> Unit,
    /** A tap on an hour or day opens everything it carries; null when
     * tap-for-details is off. Inert while arranging, when a tap on a tile
     * is part of a gesture that belongs to the grid. */
    onOpenDetail: ((Detail) -> Unit)?,
) {
    val open = onOpenDetail?.takeIf { !arranging }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ForecastHeader(
            mode = content.mode,
            onSetMode = onSetMode,
            showLabel = columns >= LABELLED_HEADER_MIN_COLUMNS,
        )
        val viewport =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), enabled = !arranging)
        when (content.mode) {
            ForecastMode.HOURLY ->
                ForecastFrame(items = content.hours, error = content.error) { list ->
                    Box(viewport) {
                        TileGrid(
                            items = list.take(content.hourlyHours),
                            columns = { HOUR_COLUMNS },
                            gap = gap,
                            gridColumns = columns,
                            modifier = Modifier.fillMaxWidth(),
                        ) { hour, _, tileModifier ->
                            HourTile(
                                hour = hour,
                                zone = content.zone,
                                elements = content.elements,
                                modifier = tileModifier.opens(open) { Details.ofHour(hour, content.zone) },
                            )
                        }
                    }
                }

            ForecastMode.DAILY ->
                ForecastFrame(items = content.periods, error = content.error) { list ->
                    // Pairing the half-day periods is pure; cache per list.
                    val days =
                        remember(list, content.dailyStyle) {
                            if (content.dailyStyle == DailyStyle.COMBINED) combineDays(list) else emptyList()
                        }
                    Box(viewport) {
                        when (content.dailyStyle) {
                            DailyStyle.COMBINED ->
                                TileGrid(
                                    items = days,
                                    columns = { DAY_COLUMNS },
                                    gap = gap,
                                    gridColumns = columns,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { day, _, tileModifier ->
                                    CombinedDayTile(day, content.elements, tileModifier.opens(open) { day.detail() })
                                }

                            DailyStyle.HALF_DAY ->
                                TileGrid(
                                    items = list,
                                    columns = { DAY_COLUMNS },
                                    gap = gap,
                                    gridColumns = columns,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { period, _, tileModifier ->
                                    HalfDayTile(
                                        period = period,
                                        elements = content.elements,
                                        modifier = tileModifier.opens(open) { Details.ofPeriod(period) },
                                    )
                                }
                        }
                    }
                }
        }
    }
}

/**
 * A tile that opens a detail on tap, or an inert one when there is nothing
 * to open. No clickable at all in the inert case, so a screen reader is not
 * offered a tap that does nothing and no ripple answers one.
 */
private fun Modifier.opens(open: ((Detail) -> Unit)?, detail: () -> Detail): Modifier =
    if (open == null) this else clickable { open(detail()) }

/**
 * The module's label row: "Forecast" on the left, the framing on the right.
 *
 * The framing is two words rather than two chips. Chips need a row to
 * themselves at two cells wide, and this header has one line — so the chosen
 * framing is the bold, accented word and the other is quiet and tappable.
 * The label stands for the provenance the glance's "Observed" line gives its
 * side: everything in this tile is model output for a grid cell, not a
 * station's measurement, and the two legitimately disagree.
 */
@Composable
private fun ForecastHeader(
    mode: ForecastMode,
    onSetMode: (ForecastMode) -> Unit,
    showLabel: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (showLabel) Arrangement.SpaceBetween else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLabel) {
            Text(
                text = "Forecast",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ForecastMode.entries.forEach { entry ->
                val selected = entry == mode
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier =
                        Modifier
                            .clickable { onSetMode(entry) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("forecast_${entry.key}"),
                )
            }
        }
    }
}

/*
 * The tiles. Each fills its row (the flow grid sizes a row to its tallest
 * tile) and lays out in three zones: when at the top, how warm in the
 * middle, what it is like at the bottom. Zoned rather than stacked so the
 * same thing sits at the same height in every tile of a row — the eye reads
 * the temperatures across, then the hours across — even when one tile has a
 * chance of rain to show and its neighbour does not. The user's elements go
 * under the temperature, in the quiet style, and conditions alone take the
 * bottom.
 */

/** One hour: when, how warm, and whatever the user has switched on. */
@Composable
private fun HourTile(
    hour: ForecastPoint,
    zone: ZoneId,
    elements: Set<ForecastElement>,
    modifier: Modifier = Modifier,
) {
    val at = hour.startTime.atZone(zone)
    ZonedTile(
        top = "${at.format(hourFormat).lowercase(Locale.getDefault())} ${at.format(shortDateFormat)}",
        bottom = hour.shortForecast?.takeIf { ForecastElement.CONDITIONS in elements },
        modifier = modifier,
        below = {
            ElementLines(
                elements = elements,
                precipChance = hour.precipProbabilityPercent,
                windMph = hour.windMph,
                windDirection = hour.windDirection,
                humidity = hour.relativeHumidityPercent,
                dewpointF = hour.dewpointF,
            )
        },
    ) {
        Text(
            text = hour.temperatureF.degrees(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** One day: its name, the high beside the quieter low, and NWS's summary. */
@Composable
private fun CombinedDayTile(
    day: DayForecast,
    elements: Set<ForecastElement>,
    modifier: Modifier = Modifier,
) {
    ZonedTile(
        top = day.name,
        bottom = day.shortForecast?.takeIf { ForecastElement.CONDITIONS in elements },
        modifier = modifier,
        below = {
            ElementLines(
                elements = elements,
                // Either half's chance: the day's rain is the day's rain.
                precipChance =
                    listOfNotNull(day.day?.precipProbabilityPercent, day.night?.precipProbabilityPercent).maxOrNull(),
                windMph = day.day?.windMph,
                windDirection = day.day?.windDirection,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = day.highF.degrees(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = day.lowF.degrees(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One of NWS's native half-day periods, for the user who wants day and night
 * kept apart rather than folded into a high and a low. */
@Composable
private fun HalfDayTile(
    period: DailyPeriod,
    elements: Set<ForecastElement>,
    modifier: Modifier = Modifier,
) {
    ZonedTile(
        top = period.name,
        bottom = period.shortForecast?.takeIf { ForecastElement.CONDITIONS in elements },
        modifier = modifier,
        below = {
            ElementLines(
                elements = elements,
                precipChance = period.precipProbabilityPercent,
                windMph = period.windMph,
                windDirection = period.windDirection,
            )
        },
    ) {
        Text(
            text = period.temperatureF.degrees(),
            style = MaterialTheme.typography.titleLarge,
            // The daytime high carries the emphasis and the night the
            // quieter tone, so which is which survives being read out of
            // order.
            color =
                if (period.isDaytime) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * The three zones. [top] is pinned to the top edge, [bottom] (when there is
 * one) to the bottom, and the [middle] — the temperature — sits exactly
 * halfway between them. What the user has switched on ([below]) hangs under
 * the temperature in the lower half, and does not move it: the two halves
 * are weighted equally, and the lower one is measured with its content, so
 * a tile with a chance of rain grows both halves by the same amount and its
 * temperature stays level with a neighbour's that has none. (Seen on the
 * Pixel 9 before this: "1%" under one tile's number lifted it a line above
 * the next tile's.)
 *
 * Weighted slots, not a Box with alignments, because a Column's intrinsic
 * height is the SUM of its children and a Box's is the tallest, and the row
 * is sized by that intrinsic: a Box would let the zones overlap in a short
 * row.
 */
@Composable
private fun ZonedTile(
    top: String,
    bottom: String?,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {},
    middle: @Composable ColumnScope.() -> Unit,
) {
    TileShell(borderColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = top,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            middle()
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = below,
            )
            bottom?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Always two lines tall, even for "Clear": the bottom
                    // zone's height is what fixes the middle's, and a row
                    // where "Mostly Clear" wraps beside a "Clear" that does
                    // not put the two temperatures at different heights
                    // (seen on the Pixel 9). An empty second line is the
                    // price of the temperatures reading straight across.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The user's elements, one quiet line each under the temperature, and only
 * when there is a value: a "0%" on every dry hour, or a "—" for a humidity
 * the day tiles never carry, is a screen of noise carrying no information.
 * Chance of rain keeps its accent — it is the one line worth a glance.
 */
@Composable
private fun ElementLines(
    elements: Set<ForecastElement>,
    precipChance: Double? = null,
    windMph: Double? = null,
    windDirection: String? = null,
    humidity: Double? = null,
    dewpointF: Double? = null,
) {
    if (ForecastElement.PRECIP_CHANCE in elements) {
        precipChance?.takeIf { it > 0 }?.let {
            Text(
                text = "${it.roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (ForecastElement.WIND in elements && windMph != null) QuietLine(Details.wind(windMph, windDirection))
    if (ForecastElement.HUMIDITY in elements && humidity != null) QuietLine("RH ${humidity.roundToInt()}%")
    if (ForecastElement.DEW_POINT in elements && dewpointF != null) QuietLine("Dew ${dewpointF.roundToInt()}°")
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** Shared load/error/empty framing: null items = first fetch still in flight;
 * an error shows in place quietly. Loaded data always wins over an error — a
 * stale message must never cover a grid we can actually draw. */
@Composable
private fun <T> ForecastFrame(
    items: List<T>?,
    error: String?,
    content: @Composable (List<T>) -> Unit,
) {
    when {
        !items.isNullOrEmpty() -> content(items)
        error != null ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        items != null ->
            // Fetched fine, but NWS had nothing for this framing.
            Text(
                text = "No forecast available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        else ->
            Text(
                text = "…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
    }
}
