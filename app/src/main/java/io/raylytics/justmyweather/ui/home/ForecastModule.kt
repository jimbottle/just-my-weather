package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleContent
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
 */

/**
 * How many hours the Hourly framing offers. A day's worth: six rows of four,
 * of which the module shows a couple and the rest is a scroll away.
 *
 * This is bounded rather than NWS's full ~156 points because the grid is drawn
 * eagerly, not lazily — thirty-nine rows of tiles would be composed whether or
 * not anyone scrolled to them. Twenty-four is the span people actually plan
 * against. Every tile still names its day ("7 pm 9/6"): the strip crosses
 * midnight, and a tile scrolled into view on its own has no neighbour to
 * tell it from tomorrow's.
 */
private const val HOURLY_TILES = 24

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
                            items = list.take(HOURLY_TILES),
                            columns = { HOUR_COLUMNS },
                            gap = gap,
                            gridColumns = columns,
                            modifier = Modifier.fillMaxWidth(),
                        ) { hour, _, tileModifier ->
                            HourTile(
                                hour = hour,
                                zone = content.zone,
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
                                    CombinedDayTile(day, tileModifier.opens(open) { day.detail() })
                                }

                            DailyStyle.HALF_DAY ->
                                TileGrid(
                                    items = list,
                                    columns = { DAY_COLUMNS },
                                    gap = gap,
                                    gridColumns = columns,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { period, _, tileModifier ->
                                    HalfDayTile(period, tileModifier.opens(open) { Details.ofPeriod(period) })
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

/** One hour: when, how likely rain is, how warm. */
@Composable
private fun HourTile(hour: ForecastPoint, zone: ZoneId, modifier: Modifier = Modifier) {
    TileShell(borderColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // "7 pm 9/6": the hour, then the day it belongs to, on one line.
            val at = hour.startTime.atZone(zone)
            Text(
                text = "${at.format(hourFormat).lowercase(Locale.getDefault())} ${at.format(shortDateFormat)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = hour.temperatureF?.let { "${it.roundToInt()}°" } ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Only when there is a chance worth mentioning: a "0%" on every dry
            // hour is a screen of noise carrying no information.
            hour.precipProbabilityPercent?.takeIf { it > 0 }?.let {
                Text(
                    text = "${it.roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // What it will actually be like — the half of an hourly forecast a
            // temperature cannot tell you, and NWS sends it per hour.
            hour.shortForecast?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One day: its name, the high beside the quieter low, and NWS's summary. */
@Composable
private fun CombinedDayTile(day: DayForecast, modifier: Modifier = Modifier) {
    TileShell(borderColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = day.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = day.highF?.let { "${it.roundToInt()}°" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = day.lowF?.let { "${it.roundToInt()}°" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.shortForecast?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One of NWS's native half-day periods, for the user who wants day and night
 * kept apart rather than folded into a high and a low. */
@Composable
private fun HalfDayTile(period: DailyPeriod, modifier: Modifier = Modifier) {
    TileShell(borderColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = period.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = period.temperatureF?.let { "${it.roundToInt()}°" } ?: "—",
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
            period.shortForecast?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
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
