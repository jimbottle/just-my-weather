package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleSpan
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The screen's second grid: the forecast, drawn as tiles on the same engine as
 * the glance. It is the counterpart to ModuleGrid — same tile language, no
 * arranging, because its contents come from NWS rather than from the user.
 *
 * The Hourly/Daily choice lives HERE, on the forecast, rather than as a
 * screen-wide mode. That is the whole shape of this file: the forecast is one
 * thing on the page with its own option, instead of the page having three
 * states of which two happen to be forecasts.
 */

/**
 * How many hours the Hourly framing shows. Twelve is three rows of four — a
 * useful look ahead that still fits the calm the app is built around, where
 * NWS's full ~156 points would be thirty-nine rows of tiles.
 *
 * No date labels come with it, deliberately: within twelve hours the times are
 * unambiguous on their own ("11 pm" then "1 am" reads as tonight), and headings
 * between rows would break the one thing a grid promises — that its rows are
 * the same kind of thing all the way down.
 */
private const val HOURLY_TILES = 12

/** Hours are terse enough for a quarter tile; a period's name ("Monday Night",
 * "This Afternoon") needs half a row to survive without ellipsis. */
private val HOUR_SPAN = ModuleSpan.QUARTER
private val DAY_SPAN = ModuleSpan.HALF

/**
 * The forecast grid and its own framing toggle.
 *
 * Renders nothing at all when the user has turned the forecast off — the calm
 * minimum is the glance by itself, and an empty heading would be worse than
 * absence.
 */
@Composable
internal fun ForecastGrid(
    mode: ForecastMode,
    onSetMode: (ForecastMode) -> Unit,
    dailyStyle: DailyStyle,
    hours: List<ForecastPoint>?,
    periods: List<DailyPeriod>?,
    error: String?,
    gap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = GRID_MAX_WIDTH).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ForecastHeader(mode = mode, onSetMode = onSetMode)
        when (mode) {
            ForecastMode.HOURLY ->
                ForecastFrame(items = hours, error = error) { list ->
                    TileGrid(
                        items = list.take(HOURLY_TILES),
                        span = { HOUR_SPAN },
                        gap = gap,
                        modifier = Modifier.fillMaxWidth(),
                    ) { hour, _, tileModifier -> HourTile(hour, tileModifier) }
                }

            ForecastMode.DAILY ->
                ForecastFrame(items = periods, error = error) { list ->
                    // Pairing the half-day periods is pure; cache per list.
                    val days =
                        remember(list, dailyStyle) {
                            if (dailyStyle == DailyStyle.COMBINED) combineDays(list) else emptyList()
                        }
                    when (dailyStyle) {
                        DailyStyle.COMBINED ->
                            TileGrid(
                                items = days,
                                span = { DAY_SPAN },
                                gap = gap,
                                modifier = Modifier.fillMaxWidth(),
                            ) { day, _, tileModifier -> CombinedDayTile(day, tileModifier) }

                        DailyStyle.HALF_DAY ->
                            TileGrid(
                                items = list,
                                span = { DAY_SPAN },
                                gap = gap,
                                modifier = Modifier.fillMaxWidth(),
                            ) { period, _, tileModifier -> HalfDayTile(period, tileModifier) }
                    }
                }
        }
    }
}

/**
 * "Forecast" with its framing chips on the same line — the arrangement that
 * makes the toggle read as an option belonging to this grid rather than a
 * control for the whole screen, which is what it was when it sat alone at the
 * bottom offering "Now" as a third state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastHeader(
    mode: ForecastMode,
    onSetMode: (ForecastMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The counterpart to the glance's "Observed" line: everything below
        // this is model output for a grid cell, not a station's measurement,
        // and the two legitimately disagree.
        Text(
            text = "Forecast",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // FlowRow so a large display scale wraps the chips under the label
        // instead of squeezing them off the edge.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ForecastMode.entries.forEach { entry ->
                FilterChip(
                    selected = entry == mode,
                    onClick = { onSetMode(entry) },
                    label = { Text(entry.label) },
                    modifier = Modifier.testTag("forecast_${entry.key}"),
                )
            }
        }
    }
}

/** One hour: when, how likely rain is, how warm. */
@Composable
private fun HourTile(hour: ForecastPoint, modifier: Modifier = Modifier) {
    TileShell(borderColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text =
                    hour.startTime
                        .atZone(ZoneId.systemDefault())
                        .format(hourFormat)
                        .lowercase(Locale.getDefault()),
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
            // hour is twelve tiles of noise for no information.
            hour.precipProbabilityPercent?.takeIf { it > 0 }?.let {
                Text(
                    text = "${it.roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
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
