package io.raylytics.justmyweather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the default home glance. Picks a location (device fix if granted,
 * otherwise a sensible default so the app is useful with zero setup), asks the
 * repository for a snapshot, and exposes it as a single [HomeUiState].
 *
 * No customization wired in yet — this is the "never opens settings" path. The
 * view-config layer will later feed which fields to show.
 */
class HomeViewModel(
    private val repository: WeatherRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val current = _state.value
        _state.value =
            when (current) {
                is HomeUiState.Ready -> current.copy(refreshing = true)
                else -> HomeUiState.Loading
            }
        viewModelScope.launch {
            val location = locationProvider.lastKnownLocation() ?: DEFAULT_LOCATION
            _state.value =
                runCatching { repository.load(location) }
                    .fold(
                        onSuccess = { HomeUiState.Ready(it) },
                        onFailure = { HomeUiState.Error(it.toUserMessage()) },
                    )
        }
    }

    private fun Throwable.toUserMessage(): String =
        when (this) {
            is java.net.UnknownHostException, is java.io.IOException ->
                "Couldn't reach the weather service. Check your connection."
            else -> message ?: "Something went wrong fetching the weather."
        }

    companion object {
        /** Until the user grants location or picks a place, show somewhere
         * real so a fresh install is never blank. */
        val DEFAULT_LOCATION =
            WeatherLocation(latitude = 40.7128, longitude = -74.0060, label = "New York, NY")
    }
}
