package io.raylytics.justmyweather.region

import android.content.Context
import android.telephony.TelephonyManager
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.places.PlaceLookup
import io.raylytics.justmyweather.location.LocationProvider
import java.util.Locale

/**
 * The platform's answers to [RegionSignals]. Each is wrapped so a platform
 * that throws (no telephony on a tablet, a revoked permission) gives "no
 * clue" rather than an error: the resolver simply falls through to the next.
 */
class AndroidRegionSignals(
    context: Context,
    private val location: LocationProvider,
    private val places: PlaceLookup,
) : RegionSignals {
    private val telephony = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    /** The network the phone is registered on, not the SIM's home country:
     * a roaming phone reports the network it is visiting, which is where it
     * is. Empty on Wi-Fi-only devices and in airplane mode. */
    override fun phoneNetwork(): String? = runCatching { telephony?.networkCountryIso }.getOrNull()?.ifBlank { null }

    override suspend fun phoneLocation(): String? =
        runCatching {
            location.lastKnownLocation()?.let { fix -> places.nearest(fix.latitude, fix.longitude)?.country }
        }.getOrNull()

    override suspend fun countryOf(place: WeatherLocation): String? =
        runCatching { places.nearest(place.latitude, place.longitude)?.country }.getOrNull()

    override fun deviceSettings(): String? = Locale.getDefault().country.ifBlank { null }
}
