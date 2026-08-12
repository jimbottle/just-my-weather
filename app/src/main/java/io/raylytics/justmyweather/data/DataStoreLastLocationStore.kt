package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * A [LastLocationStore] that survives process death — which is the whole point,
 * since the caller most in need of it is a background worker running in a
 * process the user never launched.
 *
 * Three plain preference keys rather than a JSON blob: there is no nesting to
 * encode, and a half-written coordinate is impossible when latitude and
 * longitude are written in one `edit` transaction. A missing latitude or
 * longitude reads as "nothing remembered" rather than as zero, which would
 * silently be a point in the Atlantic.
 */
class DataStoreLastLocationStore(
    private val dataStore: DataStore<Preferences>,
) : LastLocationStore {
    override suspend fun load(): WeatherLocation? {
        val prefs = dataStore.data.first()
        val latitude = prefs[LATITUDE] ?: return null
        val longitude = prefs[LONGITUDE] ?: return null
        return WeatherLocation(latitude = latitude, longitude = longitude, label = prefs[LABEL].orEmpty())
    }

    override suspend fun save(location: WeatherLocation) {
        dataStore.edit { prefs ->
            prefs[LATITUDE] = location.latitude
            prefs[LONGITUDE] = location.longitude
            prefs[LABEL] = location.label
        }
    }

    private companion object {
        val LATITUDE = doublePreferencesKey("last_location_lat")
        val LONGITUDE = doublePreferencesKey("last_location_lon")
        val LABEL = stringPreferencesKey("last_location_label")
    }
}
