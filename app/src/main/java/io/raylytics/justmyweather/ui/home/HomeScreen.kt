package io.raylytics.justmyweather.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.view.AlertBannerPosition
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.RenderedView
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.render
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How often the observation age re-reads the clock. See [ObservedLine].
 * Internal so the instrumented test steps the clock by exactly one tick
 * instead of hardcoding a number that could drift from this one. */
internal val AGE_TICK = 30.seconds

/** How often the sun row re-reads the clock, and how often MainActivity asks
 * the ViewModel to re-work the events. One constant because the two halves are
 * one behaviour: which event is next, and which day it falls on. A minute is
 * finer than the values move; the point is being on the right side of a
 * boundary within a minute of crossing it. */
internal val SUN_TICK = 1.minutes

/**
 * The home view. Out of the box it's a calm single glance; once the user edits
 * their config it's whatever they made it — same screen, driven by data. The
 * visible fields sit as bordered modules on a flow grid, each as prominent as
 * the user made it wide. Centred in whitespace so the glance is readable in
 * well under a second.
 *
 * Arrange mode is session UI, not app state: long-pressing a module starts it,
 * Done or system back ends it, and everything it changes lands in the persisted
 * ViewConfig through [onCycleSpan]/[onMoveModule] — so there is nothing to save
 * on exit and nothing lost if the process dies mid-arrange.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onSetMode: (ForecastMode) -> Unit,
    onCustomize: () -> Unit,
    onAlerts: () -> Unit,
    onCycleSpan: (ModuleKey) -> Unit,
    onMoveModule: (ModuleKey, Int) -> Unit,
    onPlaces: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var arranging by rememberSaveable { mutableStateOf(false) }
    // Back leaves arrange mode before it leaves the screen — the launcher's
    // contract, and the gesture a wiggling grid teaches you to expect.
    BackHandler(enabled = arranging) { arranging = false }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // The controls are pinned, not scrolled. A tall day — an alert banner,
        // the sun table and an hourly strip all at once — used to push them
        // below the fold, and since the glance column carries no scrollbar or
        // clipped edge there was nothing to suggest they were still down
        // there: they simply looked absent. Giving the content weight(1f) and
        // the bar the remainder keeps it on screen at any height, with no
        // overlap and no padding arithmetic to get wrong.
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                            onSetMode = onSetMode,
                            arranging = arranging,
                            onStartArranging = { arranging = true },
                            onDoneArranging = { arranging = false },
                            onCycleSpan = onCycleSpan,
                            onMoveModule = onMoveModule,
                            onPlaces = onPlaces,
                        )
                }
            }
            // Only with a reading on screen: the error view carries its own
            // "Try again", and a bare "…" has nothing to refresh yet.
            if (state is HomeUiState.Ready) {
                ActionBar(
                    refreshing = state.refreshing,
                    refreshError = state.refreshError,
                    onRefresh = onRefresh,
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
    onSetMode: (ForecastMode) -> Unit,
    arranging: Boolean,
    onStartArranging: () -> Unit,
    onDoneArranging: () -> Unit,
    onCycleSpan: (ModuleKey) -> Unit,
    onMoveModule: (ModuleKey, Int) -> Unit,
    onPlaces: () -> Unit,
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

        // The place name is the way into choosing a place. Tapping the thing
        // you want to change beats a fourth button in the action bar, which is
        // already the busiest part of a screen built around calm — and it puts
        // the control where someone looks when they notice the wrong city.
        Text(
            text = snapshot.locationLabel.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clickable(onClick = onPlaces)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("locationLabel"),
        )

        // Grid one of two: the glance. It always leads, and the forecast grid
        // below adds to it rather than replacing it, so the screen answers
        // "right now" AND "ahead" without changing what it is.
        NowContent(
            snapshot = snapshot,
            config = config,
            sunDays = state.sunDays,
            zone = state.zone,
            arranging = arranging,
            onStartArranging = onStartArranging,
            onCycleSpan = onCycleSpan,
            onMoveModule = onMoveModule,
        )

        // Only while arranging, right under the grid it ends: the wiggle says
        // "editing", this says how editing stops. Back works too.
        if (arranging) {
            TextButton(onClick = onDoneArranging, modifier = Modifier.testTag("doneArranging")) {
                Text("Done arranging")
            }
        }

        // Grid two of two: the forecast, carrying its own Hourly/Daily toggle.
        // Absent entirely when the user has turned it off — that is what the
        // old NOW view mode meant, expressed as the forecast not being there
        // rather than as a third state of the whole screen.
        //
        // The old "Updated HH:MM" line lived down here, below the forecast and
        // density-gated. It is gone rather than duplicated: the same fact now
        // sits with the glance as "Observed HH:MM", where it explains the
        // number it belongs to instead of trailing the whole screen.
        if (config.showForecast) {
            ForecastGrid(
                mode = state.forecastMode,
                onSetMode = onSetMode,
                dailyStyle = config.dailyStyle,
                hours = state.hourly,
                periods = state.daily,
                zone = state.zone,
                error = state.forecastError,
                gap = spec.moduleGap,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (config.alertBannerPosition == AlertBannerPosition.BOTTOM) {
            SafetyAlertBanner(alerts = state.safetyAlerts)
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
    /** Not part of the snapshot: computed on the device, so the sun module
     * works with no signal. */
    sunDays: List<SunDay>,
    /** The place's zone, and the one the sun rows were computed in — the same
     * value, published together. */
    zone: ZoneId,
    arranging: Boolean,
    onStartArranging: () -> Unit,
    onCycleSpan: (ModuleKey) -> Unit,
    onMoveModule: (ModuleKey, Int) -> Unit,
) {
    val rendered: RenderedView = config.render(snapshot, sunDays, zone)
    val spec = config.density.spec()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spec.sectionSpacing),
    ) {
        if (rendered.modules.isEmpty()) {
            // Every field hidden is a legal config; an em-dash reads as "you
            // chose nothing", where a blank screen reads as a failure.
            Text(
                text = "—",
                style = spec.heroStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            ModuleGrid(
                modules = rendered.modules,
                arranging = arranging,
                spec = spec,
                onStartArranging = onStartArranging,
                onCycleSpan = onCycleSpan,
                onMove = onMoveModule,
                modifier = Modifier.fillMaxWidth(),
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
        ObservedLine(snapshot)
    }
}

/**
 * "Observed 12:40 PM · 12 min ago" — when the station took the reading, and
 * how long ago that was.
 *
 * The age carries what the timestamp cannot. A station that stopped reporting
 * three hours ago and one that published a minute ago look identical when all
 * you have is a clock time, and the timestamp deliberately doesn't move on a
 * re-fetch (see [observedLabel]) — so pressing Refresh changed nothing on
 * screen and gave no sign the fetch had happened. The age moves. Both halves
 * stay: replacing the timestamp would lose the fact the label exists to state,
 * and a separate "checked at" line would re-add chrome and read as two
 * contradictory times.
 *
 * It ticks, because an age computed once at render goes stale while you look
 * at it — the one failure a line like this cannot afford. Every [AGE_TICK]:
 * the smallest unit shown is a minute, so the label is never more than half a
 * unit behind, and a screen left open costs two wakeups a minute rather than
 * four. Only this line recomposes on a tick, not the whole glance.
 *
 * Two details in the loop below are the difference between a ticking age and a
 * lying one, and neither shows up in a screen you sit and watch:
 *
 *  - **It samples before it waits.** Sampling after the delay leaves `now`
 *    frozen at first composition for a whole tick — and the effect restarts
 *    whenever `observedAt` changes, including null → non-null when a refresh
 *    finally brings a station timestamp. `now` would then be minutes older
 *    than the reading, the gap would run backwards past
 *    [ObservationAge.FUTURE_TOLERANCE], and the age would vanish for 30
 *    seconds at the exact moment it was there to confirm the refresh.
 *  - **It runs only while RESUMED,** so it re-samples on every resume.
 *    `delay` parks on the monotonic clock, which does not advance across
 *    deep sleep: a phone left on this screen overnight would wake showing
 *    "12 min ago" for a reading twelve hours old, and only correct itself
 *    once the pending delay expired. Stopping while stopped also means a
 *    backgrounded glance costs nothing.
 */
@Composable
internal fun ObservedLine(
    snapshot: WeatherSnapshot,
    /** The wall clock, injectable so a test can decide when "now" is — the
     * defects this line has had were all in *when* it read the clock, which is
     * untestable while the read is hard-wired. */
    clock: () -> Instant = Instant::now,
) {
    val observedAt = snapshot.observedAt
    var now by remember { mutableStateOf(clock()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(observedAt, lifecycle) {
        // Nothing to age when the station omitted its timestamp — don't tick.
        if (observedAt == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = clock()
                delay(AGE_TICK)
            }
        }
    }
    // The snapshot's OWN zone, not the screen's: a station reading is taken
    // where the weather is, and during a place switch this reading is still
    // the previous place's while the new one is in flight. Formatting it at
    // the new place's offset would put a time on it that never happened.
    val time = observedLabel(snapshot, snapshot.zone ?: ZoneId.systemDefault())
    val age = observedAt?.let { ObservationAge.label(it, now) }
    Text(
        text =
            when {
                time == null -> "Observed"
                age == null -> "Observed $time"
                else -> "Observed $time · $age"
            },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The controls, pinned below the glance.
 *
 * The failed-refresh message rides here rather than in the scroll. It belongs
 * beside the button it is about — that pairing is why it stopped being a
 * full-screen error in the first place — and leaving it above the fold while
 * the button stayed pinned would have split the two apart again.
 */
@Composable
private fun ActionBar(
    refreshing: Boolean,
    refreshError: String?,
    onRefresh: () -> Unit,
    onCustomize: () -> Unit,
    onAlerts: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        refreshError?.let { message ->
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
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "Refreshing…" else "Refresh")
            }
            TextButton(onClick = onCustomize) { Text("Customize") }
            TextButton(onClick = onAlerts) { Text("Alerts") }
        }
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

// Internal, like hourFormat: the sun module formats the same kinds of value,
// and two files disagreeing about how a time reads is exactly the drift a
// shared constant prevents.
internal val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

// Internal: the forecast grid names its hour tiles with the same pattern, and
// two screens showing "3 pm" and "3 PM" for the same hour is the kind of drift
// a shared constant exists to prevent.
internal val hourFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
internal val weekdayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
internal val monthDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

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
 * exists to prevent. The staleness is made legible the other way, by [ObservedLine]
 * printing the age beside this — "Observed 12:40 PM · 12 min ago" — so the
 * timestamp keeps saying what it means and the age answers "is this current?".
 */
private fun observedLabel(snapshot: WeatherSnapshot, zone: ZoneId): String? =
    snapshot.observedAt?.atZone(zone)?.format(timeFormat)
