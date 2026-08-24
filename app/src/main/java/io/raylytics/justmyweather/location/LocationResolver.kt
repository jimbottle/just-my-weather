package io.raylytics.justmyweather.location

import io.raylytics.justmyweather.data.LastLocationStore
import io.raylytics.justmyweather.data.WeatherLocation

/**
 * The one answer to "where are we?", for everything that needs weather.
 *
 * Every caller used to resolve this itself as
 * `lastKnownLocation() ?: WeatherLocation.DEFAULT`, which reads as a harmless
 * default and is not one. The platform declines to give a location far more
 * often than that line suggests — the app holds foreground-only permission, so
 * on Android 10+ background work gets nothing whatsoever — and each of those
 * refusals silently relocated the user to New York. Confirmed in the field on
 * 2026-08-12: a Severe flood watch stood over Louisville for over an hour
 * while the hourly poll asked about New York, found it quiet, and said nothing.
 * The same fallback fed personal-rule evaluation, so those rules were being
 * judged against another city's weather entirely.
 *
 * So the fallback chain has a middle rung now: a live fix if the platform will
 * give one, otherwise **the last place we actually knew** ([store]), and only
 * failing both the built-in default. That last rung is now what it always
 * should have been — the answer for an install that has never once had a fix,
 * rather than the answer for a Tuesday afternoon.
 *
 * Every live fix is written through, so the memory keeps up with a user who
 * moves; a fix is only ever replaced by a newer fix, never by the default.
 */
class LocationResolver(
    private val provider: LocationProvider,
    private val store: LastLocationStore,
    /**
     * The place the user has chosen from their saved list, or null to follow
     * the device. A plain function rather than a repository so this class stays
     * ignorant of storage — and so tests can answer it without a DataStore.
     */
    private val chosenPlace: suspend () -> WeatherLocation? = { null },
) {
    suspend fun resolve(): WeatherLocation {
        // An explicit choice outranks a live fix, and says so first. Someone
        // who picked a place is telling the app which sky to watch; quietly
        // preferring wherever the phone happens to be would make the setting
        // work only while standing still. This runs on the background poll
        // too, which is the half that matters: the choice is what the alert
        // worker watches.
        runCatching { chosenPlace() }.getOrNull()?.let { return it }
        provider.lastKnownLocation()?.let { fix ->
            // Best-effort: a store that won't write must not cost the caller
            // the perfectly good fix it is holding.
            runCatching { store.save(fix) }
            return fix
        }
        return runCatching { store.load() }.getOrNull() ?: WeatherLocation.DEFAULT
    }
}
