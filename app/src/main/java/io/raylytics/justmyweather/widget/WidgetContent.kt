package io.raylytics.justmyweather.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.raylytics.justmyweather.MainActivity
import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.SunTimes
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.ui.home.DayForecast
import io.raylytics.justmyweather.ui.home.ObservationAge
import io.raylytics.justmyweather.ui.home.combineDays
import io.raylytics.justmyweather.ui.home.forecastDays
import io.raylytics.justmyweather.ui.home.monthDayFormat
import io.raylytics.justmyweather.ui.home.periods
import io.raylytics.justmyweather.ui.home.shortDateFormat
import io.raylytics.justmyweather.ui.home.tileHourFormat
import io.raylytics.justmyweather.ui.home.timeFormat
import io.raylytics.justmyweather.ui.home.weekdayFormat
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Details
import io.raylytics.justmyweather.view.ForecastData
import io.raylytics.justmyweather.view.ForecastElement
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ForecastTileLayout
import io.raylytics.justmyweather.view.ModuleContent
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.ModuleValue
import io.raylytics.justmyweather.view.SunDays
import io.raylytics.justmyweather.view.TimesIn
import io.raylytics.justmyweather.view.WeatherField
import io.raylytics.justmyweather.view.degrees
import io.raylytics.justmyweather.view.field
import io.raylytics.justmyweather.view.render
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The widget's drawing: one module of the glance, in Glance's vocabulary.
 *
 * Everything the glance decides, this reuses — `ViewConfig.render` turns the
 * stored weather into the same ModuleContent the glance draws, the forecast's
 * day grouping is the glance's, the formats are the glance's. Only the
 * pixels are new, because a widget is RemoteViews: no custom layouts, no
 * measured text, no gestures. So a value's size is estimated (TextFit) where
 * the glance measures it, the forecast's columns are counted from the
 * widget's width where the glance packs a lattice, and a tap anywhere opens
 * the app where the glance opens a detail sheet.
 */

/** A widget this wide is the glance's full width: the value speaks for
 * itself and a default label is dropped, as on the glance. Four launcher
 * cells on most phones. */
private val HERO_WIDTH = 250.dp

/** A reading this tall has room for the "Observed … · 12 min ago" line
 * under its value — the glance's provenance, which a number on the home
 * screen needs at least as much as one in the app. */
private val FOOTER_MIN_HEIGHT = 110.dp

/** Below this the sun module cannot hold a table and draws today's pair. */
private val SUN_TABLE_MIN_HEIGHT = 100.dp

/** The glance's lattice cell, at the widget: an hour tile is one across, a
 * day tile two. Sized so "7pm 9/6" over "72°" over "5% Cloudy" reads. */
private val HOUR_CELL_WIDTH = 64.dp

/** One quiet element line under a forecast temperature. */
private val ELEMENT_LINE_HEIGHT = 13.dp

private val VALUE_FLOOR_SP = 14f
private val LABEL_SP = 12.sp
private val FOOTER_SP = 11.sp
private val HOUR_SP = 11.sp
private val TEMP_SP = 20.sp
private val LOW_SP = 15.sp
private val ELEMENT_SP = 10.sp
private val BOTTOM_SP = 11.sp
private val CORNER = 16.dp

/**
 * The whole widget: the chosen module projected through its config, in its
 * palette, at the size the launcher gave it. [now] is the worker's or the
 * draw's clock, for the ages and the sun's "today".
 */
@Composable
internal fun WidgetContent(config: WidgetConfig, data: WidgetData?, now: Instant) {
    val palette = WidgetPalette.of(config.theme)
    val spec = WidgetDensitySpec.of(config.view.density)
    val open = actionStartActivity<MainActivity>()
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(palette.background)
                .cornerRadius(CORNER)
                .clickable(open)
                .padding(spec.padding),
    ) {
        val inner = LocalSize.current.inset(spec.padding)
        val value = data?.let { config.moduleValue(it, now) }
        when {
            value == null -> Placeholder(config.setting.label, text = "…", palette)
            data.snapshot == null && data.error != null && config.module.field != null ->
                Placeholder(config.setting.label, text = data.error, palette)
            else ->
                when (val content = value.content) {
                    is ModuleContent.Reading ->
                        ReadingWidget(
                            value = value,
                            text = content.text,
                            snapshot = data.snapshot,
                            observedZone = config.observedZone(data),
                            now = now,
                            palette = palette,
                            spec = spec,
                            size = inner,
                        )
                    is ModuleContent.Sun ->
                        SunWidget(value, content, palette, spec, inner)
                    is ModuleContent.Forecast ->
                        ForecastWidget(value, content, palette, spec, inner, open)
                }
        }
    }
}

/**
 * The glance's own projection, for one module: the stored weather through
 * this widget's config. A missing reading renders as an empty snapshot so
 * the sun and the forecast — which need no reading — still draw, and a
 * reading module shows its dash.
 */
private fun WidgetConfig.moduleValue(data: WidgetData, now: Instant): ModuleValue? {
    val snapshot = data.snapshot ?: emptySnapshot(data)
    val placeZone = snapshot.zone ?: ZoneId.systemDefault()
    val displayZone =
        when (view.timesIn) {
            TimesIn.DEVICE -> ZoneId.systemDefault()
            TimesIn.PLACE -> placeZone
        }
    // Computed to the horizon, like the glance: the setting trims.
    val today = now.atZone(placeZone).toLocalDate()
    val sunDays = SunTimes.daysFrom(data.location.latitude, data.location.longitude, today, placeZone, SunDays.MAX)
    val forecast =
        ForecastData(
            mode = view.defaultForecastMode,
            hours = data.hours,
            periods = data.periods,
            error = data.error,
            placeZone = placeZone,
            extended = data.extended,
        )
    return view.render(snapshot, sunDays, displayZone, forecast).modules.firstOrNull()
}

private fun emptySnapshot(data: WidgetData) =
    WeatherSnapshot(
        locationLabel = data.location.label,
        temperatureF = null,
        conditions = null,
        windMph = null,
        precipitationIn = null,
        pressureInHg = null,
        observedAt = null,
    )

/** The clock the "Observed" time reads in — the user's choice, applied the
 * way the glance applies it. */
private fun WidgetConfig.observedZone(data: WidgetData): ZoneId {
    val snapshot = data.snapshot ?: return ZoneId.systemDefault()
    return view.timesIn.observedZone(snapshot, place = snapshot.zone ?: ZoneId.systemDefault())
}

private fun DpSize.inset(padding: Dp): DpSize =
    DpSize((width - padding * 2).coerceAtLeast(0.dp), (height - padding * 2).coerceAtLeast(0.dp))

/** The label over its content, muted, one line. */
@Composable
private fun Label(text: String, palette: WidgetPalette) {
    Text(
        text = text,
        style = TextStyle(color = palette.muted, fontSize = LABEL_SP, fontFamily = palette.font),
        maxLines = 1,
    )
}

/** Before the first fetch lands, or when it failed with nothing to show. */
@Composable
private fun Placeholder(label: String, text: String, palette: WidgetPalette) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Label(label, palette)
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style =
                    TextStyle(
                        color = palette.muted,
                        fontSize = LABEL_SP,
                        fontFamily = palette.font,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 3,
            )
        }
    }
}

/**
 * A station reading: its label, the value grown to the room it has, and
 * when there is room for it, the glance's provenance line underneath.
 */
@Composable
private fun ReadingWidget(
    value: ModuleValue,
    text: String,
    snapshot: WeatherSnapshot?,
    observedZone: ZoneId,
    now: Instant,
    palette: WidgetPalette,
    spec: WidgetDensitySpec,
    size: DpSize,
) {
    // The glance's rule: a full-width tile drops its default label; a name
    // the user chose is always shown.
    val showLabel = size.width < HERO_WIDTH || value.label != value.module.defaultLabel
    val showFooter = size.height >= FOOTER_MIN_HEIGHT
    val labelHeight = if (showLabel) 18.dp else 0.dp
    val footerHeight = if (showFooter) 16.dp else 0.dp
    // Prose wraps; a number never does. Conditions is the one phrase.
    val maxLines = if (value.module.field == WeatherField.CONDITIONS) 3 else 1
    val fitted =
        TextFit.sp(
            text = text,
            widthDp = size.width.value,
            heightDp = (size.height - labelHeight - footerHeight).value,
            ceilingSp = spec.valueCeilingSp,
            floorSp = VALUE_FLOOR_SP,
            maxLines = maxLines,
        )
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (showLabel) Label(value.label, palette)
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style =
                    TextStyle(
                        color = palette.ink,
                        fontSize = fitted.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = palette.font,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = maxLines,
            )
        }
        if (showFooter) {
            Text(
                text = snapshot?.let { observedLine(it, observedZone, now, wide = size.width >= HERO_WIDTH) } ?: "",
                style = TextStyle(color = palette.muted, fontSize = FOOTER_SP, fontFamily = palette.font),
                maxLines = 1,
            )
        }
    }
}

/**
 * "Observed 12:40 PM · 12 min ago", as the glance words it, or just the
 * age where there is no room for the clock time. The age is what carries
 * staleness on a surface that cannot say "refreshing".
 */
private fun observedLine(snapshot: WeatherSnapshot, zone: ZoneId, now: Instant, wide: Boolean): String {
    val observedAt = snapshot.observedAt ?: return "Observed"
    val time = observedAt.atZone(zone).format(timeFormat)
    val age = ObservationAge.label(observedAt, now)
    return when {
        age == null -> "Observed $time"
        wide -> "Observed $time · $age"
        else -> "Observed $age"
    }
}

/**
 * Sunrise and sunset. Tall enough, the glance's table — a row per day, the
 * user's count of them, scrolling past what fits; shorter, today's pair.
 */
@Composable
private fun SunWidget(
    value: ModuleValue,
    content: ModuleContent.Sun,
    palette: WidgetPalette,
    spec: WidgetDensitySpec,
    size: DpSize,
) {
    val custom = value.label != value.module.defaultLabel
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (custom) Label(value.label, palette)
        val today = content.days.firstOrNull()
        if (size.height < SUN_TABLE_MIN_HEIGHT || today == null) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SunPair("Sunrise", today?.sunrise, content.zone, palette.ink, palette, GlanceModifier.defaultWeight())
                SunPair("Sunset", today?.sunset, content.zone, palette.muted, palette, GlanceModifier.defaultWeight())
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = spec.gap)) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                SunHeading("Sunrise", palette)
                SunHeading("Sunset", palette)
            }
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(content.days) { day -> SunRow(day, content.zone, palette, spec) }
            }
        }
    }
}

private val SUN_TIME_WIDTH = 78.dp

@Composable
private fun SunHeading(text: String, palette: WidgetPalette) {
    Text(
        text = text,
        style =
            TextStyle(
                color = palette.muted,
                fontSize = ELEMENT_SP,
                fontFamily = palette.font,
                textAlign = TextAlign.End,
            ),
        maxLines = 1,
        modifier = GlanceModifier.width(SUN_TIME_WIDTH),
    )
}

@Composable
private fun SunRow(day: SunDay, zone: ZoneId, palette: WidgetPalette, spec: WidgetDensitySpec) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = spec.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = day.date.format(weekdayFormat).uppercase(Locale.getDefault()),
                style =
                    TextStyle(
                        color = palette.ink,
                        fontSize = HOUR_SP,
                        fontWeight = FontWeight.Medium,
                        fontFamily = palette.font,
                    ),
                maxLines = 1,
            )
            Text(
                text = day.date.format(monthDayFormat),
                style = TextStyle(color = palette.muted, fontSize = ELEMENT_SP, fontFamily = palette.font),
                maxLines = 1,
            )
        }
        SunTime(day.sunrise, zone, palette.ink, palette)
        SunTime(day.sunset, zone, palette.muted, palette)
    }
}

@Composable
private fun SunTime(event: Instant?, zone: ZoneId, color: androidx.glance.unit.ColorProvider, palette: WidgetPalette) {
    Text(
        text = event?.atZone(zone)?.format(timeFormat) ?: "—",
        style = TextStyle(color = color, fontSize = TEMP_SP, fontFamily = palette.font, textAlign = TextAlign.End),
        maxLines = 1,
        modifier = GlanceModifier.width(SUN_TIME_WIDTH),
    )
}

/** "Sunrise" over its time: the compact form, each word kept so it survives
 * being read aloud. */
@Composable
private fun SunPair(
    word: String,
    event: Instant?,
    zone: ZoneId,
    color: androidx.glance.unit.ColorProvider,
    palette: WidgetPalette,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = word,
            style = TextStyle(color = palette.muted, fontSize = ELEMENT_SP, fontFamily = palette.font),
            maxLines = 1,
        )
        Text(
            text = event?.atZone(zone)?.format(timeFormat) ?: "—",
            style = TextStyle(color = color, fontSize = TEMP_SP, fontFamily = palette.font),
            maxLines = 1,
        )
    }
}

/** One tile of the day-and-night style: an NWS half-day period, or a whole
 * extended day past NWS's reach. The glance's own split. */
private sealed interface DailyTile {
    data class Period(val period: DailyPeriod) : DailyTile

    data class Day(val day: DayForecast) : DailyTile
}

/**
 * The forecast: its label and framing on one line, then rows of hour or
 * day tiles, as many across as the widget's width holds, scrolling down
 * through the user's horizon. The framing is the config's; a widget has no
 * toggle, it has a configure screen.
 */
@Composable
private fun ForecastWidget(
    value: ModuleValue,
    content: ModuleContent.Forecast,
    palette: WidgetPalette,
    spec: WidgetDensitySpec,
    size: DpSize,
    open: Action,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = spec.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value.label,
                style = TextStyle(color = palette.muted, fontSize = LABEL_SP, fontFamily = palette.font),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = content.mode.label,
                style =
                    TextStyle(
                        color = palette.accent,
                        fontSize = LABEL_SP,
                        fontWeight = FontWeight.Bold,
                        fontFamily = palette.font,
                    ),
                maxLines = 1,
            )
        }
        val cells = ((size.width + spec.gap) / (HOUR_CELL_WIDTH + spec.gap)).toInt().coerceAtLeast(1)
        val list = GlanceModifier.fillMaxWidth().defaultWeight()
        when (content.mode) {
            ForecastMode.HOURLY ->
                ForecastFrame(content.hours, content.error, palette) { hours ->
                    val tileHeight = spec.tileHeight + ELEMENT_LINE_HEIGHT * content.elements.hourLines()
                    TileRows(hours.take(content.hourlyHours), cells, spec, open, list) { hour ->
                        HourTile(hour, content, palette, tileHeight)
                    }
                }
            ForecastMode.DAILY ->
                ForecastFrame(content.periods, content.error, palette) { periods ->
                    val days =
                        forecastDays(
                            combineDays(periods, content.hours, content.placeZone),
                            content.extended,
                            content.dailyDays,
                            content.placeZone,
                        )
                    val tileHeight = spec.tileHeight + ELEMENT_LINE_HEIGHT * content.elements.dayLines()
                    val columns = (cells / ModuleSize.CELL.columns / 2).coerceAtLeast(1)
                    when (content.dailyStyle) {
                        DailyStyle.COMBINED ->
                            TileRows(days, columns, spec, open, list) { day ->
                                DayTile(day, content, palette, tileHeight)
                            }
                        DailyStyle.HALF_DAY -> {
                            val tiles =
                                days.filter { it.extended == null }.periods.map(DailyTile::Period) +
                                    days.filter { it.extended != null }.map(DailyTile::Day)
                            TileRows(tiles, columns, spec, open, list) { tile ->
                                when (tile) {
                                    is DailyTile.Period -> HalfDayTile(tile.period, content, palette, tileHeight)
                                    is DailyTile.Day -> DayTile(tile.day, content, palette, tileHeight)
                                }
                            }
                        }
                    }
                }
        }
    }
}

/** How many quiet lines an hour tile adds under its temperature. */
private fun Set<ForecastElement>.hourLines(): Int =
    listOf(ForecastElement.WIND, ForecastElement.HUMIDITY, ForecastElement.DEW_POINT).count { it in this }

/** The same for a day tile, which only ever carries wind. */
private fun Set<ForecastElement>.dayLines(): Int = if (ForecastElement.WIND in this) 1 else 0

/** The glance's load/error/empty framing, in the widget's words. */
@Composable
private fun <T> ForecastFrame(
    items: List<T>?,
    error: String?,
    palette: WidgetPalette,
    content: @Composable (List<T>) -> Unit,
) {
    val quiet = TextStyle(color = palette.muted, fontSize = LABEL_SP, fontFamily = palette.font)
    when {
        !items.isNullOrEmpty() -> content(items)
        error != null -> Text(text = error, style = quiet, maxLines = 3)
        items != null -> Text(text = "No forecast available.", style = quiet, maxLines = 1)
        else -> Text(text = "…", style = quiet, maxLines = 1)
    }
}

/**
 * Tiles in rows of [columns], the rows in a list that scrolls. A short last
 * row is padded with empty weight so its tiles keep the width of the others
 * — the glance's flow grid does the same. Each row opens the app on tap: a
 * list draws over the widget's own click, so the rows carry it themselves.
 */
@Composable
private fun <T> TileRows(
    items: List<T>,
    columns: Int,
    spec: WidgetDensitySpec,
    open: Action,
    /** The list's place in the forecast column — the weight that gives it
     * the height under the header, which only the column's scope can grant. */
    modifier: GlanceModifier,
    tile: @Composable (T) -> Unit,
) {
    val rows = items.chunked(columns)
    LazyColumn(modifier = modifier) {
        items(rows) { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = spec.gap).clickable(open)) {
                row.forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(spec.gap))
                    Box(modifier = GlanceModifier.defaultWeight()) { tile(item) }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = GlanceModifier.width(spec.gap))
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/** One hour: when, how warm, and whatever the user has switched on. */
@Composable
private fun HourTile(hour: ForecastPoint, content: ModuleContent.Forecast, palette: WidgetPalette, height: Dp) {
    val at = hour.startTime.atZone(content.zone)
    ZonedTile(
        top = "${at.format(tileHourFormat).lowercase(Locale.getDefault())} ${at.format(shortDateFormat)}",
        topItalic = false,
        chance = hour.precipProbabilityPercent.takeIf { ForecastElement.PRECIP_CHANCE in content.elements },
        words = hour.shortForecast.takeIf { ForecastElement.CONDITIONS in content.elements },
        bottomLines = 3,
        layout = content.layout,
        palette = palette,
        height = height,
        middle = { TempText(hour.temperatureF.degrees(), palette.ink, palette) },
        below = {
            ElementLines(
                content.elements,
                palette,
                windMph = hour.windMph,
                windDirection = hour.windDirection,
                humidity = hour.relativeHumidityPercent,
                dewpointF = hour.dewpointF,
            )
        },
    )
}

/** One day: its name, the high beside the quieter low, and NWS's summary. */
@Composable
private fun DayTile(day: DayForecast, content: ModuleContent.Forecast, palette: WidgetPalette, height: Dp) {
    ZonedTile(
        top = day.name,
        // Italic marks an extended day, as on the glance: past NWS's reach.
        topItalic = day.extended != null,
        chance = day.precipChance.takeIf { ForecastElement.PRECIP_CHANCE in content.elements },
        words = day.shortForecast.takeIf { ForecastElement.CONDITIONS in content.elements },
        bottomLines = 2,
        layout = content.layout,
        palette = palette,
        height = height,
        middle = {
            Row(verticalAlignment = Alignment.Bottom) {
                TempText(day.highF.degrees(), palette.ink, palette)
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = day.lowF.degrees(),
                    style = TextStyle(color = palette.muted, fontSize = LOW_SP, fontFamily = palette.font),
                    maxLines = 1,
                )
            }
        },
        below = { ElementLines(content.elements, palette, windMph = day.windMph, windDirection = day.windDirection) },
    )
}

/** One of NWS's native half-day periods: day in ink, night in the quieter
 * tone, so which is which survives being read out of order. */
@Composable
private fun HalfDayTile(period: DailyPeriod, content: ModuleContent.Forecast, palette: WidgetPalette, height: Dp) {
    ZonedTile(
        top = period.name,
        topItalic = false,
        chance = period.precipProbabilityPercent.takeIf { ForecastElement.PRECIP_CHANCE in content.elements },
        words = period.shortForecast.takeIf { ForecastElement.CONDITIONS in content.elements },
        bottomLines = 2,
        layout = content.layout,
        palette = palette,
        height = height,
        middle = {
            TempText(period.temperatureF.degrees(), if (period.isDaytime) palette.ink else palette.muted, palette)
        },
        below = {
            ElementLines(content.elements, palette, windMph = period.windMph, windDirection = period.windDirection)
        },
    )
}

@Composable
private fun TempText(text: String, color: androidx.glance.unit.ColorProvider, palette: WidgetPalette) {
    Text(
        text = text,
        style = TextStyle(color = color, fontSize = TEMP_SP, fontFamily = palette.font),
        maxLines = 1,
    )
}

/**
 * The glance's three zones. SPREAD pins the time to the top and the words
 * to the bottom with the temperature between; STACKED keeps them together
 * in the middle. A fixed height, because a list row has no row-mates to be
 * sized against: every tile in a framing is the same height, which is what
 * keeps the temperatures level across a row.
 */
@Composable
private fun ZonedTile(
    top: String,
    topItalic: Boolean,
    chance: Double?,
    words: String?,
    bottomLines: Int,
    layout: ForecastTileLayout,
    palette: WidgetPalette,
    height: Dp,
    middle: @Composable () -> Unit,
    below: @Composable () -> Unit,
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = if (layout == ForecastTileLayout.STACKED) Alignment.CenterVertically else Alignment.Top,
    ) {
        Text(
            text = top,
            style =
                TextStyle(
                    color = palette.muted,
                    fontSize = HOUR_SP,
                    fontStyle = if (topItalic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = palette.font,
                ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        middle()
        below()
        if (layout == ForecastTileLayout.SPREAD) Spacer(modifier = GlanceModifier.defaultWeight())
        BottomLine(chance, words, bottomLines, palette)
    }
}

/**
 * The chance of rain, in the accent, then the conditions — the glance's
 * bottom line. Two texts in a row rather than one styled string, which
 * RemoteViews cannot carry; the words take what width the chance leaves.
 */
@Composable
private fun BottomLine(chance: Double?, words: String?, maxLines: Int, palette: WidgetPalette) {
    val shown = chance?.takeIf { it > 0 }
    if (shown == null && words == null) return
    Row(verticalAlignment = Alignment.Top) {
        shown?.let {
            Text(
                text = "${it.roundToInt()}%",
                style = TextStyle(color = palette.accent, fontSize = BOTTOM_SP, fontFamily = palette.font),
                maxLines = 1,
            )
            if (words != null) Spacer(modifier = GlanceModifier.width(3.dp))
        }
        words?.let {
            Text(
                text = it,
                style = TextStyle(color = palette.muted, fontSize = BOTTOM_SP, fontFamily = palette.font),
                maxLines = maxLines,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** The user's other elements, one quiet line each, only with a value. */
@Composable
private fun ElementLines(
    elements: Set<ForecastElement>,
    palette: WidgetPalette,
    windMph: Double? = null,
    windDirection: String? = null,
    humidity: Double? = null,
    dewpointF: Double? = null,
) {
    if (ForecastElement.WIND in elements && windMph != null) QuietLine(Details.wind(windMph, windDirection), palette)
    if (ForecastElement.HUMIDITY in elements && humidity != null) QuietLine("RH ${humidity.roundToInt()}%", palette)
    if (ForecastElement.DEW_POINT in elements && dewpointF != null) {
        QuietLine("Dew ${dewpointF.roundToInt()}°", palette)
    }
}

@Composable
private fun QuietLine(text: String, palette: WidgetPalette) {
    Text(
        text = text,
        style = TextStyle(color = palette.muted, fontSize = ELEMENT_SP, fontFamily = palette.font),
        maxLines = 1,
    )
}
