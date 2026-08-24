package io.raylytics.justmyweather.data.places

import io.raylytics.justmyweather.data.WeatherLocation
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for [SavedPlaces], split out from the DataStore
 * repository so it tests on the JVM with no Android — the same shape the view
 * and alert configs use.
 *
 * Coordinates are stored as numbers and read back only when finite. A NaN or
 * infinite coordinate is not a place, and it would reach `PointCacheKey.of`,
 * where rounding a NaN throws, on the launch path — the same trap
 * `DataStoreLastLocationStore` documents.
 */
object SavedPlacesCodec {
    @Serializable
    private data class StoredPlace(
        val label: String,
        val lat: Double,
        val lon: Double,
    )

    @Serializable
    private data class Stored(
        val places: List<StoredPlace> = emptyList(),
        val selected: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(saved: SavedPlaces): String =
        json.encodeToString(
            Stored(
                places = saved.places.map { StoredPlace(it.label, it.latitude, it.longitude) },
                selected = saved.selected,
            ),
        )

    /** Decode, or [SavedPlaces.EMPTY] on absent/corrupt data — a broken
     * preference must never cost the user their weather, only their list. */
    fun decode(raw: String?): SavedPlaces {
        if (raw.isNullOrBlank()) return SavedPlaces.EMPTY
        val stored = runCatching { json.decodeFromString<Stored>(raw) }.getOrNull() ?: return SavedPlaces.EMPTY
        val places =
            stored.places.mapNotNull { place ->
                if (place.label.isBlank() || !place.lat.isFinite() || !place.lon.isFinite()) return@mapNotNull null
                WeatherLocation(latitude = place.lat, longitude = place.lon, label = place.label)
            }
        // A selection naming a place that did not survive the read resolves to
        // "follow the device" rather than dangling — the safe direction.
        val selected = stored.selected?.takeIf { label -> places.any { it.label == label } }
        return SavedPlaces(places, selected)
    }
}
