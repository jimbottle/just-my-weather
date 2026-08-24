package io.raylytics.justmyweather.location

import io.raylytics.justmyweather.data.InMemoryLastLocationStore
import io.raylytics.justmyweather.data.LastLocationStore
import io.raylytics.justmyweather.data.WeatherLocation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * The fallback chain, which is the whole point of this class. The bug it was
 * written for: every caller used to read `lastKnownLocation() ?: DEFAULT`, so
 * any moment the platform withheld a fix — which is *always*, for background
 * work in a foreground-only app — silently relocated the user to New York.
 */
class LocationResolverTest {
    private val home = WeatherLocation(38.25, -85.76, label = "")

    private fun provider(fix: WeatherLocation?) =
        mock<LocationProvider> { on { lastKnownLocation() } doReturn fix }

    @Test
    fun `a chosen place outranks even a live fix`() = runTest {
        // Someone who picked a place is saying which sky to watch. Preferring
        // wherever the phone happens to be would make the setting work only
        // while standing still — and this same resolver is what the background
        // alert poll asks.
        val chosen = WeatherLocation(39.97, -105.14, "Louisville, CO")
        val store = InMemoryLastLocationStore()
        val resolver = LocationResolver(provider(home), store, chosenPlace = { chosen })
        assertEquals(chosen, resolver.resolve())
        // …and the choice is NOT written into the "last place we knew you to
        // be" memory: that answers a different question, and polluting it
        // would outlive the choice.
        assertEquals(null, store.load())
    }

    @Test
    fun `no chosen place falls through to the fix, as before`() = runTest {
        val store = InMemoryLastLocationStore()
        val resolver = LocationResolver(provider(home), store, chosenPlace = { null })
        assertEquals(home, resolver.resolve())
    }

    @Test
    fun `a places lookup that throws must not cost the caller its fix`() = runTest {
        val store = InMemoryLastLocationStore()
        val resolver = LocationResolver(provider(home), store, chosenPlace = { error("store is broken") })
        assertEquals(home, resolver.resolve())
    }

    @Test
    fun `a live fix wins and is remembered`() = runTest {
        val store = InMemoryLastLocationStore()
        assertEquals(home, LocationResolver(provider(home), store).resolve())
        assertEquals(home, store.load())
    }

    @Test
    fun `no fix falls back to where we last knew the user to be`() = runTest {
        val store = InMemoryLastLocationStore()
        store.save(home)
        // The background poll's case: the platform gives it nothing at all.
        assertEquals(home, LocationResolver(provider(null), store).resolve())
    }

    @Test
    fun `the built-in default is only for an install that has never had a fix`() = runTest {
        assertEquals(
            WeatherLocation.DEFAULT,
            LocationResolver(provider(null), InMemoryLastLocationStore()).resolve(),
        )
    }

    @Test
    fun `a newer fix replaces the remembered one`() = runTest {
        val store = InMemoryLastLocationStore()
        store.save(home)
        val away = WeatherLocation(40.71, -74.0, label = "")
        assertEquals(away, LocationResolver(provider(away), store).resolve())
        assertEquals(away, store.load())
    }

    @Test
    fun `a broken store never costs the caller a fix it already holds`() = runTest {
        val broken =
            object : LastLocationStore {
                override suspend fun load(): WeatherLocation? = error("unreadable")

                override suspend fun save(location: WeatherLocation) = error("unwritable")
            }
        assertEquals(home, LocationResolver(provider(home), broken).resolve())
        // And with no fix to hold, it degrades to the default rather than throwing.
        assertEquals(WeatherLocation.DEFAULT, LocationResolver(provider(null), broken).resolve())
    }
}
