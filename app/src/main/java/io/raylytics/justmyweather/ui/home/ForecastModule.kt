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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Detail
import io.raylytics.justmyweather.view.Details
import io.raylytics.justmyweather.view.ForecastElement
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ForecastTileLayout
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
                        ) { hour, index, tileModifier ->
                            HourTile(
                                hour = hour,
                                zone = content.zone,
                                elements = content.elements,
                                layout = content.layout,
                                modifier =
                                    tileModifier
                                        .opens(open) { Details.ofHour(hour, content.zone) }
                                        // A stable handle per tile for the
                                        // layout test, which asserts zones
                                        // in real dp against the tile's box.
                                        .testTag("hour_$index"),
                            )
                        }
                    }
                }

            ForecastMode.DAILY ->
                ForecastFrame(items = content.periods, error = content.error) { list ->
                    // Pairing the half-day periods is pure; cache per list.
                    val days =
                        remember(list, content.hours, content.dailyStyle) {
                            if (content.dailyStyle == DailyStyle.COMBINED) {
                                combineDays(list, content.hours, content.placeZone)
                            } else {
                                emptyList()
                            }
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
                                    CombinedDayTile(
                                        day = day,
                                        elements = content.elements,
                                        layout = content.layout,
                                        modifier = tileModifier.opens(open) { day.detail() },
                                    )
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
                                        layout = content.layout,
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
    layout: ForecastTileLayout,
    modifier: Modifier = Modifier,
) {
    val at = hour.startTime.atZone(zone)
    ZonedTile(
        top = "${at.format(hourFormat).lowercase(Locale.getDefault())} ${at.format(shortDateFormat)}",
        bottom = bottomLine(elements, hour.shortForecast, hour.precipProbabilityPercent),
        layout = layout,
        modifier = modifier,
        below = {
            ElementLines(
                elements = elements,
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
    layout: ForecastTileLayout,
    modifier: Modifier = Modifier,
) {
    ZonedTile(
        top = day.name,
        layout = layout,
        bottom =
            bottomLine(
                elements,
                day.shortForecast,
                // Either half's chance: the day's rain is the day's rain.
                listOfNotNull(day.day?.precipProbabilityPercent, day.night?.precipProbabilityPercent).maxOrNull(),
            ),
        modifier = modifier,
        below = {
            ElementLines(
                elements = elements,
                windMph = (day.day ?: day.night)?.windMph,
                windDirection = (day.day ?: day.night)?.windDirection,
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
    layout: ForecastTileLayout,
    modifier: Modifier = Modifier,
) {
    ZonedTile(
        top = period.name,
        bottom = bottomLine(elements, period.shortForecast, period.precipProbabilityPercent),
        layout = layout,
        modifier = modifier,
        below = {
            ElementLines(
                elements = elements,
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
 * the temperature and does not move it: the layout reserves the same room
 * above the temperature as [below] takes under it, so a tile with a chance
 * of rain grows symmetrically and its number stays level with a
 * neighbour's that has none.
 *
 * An explicit Layout rather than a Column with weighted halves. The row is
 * sized to its tallest tile's intrinsic height, and what a weighted child
 * contributes to that is subtle enough that the first attempt reserved
 * nothing for the lower half on the Pixel 9 — "1%" landed on top of the
 * conditions. Here the intrinsic IS the arithmetic below: top + middle +
 * twice below + bottom.
 */
@Composable
private fun ZonedTile(
    top: String,
    bottom: AnnotatedString?,
    layout: ForecastTileLayout,
    modifier: Modifier = Modifier,
    below: @Composable () -> Unit = {},
    middle: @Composable () -> Unit,
) {
    // The bottom zone's reserved height, in pixels: two lines of the label
    // style. Reserved here rather than through the Text's minLines because
    // the row is sized by an INTRINSIC measurement, and in that pass a Text
    // reports its natural height with minLines ignored — so rows whose
    // conditions fit on one line came out a line too short and the zones had
    // no room to spread, while rows with a wrapped "Mostly Sunny" were fine
    // (seen on the Pixel 9: "some rows worse than others").
    val labelStyle = MaterialTheme.typography.labelSmall
    val bottomReserve = with(LocalDensity.current) { (labelStyle.lineHeight * 2).roundToPx() }
    TileShell(borderColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        ZonedLayout(
            spread = layout == ForecastTileLayout.SPREAD,
            bottomReserve = bottomReserve,
            top = {
                Text(
                    text = top,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            middle = middle,
            below = below,
            bottom = {
                bottom?.let {
                    Text(
                        text = it,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}

/**
 * Stacks each slot's children vertically, centred; pins [top] and [bottom]
 * to the edges; centres [middle] between them; hangs [below] directly under
 * [middle]. Wants top + middle + 2·below + bottom of height and, when
 * [spread], takes ALL the height it is offered (the row's tallest tile
 * decides) and spends the surplus equally above and below the middle. Not
 * spread, it takes only what it wants, and the shell centres the block.
 *
 * "All the height it is offered" is read off the constraints' maximum, not
 * asked for with fillMaxSize: the tile shell hands its content loose
 * constraints, and the first version — which reported its content height
 * under them — came out as a stacked block the shell centred, not the three
 * zones (seen on the Pixel 9). In the intrinsic pass the height is
 * unbounded and the content sum is the answer.
 */
@Composable
private fun ZonedLayout(
    spread: Boolean,
    /** The bottom zone is at least this tall whenever it has content, so a
     * one-line "Clear" reserves what a two-line "Mostly Clear" takes and the
     * row's tiles agree on where the middle is. */
    bottomReserve: Int,
    top: @Composable () -> Unit,
    middle: @Composable () -> Unit,
    below: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    Layout(contents = listOf(top, middle, below, bottom)) { slots, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val measured = slots.map { slot -> slot.map { it.measure(loose) } }
        val heights = measured.map { placeables -> placeables.sumOf { it.height } }
        val topH = heights[0]
        val midH = heights[1]
        val belowH = heights[2]
        val botH = if (measured[3].isEmpty()) 0 else heights[3].coerceAtLeast(bottomReserve)
        val required = topH + midH + 2 * belowH + botH
        val width =
            if (constraints.hasBoundedWidth) constraints.maxWidth else measured.flatten().maxOfOrNull { it.width } ?: 0
        val height =
            if (spread && constraints.hasBoundedHeight) {
                constraints.maxHeight.coerceAtLeast(constraints.constrainHeight(required))
            } else {
                constraints.constrainHeight(required)
            }
        layout(width, height) {
            fun stack(placeables: List<Placeable>, from: Int) {
                var y = from
                placeables.forEach { p ->
                    p.placeRelative((width - p.width) / 2, y)
                    y += p.height
                }
            }
            stack(measured[0], 0)
            // The bottom's text sits at the TOP of its reserved zone, so a
            // one-line "Clear" lines up with the first line of a neighbour's
            // "Mostly / Clear" rather than with its second.
            stack(measured[3], height - botH)
            // The middle zone runs from under the top to above the bottom;
            // the temperature's centre is its centre, and below hangs off
            // the temperature's foot.
            val zoneCentre = (topH + (height - botH)) / 2
            val midTop = zoneCentre - midH / 2
            stack(measured[1], midTop)
            stack(measured[2], midTop + midH)
        }
    }
}

/**
 * The bottom zone's line: the conditions and the chance of rain together —
 * "Mostly Sunny · 28%", the chance in its accent — each present only when
 * its element is on and there is a value. The chance rides with the
 * conditions rather than under the temperature because it is about the
 * same thing the words are (Evan: "it is most closely tied to that"), and
 * because a line under the temperature is a line the row has to make room
 * for. A "0%" is left out: on every dry hour it is noise carrying no
 * information.
 *
 * Null only when neither element is ON. When one is on but THIS tile has
 * nothing to say — NWS sent no summary for the hour, or a dry 0% — the line
 * is empty rather than absent, so the tile still reserves the zone: a tile
 * that dropped its bottom zone would be shorter than its row-mates in
 * intrinsic terms, and its temperature would sit lower than theirs. The
 * zone is the setting's; the words are the data's.
 */
@Composable
private fun bottomLine(
    elements: Set<ForecastElement>,
    conditions: String?,
    precipChance: Double?,
): AnnotatedString? {
    val wanted = ForecastElement.CONDITIONS in elements || ForecastElement.PRECIP_CHANCE in elements
    if (!wanted) return null
    val words = conditions?.takeIf { ForecastElement.CONDITIONS in elements }
    val chance = precipChance?.takeIf { ForecastElement.PRECIP_CHANCE in elements && it > 0 }
    val accent = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        words?.let { append(it) }
        chance?.let {
            if (words != null) append(" · ")
            withStyle(SpanStyle(color = accent)) { append("${it.roundToInt()}%") }
        }
    }
}

/**
 * The user's other elements, one quiet line each under the temperature, and
 * only when there is a value: a "—" for a humidity the day tiles never
 * carry is a screen of noise carrying no information.
 */
@Composable
private fun ElementLines(
    elements: Set<ForecastElement>,
    windMph: Double? = null,
    windDirection: String? = null,
    humidity: Double? = null,
    dewpointF: Double? = null,
) {
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
