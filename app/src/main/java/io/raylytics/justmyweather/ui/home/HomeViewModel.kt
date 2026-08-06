package io.raylytics.justmyweather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.alerts.SafetyAlerts
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.location.LocationProvider
import io.raylytics.justmyweather.view.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * Location: device fix if granted, otherwise a sensible default so the app is
 * useful with zero setup.
 */
class HomeViewModel(
    private val repository: WeatherRepository,
    private val locationProvider: LocationProvider,
    configRepository: ViewConfigRepository,
    /**
     * Invoked with each freshly loaded snapshot. A plain function rather than a
     * named collaborator so this ViewModel stays ignorant of who else wants the
     * data — today that is the optional Gadgetbridge hand-off, wired in
     * MainActivity. Defaults to doing nothing, which is also what tests want.
     */
    private val onSnapshotLoaded: suspend (WeatherSnapshot) -> Unit = {},
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

    /** null = follow the config's default; set once the user taps a chip. */
    private val chosenMode = MutableStateFlow<ViewMode?>(null)

    // One fetch per framing at a time; re-entering a mode whose data already
    // arrived is a no-op, so chip-hopping never stampedes the API. Declared
    // before `init` — the collect launched there can call ensureForecast
    // synchronously, and Kotlin initialises properties in declaration order.
    private val forecastMutex = Mutex()

    private val mode: StateFlow<ViewMode> =
        combine(chosenMode, configRepository.config) { chosen, config ->
            chosen ?: config.defaultMode
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ViewMode.DEFAULT)

    val state: StateFlow<HomeUiState> =
        combine(weather, configRepository.config, mode, forecasts, safetyAlerts) {
                load, config, mode, forecasts, alerts ->
            when (load) {
                is WeatherLoad.Loading -> HomeUiState.Loading
                is WeatherLoad.Error -> HomeUiState.Error(load.message)
                is WeatherLoad.Ready ->
                    HomeUiState.Ready(
                        snapshot = load.snapshot,
                        config = config,
                        refreshing = load.refreshing,
                        mode = mode,
                        hourly = forecasts.hourly,
                        daily = forecasts.daily,
                        // Only the visible framing's own error — a Daily
                        // failure must never mask loaded Hourly data.
                        forecastError =
                            when (mode) {
                                ViewMode.NOW -> null
                                ViewMode.HOURLY -> forecasts.hourlyError
                                ViewMode.DAILY -> forecasts.dailyError
                            },
                        safetyAlerts = alerts,
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
            mode.collect { ensureForecast(it) }
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
                is WeatherLoad.Ready -> current.copy(refreshing = true)
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
            val loaded =
                runCatching { repository.load(location) }
                    .fold(
                        onSuccess = { WeatherLoad.Ready(it) },
                        onFailure = { WeatherLoad.Error(it.toUserMessage()) },
                    )
            weather.value = loaded
            // Hand off after the UI state is published, and never let a
            // failure here surface: an export is a side errand, so a watch
            // that isn't listening must not turn a good reading into an error
            // screen or skip the forecast fetch below.
            if (loaded is WeatherLoad.Ready) {
                runCatching { onSnapshotLoaded(loaded.snapshot) }
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
            ensureForecast(mode.value)
        }
    }

    /** The mode collector in `init` triggers the fetch on every mode change.
     * A same-chip tap changes nothing (StateFlow conflates equal values), so
     * exactly then we fetch directly — that's tap-to-retry for a failed
     * framing, and a no-op on loaded data thanks to the needed-check. The two
     * triggers can never both fire for one tap, so no double-fetch. */
    fun setMode(mode: ViewMode) {
        val sameChip = this.mode.value == mode
        chosenMode.value = mode
        if (sameChip) viewModelScope.launch { ensureForecast(mode) }
    }

    private suspend fun ensureForecast(mode: ViewMode) {
        forecastMutex.withLock {
            val current = forecasts.value
            val needed =
                when (mode) {
                    ViewMode.NOW -> return
                    ViewMode.HOURLY -> current.hourly == null
                    ViewMode.DAILY -> current.daily == null
                }
            if (!needed) return
            val location = currentLocation()
            runCatching {
                forecasts.value =
                    when (mode) {
                        ViewMode.HOURLY ->
                            current.copy(hourly = repository.loadForecast(location), hourlyError = null)
                        ViewMode.DAILY ->
                            current.copy(daily = repository.loadDailyForecast(location), dailyError = null)
                        ViewMode.NOW -> current
                    }
            }.onFailure { e ->
                forecasts.value =
                    when (mode) {
                        ViewMode.HOURLY -> current.copy(hourlyError = e.toUserMessage())
                        ViewMode.DAILY -> current.copy(dailyError = e.toUserMessage())
                        ViewMode.NOW -> current
                    }
            }
        }
    }

    private fun currentLocation(): WeatherLocation = locationProvider.lastKnownLocation() ?: WeatherLocation.DEFAULT

    /** Weather half of the screen state, kept separate from the config half. */
    private sealed interface WeatherLoad {
        data object Loading : WeatherLoad

        data class Ready(val snapshot: WeatherSnapshot, val refreshing: Boolean = false) : WeatherLoad

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
