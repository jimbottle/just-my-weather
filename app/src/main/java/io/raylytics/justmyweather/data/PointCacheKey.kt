package io.raylytics.justmyweather.data

import java.util.Locale
import kotlin.math.roundToLong

/*
 * The key a resolved NWS point is filed under. Pure, so the rounding is
 * testable and the persisted format has exactly one definition.
 */
object PointCacheKey {
    /**
     * Decimal places kept. Two is about 1.1 km of latitude — already finer
     * than the thing being cached, since an NWS grid cell is roughly 2.5 km
     * and every coordinate inside one resolves to the same grid and station.
     *
     * The precision that was here before — a raw Double — made the cache
     * mostly useless. ACCESS_COARSE_LOCATION quantises latitude but puts a
     * randomised offset on longitude and regenerates it about once an hour,
     * so nearly every launch produced a brand-new key for the same doorstep:
     * one phone held 17 keys that all resolved to LMK/KLOU, each having cost
     * its own /points and /stations round trip. Rounding to 2dp collapses all
     * 17 into one.
     */
    private const val PLACES = 2
    private const val SCALE = 100.0

    fun of(location: WeatherLocation): String =
        "${round(location.latitude)},${round(location.longitude)}"

    /**
     * Whether [key] is in the form [of] produces. Used to discard keys written
     * by older builds: they can never be read again — nothing will ask for a
     * full-precision coordinate now — so keeping them is pure ballast in a
     * blob that is re-encoded on every write.
     */
    fun isCanonical(key: String): Boolean {
        val parts = key.split(',')
        if (parts.size != 2) return false
        return parts.all { part ->
            // isFinite, not just non-null. "NaN" parses to a perfectly good
            // Double and then throws inside roundToLong — and this runs over
            // whatever the persisted blob happens to contain, on the launch
            // path, before any write could clean it out. One such key would
            // therefore break every point resolution permanently, which is
            // exactly the crash PointCacheCodec promises corrupt data can
            // never cause. ("Infinity" survives only because roundToLong
            // saturates instead of throwing — luck, not a guarantee.)
            val value = part.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return false
            round(value) == part
        }
    }

    /**
     * Locale.ROOT is not decoration. A device set to German formats 38.25 as
     * "38,25", which would both split wrongly on the comma separator and make
     * the persisted keys unreadable to the same app on a device set to
     * English — a cache that silently empties itself when someone changes
     * their language.
     *
     * Rounding through a Long first rather than formatting the raw value: it
     * keeps -0.001 from persisting as "-0.00", a key no rounded coordinate
     * would ever match.
     */
    private fun round(value: Double): String {
        val rounded = (value * SCALE).roundToLong() / SCALE
        return String.format(Locale.ROOT, "%.${PLACES}f", rounded)
    }
}
