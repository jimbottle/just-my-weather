package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.view.PrecipitationUnit
import io.raylytics.justmyweather.view.PressureUnit
import io.raylytics.justmyweather.view.TemperatureUnit
import io.raylytics.justmyweather.view.WindUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON for the region settings and the last clues, split from the
 * DataStore store so it tests on the JVM. Units travel by their stable
 * `key`, never their enum name, so renaming an entry can't wipe a choice.
 * Anything absent, corrupt or unrecognised decodes to "automatic": a broken
 * preference must cost the user a setting, never their units.
 */
object RegionCodec {
    @Serializable
    private data class StoredSettings(
        val region: String? = null,
        val temperature: String? = null,
        val wind: String? = null,
        val pressure: String? = null,
        val precipitation: String? = null,
    )

    @Serializable
    private data class StoredClues(
        val phoneNetwork: String? = null,
        val phoneLocation: String? = null,
        val place: String? = null,
        val deviceSettings: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeSettings(settings: RegionSettings): String =
        json.encodeToString(
            StoredSettings(
                region = settings.manualRegion,
                temperature = settings.temperature?.key,
                wind = settings.wind?.key,
                pressure = settings.pressure?.key,
                precipitation = settings.precipitation?.key,
            ),
        )

    fun decodeSettings(raw: String?): RegionSettings {
        if (raw.isNullOrBlank()) return RegionSettings.AUTOMATIC
        val s =
            runCatching { json.decodeFromString<StoredSettings>(raw) }.getOrNull()
                ?: return RegionSettings.AUTOMATIC
        return RegionSettings(
            manualRegion = s.region?.takeIf(Regions::isKnown)?.uppercase(),
            temperature = TemperatureUnit.entries.firstOrNull { it.key == s.temperature },
            wind = WindUnit.entries.firstOrNull { it.key == s.wind },
            pressure = PressureUnit.entries.firstOrNull { it.key == s.pressure },
            precipitation = PrecipitationUnit.entries.firstOrNull { it.key == s.precipitation },
        )
    }

    fun encodeClues(clues: RegionClues): String =
        json.encodeToString(StoredClues(clues.phoneNetwork, clues.phoneLocation, clues.place, clues.deviceSettings))

    fun decodeClues(raw: String?): RegionClues? {
        if (raw.isNullOrBlank()) return null
        val c = runCatching { json.decodeFromString<StoredClues>(raw) }.getOrNull() ?: return null
        return RegionClues(c.phoneNetwork, c.phoneLocation, c.place, c.deviceSettings)
    }
}
