package io.raylytics.justmyweather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the default home glance. Two independent inputs flow in: the weather
 * (fetched on demand, [refresh]) and the user's view config (a continuous
 * stream from DataStore). They're [combine]d so changing the config on the
 * customize screen recomposes the glance instantly, with no re-fetch.
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

    val state: StateFlow<HomeUiState> =
        combine(weather, configRepository.config) { load, config ->
            when (load) {
                is WeatherLoad.Loading -> HomeUiState.Loading
                is WeatherLoad.Error -> HomeUiState.Error(load.message)
                is WeatherLoad.Ready -> HomeUiState.Ready(load.snapshot, config, load.refreshing)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        weather.value =
            when (val current = weather.value) {
                is WeatherLoad.Ready -> current.copy(refreshing = true)
                else -> WeatherLoad.Loading
            }
        viewModelScope.launch {
            val location = locationProvider.lastKnownLocation() ?: WeatherLocation.DEFAULT
            weather.value =
                runCatching { repository.load(location) }
                    .fold(
                        onSuccess = { WeatherLoad.Ready(it) },
                        onFailure = { WeatherLoad.Error(it.toUserMessage()) },
                    )
        }
    }

    /** Weather half of the screen state, kept separate from the config half. */
    private sealed interface WeatherLoad {
        data object Loading : WeatherLoad

        data class Ready(val snapshot: WeatherSnapshot, val refreshing: Boolean = false) : WeatherLoad

        data class Error(val message: String) : WeatherLoad
    }

    private fun Throwable.toUserMessage(): String =
        when (this) {
            is java.net.UnknownHostException, is java.io.IOException ->
                "Couldn't reach the weather service. Check your connection."
            else -> message ?: "Something went wrong fetching the weather."
        }
}
