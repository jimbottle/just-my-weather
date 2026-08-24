package io.raylytics.justmyweather.data.places

import io.raylytics.justmyweather.data.WeatherLocation

/**
 * The places the user has kept, and which one the app is currently showing.
 *
 * [selected] is a label rather than an index because an index is a promise the
 * list has to keep: removing a place would silently re-point the app at
 * whichever place slid into that slot, which is the sort of bug nobody
 * notices until the weather is wrong. A label that no longer exists simply
 * resolves to "follow the device", which is the safe direction to fail.
 *
 * A null [selected] means exactly that — follow the device's own location, the
 * way the app always has. It is the default, and it is what "Use my location"
 * puts back.
 */
data class SavedPlaces(
    val places: List<WeatherLocation> = emptyList(),
    val selected: String? = null,
) {
    /** The chosen place, or null to follow the device. */
    val current: WeatherLocation?
        get() = selected?.let { label -> places.firstOrNull { it.label == label } }

    /** Add (or re-select) a place and show it. Labels are the identity here,
     * so saving somewhere already saved selects it rather than duplicating. */
    fun add(place: WeatherLocation): SavedPlaces =
        if (places.any { it.label == place.label }) {
            copy(selected = place.label)
        } else {
            copy(places = places + place, selected = place.label)
        }

    fun remove(label: String): SavedPlaces =
        copy(
            places = places.filterNot { it.label == label },
            // Removing the place you are looking at hands you back to the
            // device rather than to an arbitrary neighbour.
            selected = selected?.takeIf { it != label },
        )

    fun select(label: String?): SavedPlaces = copy(selected = label)

    companion object {
        val EMPTY = SavedPlaces()
    }
}
