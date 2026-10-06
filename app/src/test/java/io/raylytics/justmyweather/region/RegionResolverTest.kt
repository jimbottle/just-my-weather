package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.view.TemperatureUnit
import io.raylytics.justmyweather.view.UnitPrefs
import io.raylytics.justmyweather.view.WindUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegionResolverTest {
    private val everyClue = RegionClues(phoneNetwork = "GB", phoneLocation = "FR", place = "US", deviceSettings = "AU")

    @Test
    fun `a manual choice wins over every clue`() {
        val resolved = RegionResolver.resolve(RegionSettings(manualRegion = "NZ"), everyClue)
        assertEquals("NZ", resolved.region.code)
        assertEquals(RegionSource.MANUAL, resolved.source)
    }

    @Test
    fun `automatic trusts the phone, then the place, then the language settings`() {
        fun resolve(clues: RegionClues) = RegionResolver.resolve(RegionSettings.AUTOMATIC, clues).let {
            it.region.code to it.source
        }
        assertEquals("GB" to RegionSource.PHONE_NETWORK, resolve(everyClue))
        assertEquals("FR" to RegionSource.PHONE_LOCATION, resolve(everyClue.copy(phoneNetwork = null)))
        assertEquals("US" to RegionSource.PLACE, resolve(everyClue.copy(phoneNetwork = null, phoneLocation = null)))
        assertEquals("AU" to RegionSource.DEVICE_SETTINGS, resolve(RegionClues(deviceSettings = "AU")))
        assertEquals("US" to RegionSource.DEFAULT, resolve(RegionClues()))
    }

    @Test
    fun `a clue that is not a real country is skipped, not trusted`() {
        val junk = RegionClues(phoneNetwork = "", place = "ZZ", deviceSettings = "ca")
        val resolved = RegionResolver.resolve(RegionSettings.AUTOMATIC, junk)
        assertEquals("CA", resolved.region.code)
    }

    @Test
    fun `a unit the user pinned survives the region, and the rest follow it`() {
        val settings = RegionSettings(temperature = TemperatureUnit.FAHRENHEIT)
        val gb = RegionResolver.resolve(settings, RegionClues(phoneNetwork = "GB"))
        val units = RegionResolver.conventions(gb, settings).units
        assertEquals(TemperatureUnit.FAHRENHEIT, units.temperature)
        assertEquals(WindUnit.MPH, units.wind)
        assertEquals(UnitPrefs.UK.pressure, units.pressure)
    }

    /** Counts what was asked, so the gathering order is visible. */
    private class Signals(
        val network: String? = null,
        val location: String? = null,
        val country: String? = null,
        val device: String? = null,
    ) : RegionSignals {
        val asked = mutableListOf<String>()

        override fun phoneNetwork() = network.also { asked += "network" }

        override suspend fun phoneLocation() = location.also { asked += "location" }

        override suspend fun countryOf(place: WeatherLocation) = country.also { asked += "place" }

        override fun deviceSettings() = device.also { asked += "device" }
    }

    @Test
    fun `gathering stops at the first real country, so a phone on a network never loads the gazetteer`() = runTest {
        val onNetwork = Signals(network = "gb", location = "FR")
        assertEquals(RegionClues(phoneNetwork = "GB"), RegionRepository.gather(onNetwork, null))
        assertEquals(listOf("network"), onNetwork.asked)

        // Wi-Fi only: the fix, then the place — whose own country needs no lookup.
        val wifi = Signals(network = "", location = null, country = "XX")
        val london = WeatherLocation(51.5, -0.1, "London", "Europe/London", "GB")
        assertEquals(RegionClues(place = "GB"), RegionRepository.gather(wifi, london))
        assertEquals(listOf("network", "location"), wifi.asked)

        val nothing = Signals(device = "IE")
        assertEquals(RegionClues(deviceSettings = "IE"), RegionRepository.gather(nothing, null))
    }

    @Test
    fun `settings and clues survive storage, and junk reads as automatic`() {
        val settings = RegionSettings(manualRegion = "AU", wind = WindUnit.KNOTS)
        assertEquals(settings, RegionCodec.decodeSettings(RegionCodec.encodeSettings(settings)))
        assertEquals(RegionSettings.AUTOMATIC, RegionCodec.decodeSettings("{ not json"))
        assertEquals(RegionSettings.AUTOMATIC, RegionCodec.decodeSettings("""{"region":"ZZ","wind":"warp"}"""))
        val clues = RegionClues(phoneNetwork = "GB")
        assertEquals(clues, RegionCodec.decodeClues(RegionCodec.encodeClues(clues)))
    }
}
