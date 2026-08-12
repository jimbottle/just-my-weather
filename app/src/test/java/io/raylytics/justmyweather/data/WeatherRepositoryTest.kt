package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.NwsClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Covers the repository's label-fallback and point-caching behaviour — the
 * logic most likely to regress — by driving a real [NwsClient] over a routing
 * fake transport (no network).
 */
class WeatherRepositoryTest {
    /** Routes by endpoint so the same instance serves a full load and records
     * every URL, letting tests assert on cache reuse across calls. */
    private class RoutingTransport : HttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            requested += url
            val body =
                when {
                    "/observations/latest" in url -> OBSERVATION
                    url.endsWith("/stations") -> STATIONS
                    "/points/" in url -> POINTS
                    else -> error("unexpected url $url")
                }
            return HttpResult(200, body, null)
        }

        fun pointsLookups() = requested.count { "/points/" in it && !it.endsWith("/stations") }
    }

    private fun repo(
        transport: RoutingTransport,
        cache: PointCache = InMemoryPointCache(),
        snapshots: SnapshotCache = InMemorySnapshotCache(),
        now: Instant = Instant.parse("2026-06-24T18:05:00Z"),
    ) = WeatherRepository(NwsClient(transport = transport), cache, snapshots, clock = { now })

    @Test
    fun `blank label is filled from the points relativeLocation`() = runTest {
        val snapshot =
            repo(RoutingTransport()).load(WeatherLocation(40.71, -74.0, label = ""))
        assertEquals("Brooklyn, NY", snapshot.locationLabel)
        assertEquals(68.0, snapshot.temperatureF!!, 1e-6)
    }

    @Test
    fun `a provided label is kept verbatim`() = runTest {
        val snapshot =
            repo(RoutingTransport()).load(WeatherLocation(40.71, -74.0, label = "Home"))
        assertEquals("Home", snapshot.locationLabel)
    }

    @Test
    fun `point resolution is cached across repeated loads of the same coordinate`() = runTest {
        val transport = RoutingTransport()
        val repository = repo(transport)
        val location = WeatherLocation(40.71, -74.0, label = "Home")
        repository.load(location)
        repository.load(location)
        // Two loads, but /points (and /stations) resolved only once; the second
        // load only re-fetches the observation.
        assertEquals(1, transport.pointsLookups())
    }

    @Test
    fun `a point from a shared cache is reused by a fresh repository without re-resolving`() = runTest {
        // Covers the repository→cache seam: a second repository sharing the cache
        // reuses the resolved point instead of re-fetching. (The durable
        // encode/decode path itself is covered by DataStorePointCacheTest.)
        val cache = InMemoryPointCache()
        val location = WeatherLocation(40.71, -74.0, label = "Home")
        repo(RoutingTransport(), cache).load(location)

        val secondStart = RoutingTransport()
        repo(secondStart, cache).load(location)
        assertEquals(0, secondStart.pointsLookups())
    }

    @Test
    fun `a successful load is remembered, and a later start reads it back`() = runTest {
        val snapshots = InMemorySnapshotCache()
        val location = WeatherLocation(40.71, -74.0, label = "Home")
        val loaded = repo(RoutingTransport(), snapshots = snapshots).load(location)

        // A fresh repository (a new process) sharing the store paints this
        // before its own fetch returns.
        val remembered = repo(RoutingTransport(), snapshots = snapshots).lastReading(location)
        assertEquals(loaded, remembered)
    }

    @Test
    fun `nothing remembered means nothing to paint`() = runTest {
        assertNull(repo(RoutingTransport()).lastReading(WeatherLocation(40.71, -74.0, label = "Home")))
    }

    @Test
    fun `a remembered reading is withheld once it is too old or too far`() = runTest {
        val snapshots = InMemorySnapshotCache()
        val location = WeatherLocation(40.71, -74.0, label = "Home")
        val savedAt = Instant.parse("2026-06-24T18:05:00Z")
        repo(RoutingTransport(), snapshots = snapshots, now = savedAt).load(location)

        // Past the cap. Overnight is deliberately INSIDE it — see
        // CachedSnapshot.MAX_AGE — so this has to reach beyond a full day.
        val muchLater =
            repo(
                RoutingTransport(),
                snapshots = snapshots,
                now = savedAt.plus(CachedSnapshot.MAX_AGE).plusSeconds(1),
            )
        assertNull(muchLater.lastReading(location))

        val sameMoment = repo(RoutingTransport(), snapshots = snapshots, now = savedAt)
        assertNull(sameMoment.lastReading(WeatherLocation(38.25, -85.76, label = "Away")))
    }

    @Test
    fun `a load that opts out leaves an existing entry intact`() = runTest {
        // The background alert poll's case: it falls back to the default
        // location when it has no fix, and must not overwrite the reading the
        // user's own launch stored for where they actually are.
        val snapshots = InMemorySnapshotCache()
        val home = WeatherLocation(38.25, -85.76, label = "Louisville, KY")
        val mine = repo(RoutingTransport(), snapshots = snapshots).load(home)

        repo(RoutingTransport(), snapshots = snapshots)
            .load(WeatherLocation.DEFAULT, remember = false)

        assertEquals(mine, repo(RoutingTransport(), snapshots = snapshots).lastReading(home))
    }

    @Test
    fun `a cache that cannot be read or written never costs the caller its reading`() = runTest {
        // The store is best-effort: DataStore can throw on a corrupt file, and
        // a launch-path crash would be a far worse bug than a blank first paint.
        val broken =
            object : SnapshotCache {
                override suspend fun get(): CachedSnapshot? = error("unreadable")

                override suspend fun put(entry: CachedSnapshot) = error("unwritable")
            }
        val location = WeatherLocation(40.71, -74.0, label = "Home")
        val repository = repo(RoutingTransport(), snapshots = broken)
        assertEquals(68.0, repository.load(location).temperatureF!!, 1e-6)
        assertNull(repository.lastReading(location))
    }

    private companion object {
        const val POINTS =
            """
            {"properties":{
              "gridId":"OKX","gridX":33,"gridY":35,
              "forecastZone":"https://api.weather.gov/zones/forecast/NYZ072",
              "relativeLocation":{"properties":{"city":"Brooklyn","state":"NY"}}
            }}
            """

        const val STATIONS = """{"features":[{"properties":{"stationIdentifier":"KNYC"}}]}"""

        const val OBSERVATION =
            """
            {"properties":{
              "timestamp":"2026-06-24T18:00:00Z",
              "temperature":{"value":20.0,"unitCode":"wmoUnit:degC"},
              "textDescription":"Partly Cloudy"
            }}
            """
    }
}
