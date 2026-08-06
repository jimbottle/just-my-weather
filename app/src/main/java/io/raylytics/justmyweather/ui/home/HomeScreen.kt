package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.AlertBannerPosition
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.DisplayValue
import io.raylytics.justmyweather.view.ForecastLayout
import io.raylytics.justmyweather.view.RenderedView
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewMode
import io.raylytics.justmyweather.view.render
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How much of a label/value row the label may occupy before it ellipsizes.
 *
 * Sized so the longest built-in label still fits ("Precip (last hr)" is about
 * 46% of the narrowest block) while a runaway custom label cannot crowd the
 * value out. Everything the label does not use goes to the value.
 */
private const val LABEL_WIDTH_SHARE = 0.6f

/**
 * The home view. Out of the box it's a calm single glance; once the user edits
 * their config it's whatever they made it — same screen, driven by data. The
 * first visible field is the hero (large), the rest are compact rows. Centred
 * in whitespace so the hero is readable in well under a second.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onSetMode: (ViewMode) -> Unit,
    onCustomize: () -> Unit,
    onAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is HomeUiState.Loading ->
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                is HomeUiState.Error ->
                    ErrorView(message = state.message, onRefresh = onRefresh)

                is HomeUiState.Ready ->
                    GlanceView(
                        state = state,
                        onRefresh = onRefresh,
                        onSetMode = onSetMode,
                        onCustomize = onCustomize,
                        onAlerts = onAlerts,
                    )
            }
        }
    }
}

@Composable
private fun GlanceView(
    state: HomeUiState.Ready,
    onRefresh: () -> Unit,
    onSetMode: (ViewMode) -> Unit,
    onCustomize: () -> Unit,
    onAlerts: () -> Unit,
) {
    val snapshot = state.snapshot
    val config = state.config
    // Density drives the sizes/spacing; the chosen level lives in the config.
    val spec = config.density.spec()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spec.sectionSpacing),
        // Scroll fallback: on short viewports (landscape, split-screen) a tall
        // framing — the stacked daily list especially — must degrade to
        // scrolling, never to clipped, unreachable chips and buttons.
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        // Safety alerts lead when the user wants them on top. Absent entirely
        // on the ordinary day — an empty list renders nothing, so the calm
        // default stays calm and the banner's presence is itself the signal.
        if (config.alertBannerPosition == AlertBannerPosition.TOP) {
            SafetyAlertBanner(alerts = state.safetyAlerts)
        }

        Text(
            text = snapshot.locationLabel.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The Now glance always leads — the framing below adds to it rather
        // than replacing it, so the screen answers "right now" AND "ahead".
        NowContent(snapshot = snapshot, config = config)

        // The counterpart to "Observed" above: everything below this line is
        // model output, not measurement. Only rendered when a forecast framing
        // is actually on screen — in NOW mode there is nothing to label.
        if (state.mode != ViewMode.NOW) {
            Text(
                text = "Forecast",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Extra space ABOVE only, so the label hugs the tiles it heads
                // instead of floating midway between them and the "Observed"
                // line. Sitting equidistant, it read as ambiguous which block
                // it belonged to — which is the whole failure being fixed here.
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        when (state.mode) {
            ViewMode.NOW -> Unit // Just the glance: the calm minimum.
            ViewMode.HOURLY ->
                HourlyContent(
                    hours = state.hourly,
                    error = state.forecastError,
                    layout = config.hourlyLayout,
                )
            ViewMode.DAILY ->
                DailyContent(
                    periods = state.daily,
                    error = state.forecastError,
                    style = config.dailyStyle,
                    layout = config.dailyLayout,
                )
        }

        // The old "Updated HH:MM" line lived here, below the forecast and
        // density-gated. It is gone rather than duplicated: the same fact now
        // sits with the hero as "Observed HH:MM", where it explains the number
        // it belongs to instead of trailing the whole screen.

        ModeToggle(selected = state.mode, onSelect = onSetMode)

        // A refresh that failed with a reading already on screen. It sits with
        // the Refresh button — the control it is about — and in the same quiet
        // style as a failed forecast fetch, because the glance above is still
        // good: only its age is in question, and "Observed" already says that.
        state.refreshError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The button label reflects the refreshing flag, so a re-fetch is an
            // observable state change (and the flag stops being dead state).
            TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                Text(if (state.refreshing) "Refreshing…" else "Refresh")
            }
            TextButton(onClick = onCustomize) { Text("Customize") }
            TextButton(onClick = onAlerts) { Text("Alerts") }
        }

        if (config.alertBannerPosition == AlertBannerPosition.BOTTOM) {
            SafetyAlertBanner(alerts = state.safetyAlerts)
        }
    }
}

/**
 * The label · value rows beneath the hero.
 *
 * Extracted and `internal` so the width rule below can be asserted directly:
 * it has now regressed twice (label starvation, then a hard 50/50 cap) with
 * nothing automated to catch either, because Maestro sees text but not
 * measured widths.
 *
 * The cap comes from [BoxWithConstraints.maxWidth] — the width this block was
 * ACTUALLY given — not from [blockMaxWidth], which is only the density's
 * nominal ceiling. The row is `min(screen - padding, blockMaxWidth)` wide, so
 * on a narrow screen or at a large display scale a cap computed from the
 * constant would exceed 60% of the real row; since the label is un-weighted it
 * would win that space and starve the value it is meant to protect.
 */
@Composable
internal fun FieldRows(
    rows: List<DisplayValue>,
    blockMaxWidth: Dp,
    rowSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.widthIn(max = blockMaxWidth)) {
        val labelCap = maxWidth * LABEL_WIDTH_SHARE
        Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
            rows.forEach { row ->
                // CAP the label, then give the value everything left over.
                //
                // Not weight() on both: a weighted child is measured with
                // maxWidth = its share, and `fill = false` only relaxes
                // minWidth — so two equal weights hard-cap EACH side at 50%
                // even when the other is nearly empty. That was worse than the
                // problem it fixed: a long NWS condition got half a row and
                // wrapped further, with the other half blank.
                //
                // Un-weighted children are measured first, so the label —
                // bounded here, and ellipsized at one line — takes only what it
                // needs up to the cap, and the weighted value then receives ALL
                // the remainder. A short label leaves the value nearly the whole
                // row; a runaway custom label (uncapped free text) stops at the
                // cap instead of starving the value. The label gives way rather
                // than the value because the user chose the label and knows what
                // it says.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = labelCap),
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Active safety alerts — tornado, severe storm, hurricane, dangerous heat,
 * poor air quality — worst first.
 *
 * Renders NOTHING when there is nothing to say, which is the point: on almost
 * every day this composable contributes no pixels, so when it does appear the
 * appearance itself carries the message. Which alerts qualify is decided in
 * SafetyAlerts, not here.
 *
 * The most severe alert is expanded; the rest are one line each. A screen full
 * of headlines during a storm is the opposite of useful.
 */
@Composable
private fun SafetyAlertBanner(alerts: List<ActiveAlert>) {
    if (alerts.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("safetyAlertBanner"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            alerts.forEachIndexed { index, alert ->
                Text(
                    text = alert.event,
                    style = MaterialTheme.typography.titleSmall,
                )
                // Only the worst one gets its headline: during severe weather
                // several alerts overlap, and stacking every headline buries
                // the one that matters under the ones that don't.
                if (index == 0 && alert.headline.isNotBlank()) {
                    Text(
                        text = alert.headline,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowContent(
    snapshot: WeatherSnapshot,
    config: ViewConfig,
) {
    val rendered: RenderedView = config.render(snapshot)
    val spec = config.density.spec()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spec.sectionSpacing),
    ) {
        // Hero: the value speaks for itself, so no caption above it.
        Text(
            text = rendered.hero?.value ?: "—",
            style = spec.heroStyle,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        // Secondary fields, in the user's order, as quiet label · value rows.
        if (rendered.rows.isNotEmpty()) {
            FieldRows(
                rows = rendered.rows,
                blockMaxWidth = spec.rowMaxWidth,
                rowSpacing = spec.rowSpacing,
                modifier = Modifier.padding(top = spec.sectionSpacing),
            )
        }

        // Provenance, attached to the reading it describes. The hero is a real
        // thermometer at a nearby station; the forecast strip below is model
        // output for a 2.5km grid cell, and the two legitimately disagree —
        // 75° here against 71° for the same hour is two products, not a bug.
        // Unlabelled and side by side, that reads as the app contradicting
        // itself, so each region now says which it is.
        //
        // Shown at EVERY density, including Spacious. That end used to hide
        // the time to be "just the number", but a number whose provenance is
        // undiscoverable is exactly what made this confusing, and one quiet
        // line is the smallest thing that fixes it.
        Text(
            text = observedLabel(snapshot)?.let { "Observed $it" } ?: "Observed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The mode chips. A horizontally scrolling row rather than FlowRow so future
 * framings extend sideways instead of stacking — the calm column stays calm. */
@Composable
private fun ModeToggle(
    selected: ViewMode,
    onSelect: (ViewMode) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
                modifier = Modifier.testTag("mode_${mode.key}"),
            )
        }
    }
}

/** Shared load/error/empty framing for the forecast views: null items = first
 * fetch still in flight; an error shows in place quietly. Loaded data always
 * wins over an error — a stale message must never cover a strip we can
 * actually draw. */
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
            )
        items != null ->
            // Fetched fine, but NWS had nothing for this framing.
            Text(
                text = "No forecast available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else ->
            Text(
                text = "…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

/** The hourly view: date labels mark each local-midnight boundary so the
 * hours read grouped by day, in the strip or as stacked rows with date
 * section headers. */
@Composable
private fun HourlyContent(
    hours: List<ForecastPoint>?,
    error: String?,
    layout: ForecastLayout,
) {
    ForecastFrame(items = hours, error = error) { list ->
        // Derived state: reshaping ~156 points is pure, so cache per list.
        val groups = remember(list) { groupHoursByDay(list, ZoneId.systemDefault()) }
        when (layout) {
            ForecastLayout.ROW ->
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    groups.forEach { group ->
                        DateTile(group.date)
                        group.hours.forEach { HourTile(it) }
                    }
                }
            ForecastLayout.COLUMN ->
                // Lazy: a week of hours is ~156 rows — an order of magnitude
                // more than any other list here — and only a handful fit the
                // 380dp viewport. The bounded height keeps the nesting legal
                // inside the glance column's scroll fallback.
                LazyColumn(
                    modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "date-${group.date}") {
                            Text(
                                text = "${group.date.format(weekdayFormat)}, ${group.date.format(monthDayFormat)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(group.hours, key = { it.startTime.toEpochMilli() }) { HourRow(it) }
                    }
                }
        }
    }
}

/** One stacked row of the vertical hourly list: time, rain chance, temp. */
@Composable
private fun HourRow(hour: ForecastPoint) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = hour.startTime.atZone(ZoneId.systemDefault()).format(hourFormat).lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            hour.precipProbabilityPercent?.takeIf { it > 0 }?.let {
                Text(
                    text = "${it.roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = hour.temperatureF?.let { "${it.roundToInt()}°" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** The daily view in the user's chosen shape (combined high/low or half-day
 * periods) and direction (strip or stacked). */
@Composable
private fun DailyContent(
    periods: List<DailyPeriod>?,
    error: String?,
    style: DailyStyle,
    layout: ForecastLayout,
) {
    ForecastFrame(items = periods, error = error) { list ->
        // Derived state: pairing the half-day periods is pure, cache per list.
        val days = remember(list, style) { if (style == DailyStyle.COMBINED) combineDays(list) else emptyList() }
        when (layout) {
            ForecastLayout.ROW ->
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (style) {
                        DailyStyle.COMBINED -> days.forEach { CombinedDayTile(it) }
                        DailyStyle.HALF_DAY -> list.forEach { DayTile(it) }
                    }
                }
            ForecastLayout.COLUMN ->
                Column(
                    // The height cap keeps the stacked list from swallowing the
                    // whole glance; its own scroll shows the tail, and the
                    // glance column's scroll fallback covers short viewports.
                    modifier =
                        Modifier
                            .widthIn(max = 320.dp)
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (style) {
                        DailyStyle.COMBINED -> days.forEach { CombinedDayRow(it) }
                        DailyStyle.HALF_DAY -> list.forEach { HalfDayRow(it) }
                    }
                }
        }
    }
}

/** A date marker inside the hourly scroll: weekday over month/day, quietly
 * accented so the day boundaries are findable at a glance. */
@Composable
private fun DateTile(date: java.time.LocalDate) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // onSurfaceVariant, NOT the accent. The accent marks things you can
        // press (Refresh, Customize, the mode chips), and a date marker is a
        // label — in the accent it looked as tappable as the buttons below,
        // the same misread that made "Clear" seem like a control.
        //
        // Not a blanket rule yet: rain-chance percentages still take the
        // accent as deliberate emphasis. That is a live judgement call rather
        // than an oversight — worth revisiting if anyone tries to tap one.
        Text(
            text = date.format(weekdayFormat).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = date.format(monthDayFormat),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CombinedDayTile(day: DayForecast) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(96.dp),
    ) {
        Text(
            text = day.name,
            style = MaterialTheme.typography.labelMedium,
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
        Text(
            text = day.shortForecast ?: " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** One stacked row of the vertical daily list: name + summary on the left,
 * high (bold) and low (quiet) on the right. */
@Composable
private fun CombinedDayRow(day: DayForecast) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            day.shortForecast?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = day.highF?.let { "${it.roundToInt()}°" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = day.lowF?.let { "${it.roundToInt()}°" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HalfDayRow(period: DailyPeriod) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = period.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            period.shortForecast?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = period.temperatureF?.let { "${it.roundToInt()}°" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color =
                if (period.isDaytime) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun HourTile(hour: ForecastPoint) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = hour.startTime.atZone(ZoneId.systemDefault()).format(hourFormat).lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hour.temperatureF?.let { "${it.roundToInt()}°" } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // Rain chance only when it's worth knowing — a dry hour stays blank
        // rather than shouting 0%.
        Text(
            text = hour.precipProbabilityPercent?.takeIf { it > 0 }?.let { "${it.roundToInt()}%" } ?: " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DayTile(period: DailyPeriod) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(96.dp),
    ) {
        Text(
            text = period.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // Night periods carry the low; render it quieter so the highs read
            // as the row's melody and the lows as its undertone.
            text = period.temperatureF?.let { "${it.roundToInt()}°" } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color =
                if (period.isDaytime) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Text(
            text = period.shortForecast ?: " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRefresh: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRefresh) { Text("Try again") }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val hourFormat = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val monthDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * The time the STATION took the reading — never the time we fetched it.
 *
 * This is the answer to "why doesn't the Observed time change when I hit
 * Refresh?", which is a reasonable thing to ask and not a bug. Refresh really
 * does re-fetch: nothing caches the observation (only the grid/station lookup
 * is cached, and OkHttp is built with no HTTP cache). The value simply hasn't
 * moved yet, for two independent reasons, both measured against the live API
 * on 2026-08-03:
 *
 *  - Stations publish on their own cadence. KLOU/KSDF post every 5 minutes;
 *    many others are hourly.
 *  - NWS serves this endpoint with `cache-control: max-age=183, s-maxage=300`,
 *    so even a station that just reported can be up to ~5 minutes behind.
 *
 * At 12:52 PM local the newest record available was 12:40 PM — a 12-minute lag
 * on their side, with the temperature unchanged across the previous six
 * observations.
 *
 * Substituting the clock here would make a three-hour-old reading claim to be
 * current the moment someone tapped Refresh, which is the one thing this label
 * exists to prevent. If the staleness needs to be more legible, show the age
 * ("12 min ago") rather than replacing the timestamp — see
 * just-my-weather-i0q.
 */
private fun observedLabel(snapshot: WeatherSnapshot): String? =
    snapshot.observedAt?.atZone(ZoneId.systemDefault())?.format(timeFormat)
