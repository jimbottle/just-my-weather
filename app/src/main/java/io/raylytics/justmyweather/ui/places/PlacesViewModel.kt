package io.raylytics.justmyweather.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.places.Place
import io.raylytics.justmyweather.data.places.PlaceCatalog
import io.raylytics.justmyweather.data.places.SavedPlaces
import io.raylytics.justmyweather.data.places.SavedPlacesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the places screen. The saved list is the single source of truth and
 * lives in DataStore, so a change here reaches the glance and the alert worker
 * without either being told.
 *
 * The gazetteer is loaded once, when this ViewModel is created — i.e. when the
 * screen is opened — and released with it. It is a few megabytes that only
 * earn their keep while somebody is typing.
 */
class PlacesViewModel(
    private val repository: SavedPlacesRepository,
    private val loadCatalog: suspend () -> PlaceCatalog,
) : ViewModel() {
    val saved: StateFlow<SavedPlaces> =
        repository.saved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedPlaces.EMPTY)

    private val catalog = MutableStateFlow<PlaceCatalog?>(null)
    private val query = MutableStateFlow("")

    /** True until the gazetteer is in memory, so the screen can say "loading"
     * rather than "no matches" — those are different answers. */
    val loading: StateFlow<Boolean> =
        catalog.map { it == null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val results: StateFlow<List<Place>> =
        combine(query, catalog) { text, loaded -> loaded?.search(text).orEmpty() }
            // Searching 32k names is a few milliseconds, but it happens on
            // every keystroke and the main thread has a frame to draw.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // A missing or unreadable asset leaves search empty rather than
            // taking the screen down: the saved list and the coordinate entry
            // below it still work, which is most of what this screen is for.
            catalog.value = runCatching { loadCatalog() }.getOrNull() ?: PlaceCatalog(emptyList())
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun save(place: Place) = edit { it.add(place.toLocation()) }

    fun saveCoordinates(location: WeatherLocation) = edit { it.add(location) }

    fun select(label: String?) = edit { it.select(label) }

    fun remove(label: String) = edit { it.remove(label) }

    private fun edit(transform: (SavedPlaces) -> SavedPlaces) {
        viewModelScope.launch { repository.update(transform) }
    }
}
