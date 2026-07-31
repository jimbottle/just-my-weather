package io.raylytics.justmyweather.ui.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.NwsClient
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

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
     * the daily endpoint on demand or park one daily response on a gate so a
     * test can act while that fetch is genuinely in flight. */
    private class RoutingTransport : HttpTransport {
        var hourlyFetches = 0
        var dailyFetches = 0
        var failDaily = false
        var gateDaily: CompletableDeferred<Unit>? = null

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            val body =
                when {
                    "/observations/latest" in url -> OBSERVATION
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
    private fun TestScope.harness(config: ViewConfig? = null): Harness {
        val transport = RoutingTransport()
        val configRepository = ViewConfigRepository(FakePreferencesDataStore())
        config?.let { c -> launch { configRepository.save(c) } }
        val vm =
            HomeViewModel(
                repository = WeatherRepository(NwsClient(transport = transport)),
                locationProvider = mock { on { lastKnownLocation() } doReturn null },
                configRepository = configRepository,
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

    private companion object {
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
