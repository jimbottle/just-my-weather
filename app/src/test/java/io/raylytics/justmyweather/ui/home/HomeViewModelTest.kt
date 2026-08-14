package io.raylytics.justmyweather.ui.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.raylytics.justmyweather.data.CachedSnapshot
import io.raylytics.justmyweather.data.InMemoryLastLocationStore
import io.raylytics.justmyweather.data.InMemorySnapshotCache
import io.raylytics.justmyweather.data.SnapshotCache
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.location.LocationProvider
import io.raylytics.justmyweather.location.LocationResolver
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Locks in the view-mode contract: forecasts fetch lazily once per framing,
 * Refresh invalidates everything, one framing's failure never leaks into
 * another's data, and the config's default drives the opening mode until the
 * user's session choice overrides it. Uses a real repository over a routing
 * fake transport (house style) so the fetch counting is honest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** Minimal DataStore<Preferences> so a real ViewConfigRepository runs on the JVM. */
    private class FakePreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val flow = MutableStateFlow(initial)
        override val data: Flow<Preferences> = flow

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(flow.value).also { flow.value = it }
    }

    /** Routes by endpoint, counts fetches per forecast framing, and can fail
     * the daily endpoint on demand or park one daily (or observation) response
     * on a gate so a test can act while that fetch is genuinely in flight. */
    private class RoutingTransport : HttpTransport {
        var hourlyFetches = 0
        var dailyFetches = 0
        var failDaily = false
        var failObservation = false
        var gateDaily: CompletableDeferred<Unit>? = null
        var gateObservation: CompletableDeferred<Unit>? = null

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            val body =
                when {
                    "/observations/latest" in url -> {
                        gateObservation?.let { gate ->
                            gateObservation = null
                            gate.await()
                        }
                        if (failObservation) return HttpResult(500, "boom", null)
                        OBSERVATION
                    }
                    url.endsWith("/stations") -> STATIONS
                    "/points/" in url -> POINTS
                    url.endsWith("/forecast/hourly") -> {
                        hourlyFetches++
                        HOURLY
                    }
                    url.endsWith("/forecast") -> {
                        dailyFetches++
                        // Park exactly one response, then let later ones flow.
                        gateDaily?.let { gate ->
                            gateDaily = null
                            gate.await()
                        }
                        if (failDaily) return HttpResult(500, "boom", null)
                        DAILY
                    }
                    else -> error("unexpected url $url")
                }
            return HttpResult(200, body, null)
        }
    }

    private class Harness(val transport: RoutingTransport, val vm: HomeViewModel)

    /** Builds the ViewModel over real repositories; collects [HomeViewModel.state]
     * for the test body so the WhileSubscribed combine actually runs. */
    private fun TestScope.harness(
        config: ViewConfig? = null,
        snapshots: SnapshotCache = InMemorySnapshotCache(),
        onSnapshotLoaded: suspend (WeatherSnapshot) -> Unit = {},
        // A lambda, not an Instant: a test that steps time needs the clock to
        // keep reading its variable, not a copy taken at construction.
        clock: () -> Instant = { NOW },
        zone: () -> ZoneId = { ZoneId.of("America/New_York") },
    ): Harness {
        val transport = RoutingTransport()
        val configRepository = ViewConfigRepository(FakePreferencesDataStore())
        config?.let { c -> launch { configRepository.save(c) } }
        val vm =
            HomeViewModel(
                repository =
                    WeatherRepository(
                        nws = NwsClient(transport = transport),
                        snapshotCache = snapshots,
                        clock = { NOW },
                    ),
                // No fix and nothing remembered, so this resolves to
                // WeatherLocation.DEFAULT — which the fixtures answer for.
                locationResolver =
                    LocationResolver(
                        mock<LocationProvider> { on { lastKnownLocation() } doReturn null },
                        InMemoryLastLocationStore(),
                    ),
                configRepository = configRepository,
                onSnapshotLoaded = onSnapshotLoaded,
                clock = clock,
                zone = zone,
            )
        backgroundScope.launch { vm.state.collect {} }
        return Harness(transport, vm)
    }

    private fun HomeViewModel.ready(): HomeUiState.Ready = state.value as HomeUiState.Ready

    @Test
    fun `entering a framing fetches once and re-entering does not refetch`() = runTest(dispatcher) {
        val h = harness()
        advanceUntilIdle()

        h.vm.setMode(ViewMode.HOURLY)
        advanceUntilIdle()
        assertEquals(1, h.transport.hourlyFetches)
        assertNotNull(h.vm.ready().hourly)

        h.vm.setMode(ViewMode.NOW)
        h.vm.setMode(ViewMode.HOURLY)
        advanceUntilIdle()
        assertEquals(1, h.transport.hourlyFetches)
    }

    @Test
    fun `refresh clears forecasts and refetches the active framing`() = runTest(dispatcher) {
        val h = harness()
        advanceUntilIdle()
        h.vm.setMode(ViewMode.DAILY)
        advanceUntilIdle()
        assertEquals(1, h.transport.dailyFetches)

        h.vm.refresh()
        advanceUntilIdle()
        assertEquals(2, h.transport.dailyFetches)
        assertNotNull(h.vm.ready().daily)
    }

    @Test
    fun `a failed framing keeps its error to itself and retries on re-entry`() = runTest(dispatcher) {
        val h = harness()
        advanceUntilIdle()
        h.vm.setMode(ViewMode.HOURLY)
        advanceUntilIdle()

        h.transport.failDaily = true
        h.vm.setMode(ViewMode.DAILY)
        advanceUntilIdle()
        assertNotNull(h.vm.ready().forecastError)
        assertNull(h.vm.ready().daily)

        // Back on Hourly the loaded strip shows — Daily's error stays Daily's.
        h.vm.setMode(ViewMode.HOURLY)
        advanceUntilIdle()
        assertNull(h.vm.ready().forecastError)
        assertNotNull(h.vm.ready().hourly)

        // A failed framing stayed null, so re-entering retries — and succeeds.
        h.transport.failDaily = false
        h.vm.setMode(ViewMode.DAILY)
        advanceUntilIdle()
        assertEquals(2, h.transport.dailyFetches)
        assertNotNull(h.vm.ready().daily)
        assertNull(h.vm.ready().forecastError)
    }

    @Test
    fun `tapping the already-selected chip retries a failed framing`() = runTest(dispatcher) {
        val h = harness()
        advanceUntilIdle()

        h.transport.failDaily = true
        h.vm.setMode(ViewMode.DAILY)
        advanceUntilIdle()
        assertNotNull(h.vm.ready().forecastError)

        // Same-chip tap: the mode doesn't change, so this is the retry path.
        h.transport.failDaily = false
        h.vm.setMode(ViewMode.DAILY)
        advanceUntilIdle()
        assertEquals(2, h.transport.dailyFetches)
        assertNotNull(h.vm.ready().daily)
        assertNull(h.vm.ready().forecastError)

        // The other half of the same-chip contract: once data is loaded,
        // tapping the selected chip again must NOT re-hit the API.
        h.vm.setMode(ViewMode.DAILY)
        advanceUntilIdle()
        assertEquals(2, h.transport.dailyFetches)
    }

    @Test
    fun `refresh discards the result of a fetch that was in flight when it cleared`() = runTest(dispatcher) {
        val h = harness()
        advanceUntilIdle()

        // Park the daily fetch mid-flight, then refresh while it's suspended.
        val gate = CompletableDeferred<Unit>()
        h.transport.gateDaily = gate
        h.vm.setMode(ViewMode.DAILY)
        runCurrent()
        assertEquals(1, h.transport.dailyFetches)

        h.vm.refresh()
        runCurrent()

        // The parked fetch completes with pre-refresh data; the clear (queued
        // behind the mutex) must wipe it, and the post-refresh ensure must
        // fetch fresh — the stale result never survives as "current".
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, h.transport.dailyFetches)
        assertNotNull(h.vm.ready().daily)
    }

    @Test
    fun `the config default drives the opening framing until setMode overrides it`() = runTest(dispatcher) {
        val h = harness(config = ViewConfig.DEFAULT.setDefaultMode(ViewMode.DAILY))
        advanceUntilIdle()

        // The persisted default opened Daily — and fetched it, unprompted.
        assertEquals(ViewMode.DAILY, h.vm.ready().mode)
        assertEquals(1, h.transport.dailyFetches)

        h.vm.setMode(ViewMode.NOW)
        advanceUntilIdle()
        assertEquals(ViewMode.NOW, h.vm.ready().mode)
    }

    @Test
    fun `the remembered reading fills the first paint until the live one lands`() = runTest(dispatcher) {
        val snapshots = InMemorySnapshotCache()
        snapshots.put(remembered)

        val h = harness(snapshots = snapshots)
        // Park the observation so we're looking at the app exactly as the user
        // does on launch: the fetch has started and has not come back.
        h.transport.gateObservation = CompletableDeferred()
        val gate = h.transport.gateObservation!!
        advanceUntilIdle()

        // Not "…": the last reading, shown as refreshing because it is.
        assertEquals(64.0, h.vm.ready().snapshot.temperatureF)
        assertTrue(h.vm.ready().refreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        // The live reading (20°C = 68°F) has taken over, refresh done.
        assertEquals(68.0, h.vm.ready().snapshot.temperatureF)
        assertFalse(h.vm.ready().refreshing)
    }

    @Test
    fun `a slow cache read never replaces a live reading that already arrived`() = runTest(dispatcher) {
        // The seed races the fetch it was started alongside, and on a warm
        // point cache the fetch can win. Force that order: the cache read only
        // returns after the live reading is already on screen.
        val gate = CompletableDeferred<Unit>()
        val slow =
            object : SnapshotCache {
                override suspend fun get(): CachedSnapshot? {
                    gate.await()
                    return remembered
                }

                override suspend fun put(entry: CachedSnapshot) = Unit
            }

        val h = harness(snapshots = slow)
        advanceUntilIdle()
        assertEquals(68.0, h.vm.ready().snapshot.temperatureF)

        gate.complete(Unit)
        advanceUntilIdle()
        // Still the live reading — the stale one arrived late and was dropped.
        assertEquals(68.0, h.vm.ready().snapshot.temperatureF)
        assertFalse(h.vm.ready().refreshing)
    }

    @Test
    fun `a failed fetch keeps the reading on screen rather than replacing it with an error`() =
        runTest(dispatcher) {
            val snapshots = InMemorySnapshotCache()
            snapshots.put(remembered)
            var exports = 0

            // The offline cold start: the remembered reading is all we have,
            // and the fetch that would have replaced it fails.
            val h = harness(snapshots = snapshots, onSnapshotLoaded = { exports++ })
            h.transport.failObservation = true
            advanceUntilIdle()

            assertEquals(64.0, h.vm.ready().snapshot.temperatureF)
            assertNotNull(h.vm.ready().refreshError)
            assertFalse(h.vm.ready().refreshing)
            // Nothing new was fetched, so nothing was handed to the watch —
            // re-exporting the retained reading would look like a new one.
            assertEquals(0, exports)

            // Retrying clears the message and takes the live reading.
            h.transport.failObservation = false
            h.vm.refresh()
            advanceUntilIdle()
            assertEquals(68.0, h.vm.ready().snapshot.temperatureF)
            assertNull(h.vm.ready().refreshError)
            assertEquals(1, exports)
        }

    @Test
    fun `a fetch that fails before the seed lands still keeps the reading`() = runTest(dispatcher) {
        // The offline cold start's real ordering: a DNS failure with no
        // connectivity returns in a millisecond or two, while the first read
        // of the store is still opening and parsing a file. The remembered
        // reading must survive that, or it survives only when it wasn't
        // needed. Parks the seed's read; later reads run at speed.
        val gate = CompletableDeferred<Unit>()
        val slowFirstRead =
            object : SnapshotCache {
                private var parked = false

                override suspend fun get(): CachedSnapshot? {
                    if (!parked) {
                        parked = true
                        gate.await()
                    }
                    return remembered
                }

                override suspend fun put(entry: CachedSnapshot) = Unit
            }

        val h = harness(snapshots = slowFirstRead)
        h.transport.failObservation = true
        advanceUntilIdle()

        // The seed is still parked, and the fetch has already failed.
        assertEquals(64.0, h.vm.ready().snapshot.temperatureF)
        assertNotNull(h.vm.ready().refreshError)

        // The seed arriving late changes nothing — it declines to write over
        // a state that is no longer Loading.
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(64.0, h.vm.ready().snapshot.temperatureF)
        assertNotNull(h.vm.ready().refreshError)
    }

    @Test
    fun `a reading that appears after the seed ran is still found by a later failure`() = runTest(dispatcher) {
        // The seed runs once, in init — so a reading that only becomes
        // available later (the background poll stored one between attempts)
        // is reachable only if every failure looks for itself. Otherwise one
        // empty read at startup hides it for the rest of the session.
        val store =
            object : SnapshotCache {
                var available = false

                override suspend fun get(): CachedSnapshot? = remembered.takeIf { available }

                override suspend fun put(entry: CachedSnapshot) = Unit
            }

        val h = harness(snapshots = store)
        h.transport.failObservation = true
        advanceUntilIdle()
        assertTrue(h.vm.state.value is HomeUiState.Error) // nothing to show yet

        store.available = true
        h.vm.refresh()
        advanceUntilIdle()
        assertEquals(64.0, h.vm.ready().snapshot.temperatureF)
        assertNotNull(h.vm.ready().refreshError)
    }

    @Test
    fun `with nothing on screen a failed fetch still shows the error, as before`() = runTest(dispatcher) {
        val h = harness()
        h.transport.failObservation = true
        advanceUntilIdle()

        // No remembered reading to protect, so the error screen is the only
        // honest thing to show — the pre-existing behaviour, unchanged.
        assertTrue(h.vm.state.value is HomeUiState.Error)
    }

    @Test
    fun `nothing worth remembering means the quiet placeholder, as before`() = runTest(dispatcher) {
        val h = harness()
        h.transport.gateObservation = CompletableDeferred()
        val gate = h.transport.gateObservation!!
        advanceUntilIdle()

        assertEquals(HomeUiState.Loading, h.vm.state.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(68.0, h.vm.ready().snapshot.temperatureF)
    }

    @Test
    fun `sun times are off until the config asks for them`() = runTest(dispatcher) {
        val off = harness()
        advanceUntilIdle()
        assertTrue(off.vm.ready().sunDays.isEmpty(), "the opt-in ships off")

        val on = harness(config = ViewConfig.DEFAULT.setShowSunTimes(true))
        advanceUntilIdle()
        assertTrue(on.vm.ready().sunDays.isNotEmpty(), "switched on, the glance carries them")
    }

    @Test
    fun `sun times survive a failed fetch, because they were never fetched`() = runTest(dispatcher) {
        // The claim the feature is sold on: computed on device, so a dead
        // network cannot take them away. Seed a reading so the glance still
        // renders, then fail the observation.
        val snapshots = InMemorySnapshotCache()
        snapshots.put(remembered)
        val h = harness(config = ViewConfig.DEFAULT.setShowSunTimes(true), snapshots = snapshots)
        h.transport.failObservation = true
        advanceUntilIdle()

        assertNotNull(h.vm.ready().refreshError, "the fetch really did fail")
        assertTrue(h.vm.ready().sunDays.isNotEmpty(), "sun times do not depend on the fetch")
    }

    @Test
    fun `the location resolves before the fetch, so sun times need no successful load`() =
        runTest(dispatcher) {
            // Ordering guard: SunTimes.next must run before the fetch that can
            // throw. Park the observation and assert the events are already
            // there while it is still in flight.
            val snapshots = InMemorySnapshotCache()
            snapshots.put(remembered)
            val h = harness(config = ViewConfig.DEFAULT.setShowSunTimes(true), snapshots = snapshots)
            h.transport.gateObservation = CompletableDeferred()
            val gate = h.transport.gateObservation!!
            advanceUntilIdle()

            assertTrue(h.vm.ready().sunDays.isNotEmpty(), "computed before the fetch returned")
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `the rows roll over at local midnight`() = runTest(dispatcher) {
        // Which day is "today" decays with the clock, and nothing else in the
        // ViewModel would notice. This is the whole defence against the glance
        // still heading its first row with yesterday's date.
        val zone = ZoneId.of("America/New_York")
        var now = Instant.parse("2026-08-14T03:59:00Z") // 11:59 PM on the 13th
        val h =
            harness(
                config = ViewConfig.DEFAULT.setShowSunTimes(true),
                clock = { now },
                zone = { zone },
            )
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 8, 13), h.vm.ready().sunDays.first().date, "before midnight")
        assertEquals(LocalDate.of(2026, 8, 14), h.vm.ready().sunDays[1].date, "tomorrow follows today")

        now = Instant.parse("2026-08-14T04:05:00Z") // 12:05 AM on the 14th
        h.vm.refreshSunTimes()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 8, 14), h.vm.ready().sunDays.first().date, "after midnight")
    }

    @Test
    fun `each row's times belong to that row's date`() = runTest(dispatcher) {
        // The reason day rows replaced "the next of each": a row carrying a
        // date must not carry another day's times.
        val zone = ZoneId.of("America/New_York")
        val h =
            harness(
                config = ViewConfig.DEFAULT.setShowSunTimes(true),
                clock = { Instant.parse("2026-08-14T17:00:00Z") },
                zone = { zone },
            )
        advanceUntilIdle()
        h.vm.ready().sunDays.forEach { day ->
            assertEquals(day.date, day.sunrise!!.atZone(zone).toLocalDate(), "sunrise on its own date")
            assertEquals(day.date, day.sunset!!.atZone(zone).toLocalDate(), "sunset on its own date")
        }
    }

    private companion object {
        /** Fixed "now" for the repository's freshness rule; [remembered] is
         * stored at the same instant, so it is unambiguously fresh. */
        val NOW: Instant = Instant.parse("2026-07-31T12:05:00Z")

        /** A reading for the default location (no permission → DEFAULT), at a
         * temperature the live fixture never returns, so the assertions can
         * tell the two apart. */
        val remembered =
            CachedSnapshot(
                snapshot =
                    WeatherSnapshot(
                        locationLabel = "Brooklyn, NY",
                        temperatureF = 64.0,
                        conditions = "Cloudy",
                        windMph = null,
                        precipitationIn = null,
                        pressureInHg = null,
                        observedAt = Instant.parse("2026-07-31T11:40:00Z"),
                    ),
                latitude = WeatherLocation.DEFAULT.latitude,
                longitude = WeatherLocation.DEFAULT.longitude,
                savedAt = NOW,
            )

        const val POINTS =
            """{"properties":{"gridId":"OKX","gridX":33,"gridY":35,
                "forecastZone":"https://api.weather.gov/zones/forecast/NYZ072",
                "relativeLocation":{"properties":{"city":"Brooklyn","state":"NY"}}}}"""
        const val STATIONS = """{"features":[{"properties":{"stationIdentifier":"KNYC"}}]}"""
        const val OBSERVATION =
            """{"properties":{"timestamp":"2026-07-31T12:00:00+00:00",
                "temperature":{"value":20.0,"unitCode":"wmoUnit:degC"},"textDescription":"Sunny"}}"""
        const val HOURLY =
            """{"properties":{"periods":[{"startTime":"2026-07-31T12:00:00+00:00",
                "temperature":72,"temperatureUnit":"F","windSpeed":"5 mph"}]}}"""
        const val DAILY =
            """{"properties":{"periods":[{"name":"Today","isDaytime":true,
                "temperature":81,"temperatureUnit":"F","shortForecast":"Sunny"}]}}"""
    }
}
