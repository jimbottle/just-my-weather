package io.raylytics.justmyweather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.WeatherSnapshot
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
) : ViewModel() {
    private val weather = MutableStateFlow<WeatherLoad>(WeatherLoad.Loading)
    private val forecasts = MutableStateFlow(ForecastLoad())

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
        combine(weather, configRepository.config, mode, forecasts) { load, config, mode, forecasts ->
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
                        forecastError = forecasts.error,
                    )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        refresh()
        // A mode needs its data the moment it's entered — whether by tap or by
        // the persisted default arriving from DataStore after first paint.
        // (StateFlow already skips duplicate values, so no distinct operator.)
        viewModelScope.launch {
            mode.collect { ensureForecast(it) }
        }
    }

    fun refresh() {
        weather.value =
            when (val current = weather.value) {
                is WeatherLoad.Ready -> current.copy(refreshing = true)
                else -> WeatherLoad.Loading
            }
        // Forecasts refetch with the same gesture, so one Refresh means "all of
        // it is current", not just the visible framing.
        forecasts.value = ForecastLoad()
        viewModelScope.launch {
            val location = currentLocation()
            weather.value =
                runCatching { repository.load(location) }
                    .fold(
                        onSuccess = { WeatherLoad.Ready(it) },
                        onFailure = { WeatherLoad.Error(it.toUserMessage()) },
                    )
            ensureForecast(mode.value)
        }
    }

    fun setMode(mode: ViewMode) {
        chosenMode.value = mode
        viewModelScope.launch { ensureForecast(mode) }
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
                        ViewMode.HOURLY -> current.copy(hourly = repository.loadForecast(location), error = null)
                        ViewMode.DAILY -> current.copy(daily = repository.loadDailyForecast(location), error = null)
                        ViewMode.NOW -> current
                    }
            }.onFailure { forecasts.value = current.copy(error = it.toUserMessage()) }
        }
    }

    private fun currentLocation(): WeatherLocation = locationProvider.lastKnownLocation() ?: WeatherLocation.DEFAULT

    /** Weather half of the screen state, kept separate from the config half. */
    private sealed interface WeatherLoad {
        data object Loading : WeatherLoad

        data class Ready(val snapshot: WeatherSnapshot, val refreshing: Boolean = false) : WeatherLoad

        data class Error(val message: String) : WeatherLoad
    }

    /** Forecast half: null per framing until fetched; cleared on refresh. */
    private data class ForecastLoad(
        val hourly: List<ForecastPoint>? = null,
        val daily: List<DailyPeriod>? = null,
        val error: String? = null,
    )

    private fun Throwable.toUserMessage(): String =
        when (this) {
            is java.net.UnknownHostException, is java.io.IOException ->
                "Couldn't reach the weather service. Check your connection."
            else -> message ?: "Something went wrong fetching the weather."
        }
}
