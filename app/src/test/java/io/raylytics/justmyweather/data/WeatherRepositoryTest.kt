package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.metno.MetNoClient
import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.NwsClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Covers the repository's label-fallback and point-caching behaviour — the
 * logic most likely to regress — by driving a real [NwsClient] over a routing
 * fake transport (no network).
 */
class WeatherRepositoryTest {
    /** Routes by endpoint so the same instance serves a full load and records
     * every URL, letting tests assert on cache reuse across calls. */
    private class RoutingTransport(
        private val points: String = POINTS,
        /** What /points answers with; 404 + [NOT_NWS] is a place abroad. */
        private val pointsStatus: Int = 200,
        private val metNo: String = MET_NO,
    ) : HttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            requested += url
            if ("/points/" in url && !url.endsWith("/stations") && pointsStatus != 200) {
                return HttpResult(pointsStatus, points, null)
            }
            val body =
                when {
                    "/observations/latest" in url -> OBSERVATION
                    url.endsWith("/stations") -> STATIONS
                    "/points/" in url -> points
                    "api.met.no" in url -> metNo
                    else -> error("unexpected url $url")
                }
            return HttpResult(200, body, null)
        }

        fun pointsLookups() = requested.count { "/points/" in it && !it.endsWith("/stations") }
    }

    private val honolulu = WeatherLocation(21.3, -157.9, label = "Honolulu")

    private fun repo(
        transport: RoutingTransport,
        cache: PointCache = InMemoryPointCache(),
        snapshots: SnapshotCache = InMemorySnapshotCache(),
        now: Instant = Instant.parse("2026-06-24T18:05:00Z"),
        metNo: MetNoClient? = null,
    ) = WeatherRepository(NwsClient(transport = transport), cache, snapshots, clock = { now }, metNo = metNo)

    @Test
    fun `extended days are cut in the place's calendar, which the NWS point knows`() = runTest {
        // Honolulu is UTC-10: the fixture's eight six-hour blocks (Oct 11 00Z
        // to Oct 12 18Z) make one full Honolulu day, Oct 11 (2 am, 8 am,
        // 2 pm, 8 pm); Oct 10 and Oct 12 are partial and dropped.
        val transport =
            RoutingTransport(points = POINTS.replace("\"gridId\"", "\"timeZone\":\"Pacific/Honolulu\",\"gridId\""))
        val days =
            repo(transport, metNo = MetNoClient(transport = transport))
                .loadExtendedDaily(honolulu)
        assertEquals(listOf(LocalDate.of(2026, 10, 11)), days.map { it.date })
        assertEquals(
            MetNoClient(transport = transport).getDailyForecast(21.3, -157.9, ZoneId.of("Pacific/Honolulu")),
            days,
        )
    }

    @Test
    fun `without a usable place zone, or a point at all, the device's calendar cuts the days`() = runTest {
        // Pinned, so the outcome is the same on a UTC CI runner as on a desk
        // in Kentucky; restored after, since the default is JVM-wide.
        val before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
        try {
            deviceZoneCutsTheDays()
        } finally {
            TimeZone.setDefault(before)
        }
    }

    private suspend fun deviceZoneCutsTheDays() {
        // No timeZone on the point (older cache entries have none).
        val plain = RoutingTransport()
        val expected = MetNoClient(transport = plain).getDailyForecast(21.3, -157.9, ZoneId.systemDefault())
        assertTrue(expected.isNotEmpty())
        assertEquals(
            expected,
            repo(plain, metNo = MetNoClient(transport = plain)).loadExtendedDaily(honolulu),
        )
        // /points fails outright: the NWS days are lost, MET's must not be.
        val broken = RoutingTransport(points = "{}")
        assertEquals(
            expected,
            repo(broken, metNo = MetNoClient(transport = broken)).loadExtendedDaily(honolulu),
        )
    }

    private val london = WeatherLocation(51.51, -0.13, label = "London")

    private fun abroad() = RoutingTransport(points = NOT_NWS, pointsStatus = 404, metNo = MET_NO_HOURLY)

    @Test
    fun `a place NWS does not cover is read from MET, and its reading says it is a forecast`() = runTest {
        val transport = abroad()
        val repository = repo(transport, now = Instant.parse("2026-10-06T20:20:00Z"), metNo = MetNoClient(transport))

        val now = repository.load(london)
        assertEquals("London", now.locationLabel)
        assertTrue(now.fromForecast, "a forecast hour, not a station's observation")
        // The hour that has begun (20Z, 17.4 °C), not the one coming (21Z).
        assertEquals(63.32, now.temperatureF!!, 0.01)
        assertEquals(Instant.parse("2026-10-06T20:00:00Z"), now.observedAt)
        assertEquals("Partly Cloudy", now.conditions)

        assertEquals(2, repository.loadForecast(london).size)
        // MET has no half-day periods: the Daily view takes MET's whole days.
        assertEquals(emptyList<Any>(), repository.loadDailyForecast(london))
        // No official alerts abroad, and NWS is not even asked (it answers a
        // foreign point with a 400).
        assertEquals(emptyList<Any>(), repository.loadActiveAlerts(london))
        assertTrue(transport.requested.none { "/alerts/" in it || "/stations" in it || "/gridpoints/" in it })
        // NWS said no once, and the verdict is cached like a grid point.
        assertEquals(1, transport.pointsLookups())
    }

    @Test
    fun `a GPS fix abroad has no NWS city to borrow, so it is the current location`() = runTest {
        val transport = abroad()
        val snapshot = repo(transport, metNo = MetNoClient(transport)).load(london.copy(label = ""))
        assertEquals("Current location", snapshot.locationLabel)
    }

    @Test
    fun `only NWS's explicit no counts as abroad, and any other failure is asked again`() = runTest {
        // A 500 (or a 404 of some other kind) could be a passing outage at a US
        // point; caching it as "abroad" would pin that place to MET forever.
        val transport = RoutingTransport(points = """{"status":500}""", pointsStatus = 500)
        val repository = repo(transport, metNo = MetNoClient(transport))
        repeat(2) { assertTrue(runCatching { repository.load(london) }.isFailure) }
        assertEquals(2, transport.pointsLookups(), "not cached: asked again")
        assertTrue(transport.requested.none { "api.met.no" in it }, "and not routed to MET")
    }

    @Test
    fun `abroad with no second source configured, a load fails rather than showing nothing`() = runTest {
        assertTrue(runCatching { repo(abroad()).load(london) }.isFailure)
    }

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
    fun `a jittering coarse fix resolves the point once, not once per launch`() = runTest {
        // The defect this cache was failing at. Coarse location keeps latitude
        // steady and puts a fresh random offset on longitude about every hour,
        // so a phone sitting on one doorstep produced a new key — and a new
        // /points AND /stations round trip — on nearly every launch. These are
        // real coordinates from Evan's device.
        val transport = RoutingTransport()
        val cache = InMemoryPointCache()
        val jittered =
            listOf(
                -85.6806540212635,
                -85.68068173281894,
                -85.6804653663773,
                -85.68013327780797,
                -85.68098307942641,
            )
        jittered.forEach { lon ->
            repo(transport, cache).load(WeatherLocation(38.252252252252255, lon, label = "Home"))
        }
        assertEquals(1, transport.pointsLookups(), "five launches, one grid resolution")
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

        /** NWS's answer for a point outside its territory, as the live API
         * gives it for London (2026-10-06). */
        const val NOT_NWS =
            """{"title":"Data Unavailable For Requested Point",
            "type":"https://api.weather.gov/problems/InvalidPoint","status":404}"""

        /** MET's hourly head for London: two hours, each with its own block. */
        const val MET_NO_HOURLY =
            """{"properties":{"timeseries":[
            {"time":"2026-10-06T20:00:00Z","data":{"instant":{"details":{"air_temperature":17.4,
              "apparent_air_temperature":17.4,"wind_speed":2.1,"wind_from_direction":87.2}},
              "next_1_hours":{"summary":{"symbol_code":"partlycloudy_night"},"details":{"precipitation_amount":0.0}}}},
            {"time":"2026-10-06T21:00:00Z","data":{"instant":{"details":{"air_temperature":16.9}},
              "next_1_hours":{"summary":{"symbol_code":"cloudy"},"details":{"precipitation_amount":0.2}}}}
            ]}}"""

        const val STATIONS = """{"features":[{"properties":{"stationIdentifier":"KNYC"}}]}"""

        /** Eight six-hour blocks, Oct 11 00Z through Oct 12 18Z. */
        val MET_NO =
            (0 until 8).joinToString(",", prefix = """{"properties":{"timeseries":[""", postfix = "]}}") { i ->
                val time = Instant.parse("2026-10-11T00:00:00Z").plusSeconds(i * 6L * 3600)
                """{"time":"$time","data":{"instant":{"details":{"air_temperature":20.0}},
                  "next_6_hours":{"summary":{"symbol_code":"cloudy"},
                  "details":{"air_temperature_max":22.0,"air_temperature_min":18.0,"precipitation_amount":0.0}}}}"""
            }

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
