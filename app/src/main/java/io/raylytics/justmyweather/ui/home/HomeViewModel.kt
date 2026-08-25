package io.raylytics.justmyweather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.alerts.SafetyAlerts
import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.SunTimes
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.location.LocationResolver
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ViewConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * Drives the default home glance. Two independent inputs flow in: the weather
 * (fetched on demand, [refresh]) and the user's view config (a continuous
 * stream from DataStore). They're [combine]d so changing the config on the
 * customize screen recomposes the glance instantly, with no re-fetch.
 *
 * View modes: the user's session choice ([setMode]) overrides the config's
 * default until the ViewModel dies; forecasts for a mode are fetched lazily on
 * first entry and kept until the next [refresh].
 *
 * Location: resolved through [LocationResolver], so a moment without a fix
 * falls back to where we last knew the user to be rather than to a default
 * city on the other side of the country.
 */
private const val SUN_DAYS = 2

class HomeViewModel(
    private val repository: WeatherRepository,
    private val locationResolver: LocationResolver,
    private val configRepository: ViewConfigRepository,
    /**
     * Invoked with each freshly loaded snapshot. A plain function rather than a
     * named collaborator so this ViewModel stays ignorant of who else wants the
     * data — today that is the optional Gadgetbridge hand-off, wired in
     * MainActivity. Defaults to doing nothing, which is also what tests want.
     */
    private val onSnapshotLoaded: suspend (WeatherSnapshot) -> Unit = {},
    /** Injected so the sun times are testable without waiting for dawn. */
    private val clock: () -> Instant = Instant::now,
    /** Injected alongside the clock: which local day it is decides which rows
     * the glance shows, and that answer is a zone away from the instant. */
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val weather = MutableStateFlow<WeatherLoad>(WeatherLoad.Loading)
    private val forecasts = MutableStateFlow(ForecastLoad())

    /**
     * Active safety alerts, refreshed with the weather. Its own flow rather
     * than a field on WeatherLoad because a failed alert fetch must not cost
     * the reading: the glance is still useful without it, and NWS returning an
     * error for the zone is not a reason to show an error screen.
     */
    private val safetyAlerts = MutableStateFlow<List<ActiveAlert>>(emptyList())

    /**
     * The coordinate the sun times were last worked out for, so [refreshSunTimes]
     * can redo them without a fetch or a permission read. Null until the first
     * location resolves, which is the only state in which there is nothing to
     * recompute.
     */
    private var sunLocation: WeatherLocation? = null

    /**
     * The place's zone and the sun rows worked out in it — ONE value, always.
     *
     * They were two flows, and that was a tear waiting to happen: publishing a
     * zone and then rows computed in it is two emissions, so a frame between
     * them carries one place's instants at another's offset. Bundling them
     * makes that unrepresentable instead of merely unlikely, and collapses two
     * near-identical fields on the UI state into one — the zone the rows were
     * computed in IS the current place's zone; there was never a case where
     * they should differ.
     *
     * A null zone means "not known yet" (no cached point for this place, and
     * no reading landed), and the device's zone stands in.
     */
    private val placeTimes = MutableStateFlow(PlaceTimes())

    /** null = follow the config's default; set once the user taps a chip on
     * the forecast grid. */
    private val chosenMode = MutableStateFlow<ForecastMode?>(null)

    // One fetch per framing at a time; re-entering a mode whose data already
    // arrived is a no-op, so chip-hopping never stampedes the API. Declared
    // before `init` — the collect launched there can call ensureForecast
    // synchronously, and Kotlin initialises properties in declaration order.
    private val forecastMutex = Mutex()

    /**
     * Which framing the forecast grid shows, and whether it shows at all. The
     * pair travels together because they decide one thing between them — what,
     * if anything, to fetch — and splitting them let a hidden forecast keep
     * fetching on every config change.
     *
     * NULL until the stored config arrives, and deliberately so: an eager
     * "shown, hourly" placeholder is a guess, and [refresh] runs in `init`
     * against whatever this holds. A user who had turned the forecast off paid
     * for one hourly fetch on every cold start, before their own config landed
     * and switched it back off. Null means "not known yet", nothing is fetched
     * on a guess, and the collector below fetches the moment the truth arrives.
     */
    private val forecast: StateFlow<ForecastChoice?> =
        combine(chosenMode, configRepository.config) { chosen, config ->
            ForecastChoice(config.showForecast, chosen ?: config.defaultForecastMode)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Paired so the five-flow limit of the typed [combine] still fits: these
     * two are the "what to show for right now" half of the screen. */
    private val glance = combine(weather, placeTimes) { load, times -> load to times }

    val state: StateFlow<HomeUiState> =
        combine(glance, configRepository.config, forecast, forecasts, safetyAlerts) {
                (load, times), config, forecastChoice, forecasts, alerts ->
            // Before the stored config lands, fall back to what it will say:
            // the config flow itself has already emitted by the time a Ready
            // state can exist, so this only covers the first frame.
            val shown = forecastChoice?.shown ?: config.showForecast
            val framing = forecastChoice?.mode ?: config.defaultForecastMode
            when (load) {
                is WeatherLoad.Loading -> HomeUiState.Loading
                is WeatherLoad.Error -> HomeUiState.Error(load.message)
                is WeatherLoad.Ready ->
                    HomeUiState.Ready(
                        snapshot = load.snapshot,
                        config = config,
                        refreshing = load.refreshing,
                        forecastMode = framing,
                        hourly = forecasts.hourly,
                        daily = forecasts.daily,
                        // Only the visible framing's own error — a Daily
                        // failure must never mask loaded Hourly data — and
                        // nothing at all when the grid is switched off.
                        forecastError =
                            if (!shown) {
                                null
                            } else {
                                when (framing) {
                                    ForecastMode.HOURLY -> forecasts.hourlyError
                                    ForecastMode.DAILY -> forecasts.dailyError
                                }
                            },
                        safetyAlerts = alerts,
                        refreshError = load.error,
                        // Gated here rather than in the composable so the
                        // screen renders exactly what the state says.
                        // Gated on the module being on the grid, not on a
                        // separate switch: the sun module IS the switch now.
                        sunDays = if (config.shows(ModuleKey.Sun)) times.sunDays else emptyList(),
                        // One value for both the rows above and the forecast
                        // below, so they cannot disagree. Deliberately NOT the
                        // snapshot's zone: during a place switch the snapshot
                        // on screen is still the previous place's, and it
                        // formats its own observed time from its own zone (see
                        // ObservedLine).
                        zone = times.zone ?: zone(),
                    )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        seedFromLastReading()
        refresh()
        // A mode needs its data the moment it's entered — whether by tap or by
        // the persisted default arriving from DataStore after first paint.
        // (StateFlow already skips duplicate values, so no distinct operator.)
        viewModelScope.launch {
            forecast.collect { ensureForecast(it) }
        }
    }

    /**
     * Paint the last remembered reading while the live fetch is in flight, so
     * opening the app shows weather instead of a "…" for as long as the network
     * takes. It is marked `refreshing`, which is the literal truth: the fetch
     * started in the same breath and will overwrite this the moment it lands.
     *
     * The repository decides whether a remembered reading is still honest
     * enough to show (recent, same place) — this only decides *when* to use one.
     *
     * `compareAndSet` rather than a plain assignment because this races the
     * fetch [refresh] just started, and the ordering is not ours to control: on
     * a warm cache the fetch can win. Seeding only over `Loading` means a
     * late-arriving cache read can never replace live data with older data, nor
     * clobber an error the user is looking at.
     *
     * Which makes this the *optimistic* path only — it fills the wait when the
     * fetch is slower than the store. A fetch that fails before this lands
     * reads the cache itself, in [refresh], so nothing depends on who wins.
     */
    private fun seedFromLastReading() {
        viewModelScope.launch {
            val cached = runCatching { repository.lastReading(currentLocation()) }.getOrNull() ?: return@launch
            weather.compareAndSet(WeatherLoad.Loading, WeatherLoad.Ready(cached, refreshing = true))
        }
    }

    fun refresh() {
        weather.value =
            when (val current = weather.value) {
                // The previous failure clears as the retry starts: a message
                // sitting under a "Refreshing…" button reads as the *current*
                // attempt having already failed.
                is WeatherLoad.Ready -> current.copy(refreshing = true, error = null)
                else -> WeatherLoad.Loading
            }
        viewModelScope.launch {
            // Forecasts refetch with the same gesture, so one Refresh means
            // "all of it is current", not just the visible framing. The clear
            // happens under the mutex: an in-flight mode fetch holds it across
            // its network call, so the clear serialises after that write and
            // pre-refresh data can never be resurrected past the clear.
            forecastMutex.withLock { forecasts.value = ForecastLoad() }
            val location = currentLocation()
            // Before the fetch, and independent of it: pure arithmetic that
            // cannot fail, so the sun times still appear on a dead network.
            sunLocation = location
            // Cache-only, so this stays free and offline-safe: the sun module
            // is pure arithmetic and must not wait on a fetch to be right.
            publishPlaceTimes(runCatching { repository.cachedZone(location) }.getOrNull()?.let(::parseZone))
            val result = runCatching { repository.load(location) }
            weather.value =
                result.fold(
                    onSuccess = { WeatherLoad.Ready(it) },
                    // A reading already on screen survives a failed fetch. It
                    // is the last thing we know to be true, and replacing it
                    // with a full-screen error throws away data the app has in
                    // hand — worst on an offline cold start, where the
                    // remembered reading would appear and then flash away into
                    // an error a second later. The failure is reported next to
                    // it instead, the way a failed forecast or alert fetch
                    // already declines to take down the glance.
                    onFailure = { e ->
                        val message = e.toUserMessage()
                        when (val current = weather.value) {
                            is WeatherLoad.Ready -> current.copy(refreshing = false, error = message)
                            // Nothing on screen yet — so ask the cache here
                            // rather than hoping the seed has landed. Offline
                            // is exactly where it hasn't: a DNS failure with
                            // no connectivity returns in a millisecond or two,
                            // while the seed is still opening and parsing the
                            // store on a cold process. Losing that race used
                            // to publish the error the seed then declined to
                            // overwrite, and since the seed runs once, in
                            // init, the remembered reading stayed unreachable
                            // for the rest of the session — Retry just
                            // produced the same error again. Reading it here
                            // makes the outcome the same whoever wins, and
                            // survives a retry.
                            else ->
                                runCatching { repository.lastReading(location) }.getOrNull()
                                    ?.let { WeatherLoad.Ready(it, error = message) }
                                    ?: WeatherLoad.Error(message)
                        }
                    },
                )
            // Hand off after the UI state is published, and never let a
            // failure here surface: an export is a side errand, so a watch
            // that isn't listening must not turn a good reading into an error
            // screen or skip the forecast fetch below. Keyed off the fetch
            // result, NOT off the published state: a failed fetch can now
            // leave a Ready state standing, and re-exporting the reading it
            // retained would send the watch the same reading again as if it
            // were new.
            // A landed reading is the authority on its own zone, and may be
            // the first time we learn it for a newly chosen place.
            result.onSuccess { snapshot ->
                snapshot.zone?.let { learned ->
                    // One write, carrying the zone and rows recomputed in it —
                    // a new zone means "today" may be a different day, and the
                    // two must never reach the screen separately.
                    if (placeTimes.value.zone != learned) publishPlaceTimes(learned)
                }
                runCatching { onSnapshotLoaded(snapshot) }
            }
            // Alerts are a side dish: a fetch that fails leaves the glance
            // intact and the banner simply absent.
            //
            // ONE assignment, no pre-clear. Clearing first blanked the banner
            // for the whole duration of the network round trip, so refreshing
            // with a standing tornado warning made it vanish and reappear —
            // a flicker on the one element whose presence IS the message. It
            // also bought nothing: this assignment is unconditional, so a
            // stale warning cannot outlive a location change either way.
            safetyAlerts.value =
                runCatching { SafetyAlerts.filter(repository.loadActiveAlerts(location)) }
                    .getOrDefault(emptyList())
            ensureForecast(forecast.value)
        }
    }

    /**
     * Re-work the rows for the moment it is now, in the zone already known.
     *
     * Public because the screen drives it from a timer, and it needs one:
     * "next sunrise" decays. A value computed at 5am and still on screen at
     * 9am names a sunrise that has already happened, and unlike the reading
     * above it — which carries "Observed 4 hr ago" — the sun row has no cue
     * that would let anyone notice. [refresh] is not enough on its own; it
     * runs at launch, on a permission grant and on the button, none of which
     * is "the user came back to the app four hours later".
     *
     * Free to call: pure arithmetic over a coordinate and a zone already in
     * hand, no I/O and no permission read.
     */
    fun refreshSunTimes() = publishPlaceTimes(placeTimes.value.zone)

    /**
     * Publish the place's zone and the sun rows for it, in a SINGLE write.
     *
     * Single is the whole contract. Writing a zone and then rows computed in
     * it is two emissions, and the instant between them carries one place's
     * instants at another's offset — the bug this file has now had twice, in
     * two different pairs of fields. One value, one write, no ordering for
     * anything downstream to get wrong.
     *
     * A null [zone] means the place's own is not known yet; the rows are then
     * worked out in the device's, which is the honest stand-in — and it is
     * that resolved zone that gets stored, so the screen formats with the same
     * one rather than resolving its own.
     */
    private fun publishPlaceTimes(zone: ZoneId?) {
        val location = sunLocation
        // The place's zone decides which day "today" is — for a place a few
        // hours away that is a different date for part of the day, and the
        // whole point of the day rows is saying which date a time belongs to.
        val effective = zone ?: zone()
        val days =
            if (location == null) {
                emptyList()
            } else {
                val today = clock().atZone(effective).toLocalDate()
                SunTimes.daysFrom(location.latitude, location.longitude, today, effective, SUN_DAYS)
            }
        // The RESOLVED zone, not the nullable one that came in. Storing the
        // null would leave the screen to re-resolve the device's zone for
        // itself at render time — a second, independent reading of a value
        // that can genuinely change between the two (travel, a DST boundary, a
        // settings change), and unlike two writes in one function body these
        // are separated by real time and by unrelated flow emissions. The rows
        // were computed at THIS offset, so this is the offset that has to
        // travel with them.
        placeTimes.value = PlaceTimes(effective, days)
    }

    /**
     * The place's zone and the sun rows worked out in it, travelling together.
     *
     * These instants are only meaningful alongside the offset they were worked
     * out for, and the forecast below them belongs to the same place — so one
     * value carries the lot and nothing downstream has to order anything.
     */
    private data class PlaceTimes(
        /** Null ONLY before the first publish, when there are no rows to
         * format either. After that it is always the resolved zone the rows
         * were computed in — never the "unknown" that would send the screen
         * off to resolve one of its own. */
        val zone: ZoneId? = null,
        val sunDays: List<SunDay> = emptyList(),
    )

    /** An id this JVM does not recognise costs a fallback, never a crash. */
    private fun parseZone(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()

    /** A tap on a wiggling module: step it to the next grid width. */
    fun cycleModuleSpan(module: ModuleKey) = editConfig { it.cycleSpan(module) }

    /** A drop from the arrange drag: land [module] at this visible slot. */
    fun moveModule(module: ModuleKey, toVisibleIndex: Int) = editConfig { it.moveVisible(module, toVisibleIndex) }

    /**
     * Arrange-mode edits persist as atomic transforms of the stored config —
     * [ViewConfigRepository.update] runs the transform inside the DataStore
     * edit, so the burst of edits a drag emits composes in order instead of a
     * stale read clobbering a newer write. The glance observes the same flow,
     * so the grid reflects the edit as soon as it lands; there is no separate
     * in-memory arrangement to reconcile.
     */
    private fun editConfig(transform: (ViewConfig) -> ViewConfig) {
        viewModelScope.launch { configRepository.update(transform) }
    }

    /** The mode collector in `init` triggers the fetch on every mode change.
     * A same-chip tap changes nothing (StateFlow conflates equal values), so
     * exactly then we fetch directly — that's tap-to-retry for a failed
     * framing, and a no-op on loaded data thanks to the needed-check. The two
     * triggers can never both fire for one tap, so no double-fetch. */
    fun setForecastMode(mode: ForecastMode) {
        val sameChip = forecast.value?.mode == mode
        chosenMode.value = mode
        if (sameChip) viewModelScope.launch { ensureForecast(forecast.value) }
    }

    /** No fetch at all while the forecast grid is switched off, nor before the
     * config says whether it is: the network cost of a view nobody is looking
     * at is the reason "off" is worth having. */
    private suspend fun ensureForecast(choice: ForecastChoice?) {
        if (choice == null || !choice.shown) return
        val mode = choice.mode
        forecastMutex.withLock {
            val current = forecasts.value
            val needed =
                when (mode) {
                    ForecastMode.HOURLY -> current.hourly == null
                    ForecastMode.DAILY -> current.daily == null
                }
            if (!needed) return
            val location = currentLocation()
            runCatching {
                forecasts.value =
                    when (mode) {
                        ForecastMode.HOURLY ->
                            current.copy(hourly = repository.loadForecast(location), hourlyError = null)
                        ForecastMode.DAILY ->
                            current.copy(daily = repository.loadDailyForecast(location), dailyError = null)
                    }
            }.onFailure { e ->
                forecasts.value =
                    when (mode) {
                        ForecastMode.HOURLY -> current.copy(hourlyError = e.toUserMessage())
                        ForecastMode.DAILY -> current.copy(dailyError = e.toUserMessage())
                    }
            }
        }
    }

    /** Whether the forecast grid is on screen, and which framing it shows. */
    private data class ForecastChoice(val shown: Boolean, val mode: ForecastMode)

    /** Where to ask about, resolved through the one seam that remembers a
     * real fix — never a bare fallback to the built-in default. */
    private suspend fun currentLocation(): WeatherLocation = locationResolver.resolve()

    /** Weather half of the screen state, kept separate from the config half. */
    private sealed interface WeatherLoad {
        data object Loading : WeatherLoad

        /** [error] is a refresh that failed with this reading already on
         * screen — the reading stays, the message rides alongside it. */
        data class Ready(
            val snapshot: WeatherSnapshot,
            val refreshing: Boolean = false,
            val error: String? = null,
        ) : WeatherLoad

        data class Error(val message: String) : WeatherLoad
    }

    /** Forecast half: null per framing until fetched; cleared on refresh.
     * Errors are per-framing so one mode's failure never leaks into another's
     * loaded strip; a failed framing stays null, so re-entering it retries. */
    private data class ForecastLoad(
        val hourly: List<ForecastPoint>? = null,
        val hourlyError: String? = null,
        val daily: List<DailyPeriod>? = null,
        val dailyError: String? = null,
    )

    private fun Throwable.toUserMessage(): String =
        when (this) {
            is java.net.UnknownHostException, is java.io.IOException ->
                "Couldn't reach the weather service. Check your connection."
            else -> message ?: "Something went wrong fetching the weather."
        }
}
