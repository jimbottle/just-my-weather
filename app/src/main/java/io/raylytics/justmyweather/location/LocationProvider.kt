package io.raylytics.justmyweather.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import io.raylytics.justmyweather.data.WeatherLocation

/**
 * Resolves the device's approximate location using the platform
 * [LocationManager] — deliberately no Google Play Services dependency, so the
 * app stays buildable from source on any device and friendly to open
 * distribution (e.g. F-Droid).
 *
 * Coarse only: an NWS grid point is ~2.5km, so a city-level fix is plenty and
 * we never ask for fine GPS. Returns the most recent cached fix rather than
 * powering up the radio for a fresh one — a glance app shouldn't make the user
 * wait on a satellite lock.
 */
class LocationProvider(
    private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Last known coarse fix as a label-less [WeatherLocation], or null if
     * permission is absent or no fix is cached yet. */
    fun lastKnownLocation(): WeatherLocation? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val fix =
            COARSE_PROVIDERS
                .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
                .mapNotNull { provider ->
                    @Suppress("MissingPermission") // guarded by hasPermission() above
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
                ?: return null
        return WeatherLocation(latitude = fix.latitude, longitude = fix.longitude, label = "")
    }

    private companion object {
        val COARSE_PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}
