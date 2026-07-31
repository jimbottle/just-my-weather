package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.RenderedView
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewMode
import io.raylytics.justmyweather.view.render
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
    ) {
        Text(
            text = snapshot.locationLabel.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state.mode) {
            ViewMode.NOW -> NowContent(snapshot = snapshot, config = config)
            ViewMode.HOURLY ->
                ForecastStrip(items = state.hourly, error = state.forecastError) { hour ->
                    HourTile(hour)
                }
            ViewMode.DAILY ->
                ForecastStrip(items = state.daily, error = state.forecastError) { period ->
                    DayTile(period)
                }
        }

        // The "Updated" line is secondary chrome — hidden at the spacious end so
        // the calmest view really is just the number.
        observedLabel(snapshot)?.takeIf { config.density.showsTimestamp && state.mode == ViewMode.NOW }
            ?.let { updated ->
                Text(
                    text = "Updated $updated",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

        ModeToggle(selected = state.mode, onSelect = onSetMode)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The button label reflects the refreshing flag, so a re-fetch is an
            // observable state change (and the flag stops being dead state).
            TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                Text(if (state.refreshing) "Refreshing…" else "Refresh")
            }
            TextButton(onClick = onCustomize) { Text("Customize") }
            TextButton(onClick = onAlerts) { Text("Alerts") }
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
            Column(
                modifier = Modifier.widthIn(max = spec.rowMaxWidth).padding(top = spec.sectionSpacing),
                verticalArrangement = Arrangement.spacedBy(spec.rowSpacing),
            ) {
                rendered.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = row.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
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

/** The horizontally scrolling forecast window shared by Hourly and Daily:
 * null items = first fetch still in flight; an error shows in place quietly.
 * Loaded data always wins over an error — a stale message must never cover a
 * strip we can actually draw. */
@Composable
private fun <T> ForecastStrip(
    items: List<T>?,
    error: String?,
    tile: @Composable (T) -> Unit,
) {
    when {
        !items.isNullOrEmpty() ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items.forEach { tile(it) }
            }
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

private fun observedLabel(snapshot: WeatherSnapshot): String? =
    snapshot.observedAt?.atZone(ZoneId.systemDefault())?.format(timeFormat)
