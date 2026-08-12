package io.raylytics.justmyweather.data

/**
 * Remembers the last place we actually knew the user to be.
 *
 * This exists because "no fix right now" is not the same as "no idea where you
 * are", and the app used to treat them as the same thing: every caller fell
 * back to [WeatherLocation.DEFAULT] — New York — the moment the platform
 * declined to hand over a location. That happens routinely. The app holds
 * foreground-only location, so background work gets nothing at all on Android
 * 10+, which silently pointed the whole alert poll at a city 700 miles from
 * the user (see [io.raylytics.justmyweather.location.LocationResolver]).
 *
 * One entry, overwritten whenever a real fix arrives. Deliberately NOT a
 * history: the question is only ever "where were we last", and keeping a trail
 * of somebody's movements is a thing this app has no reason to do.
 */
interface LastLocationStore {
    suspend fun load(): WeatherLocation?

    suspend fun save(location: WeatherLocation)
}

/** A process-lifetime store — the default when durability isn't wired in. */
class InMemoryLastLocationStore : LastLocationStore {
    private var location: WeatherLocation? = null

    override suspend fun load(): WeatherLocation? = location

    override suspend fun save(location: WeatherLocation) {
        this.location = location
    }
}
