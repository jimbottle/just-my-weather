package io.raylytics.justmyweather.alerts

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for [AlertSettings], split from the DataStore
 * repository so it tests on the JVM. Every field is defaulted, so a partial or
 * older blob — or absent/corrupt data — resolves to [AlertSettings.DEFAULT]
 * rather than crashing.
 */
object AlertSettingsCodec {
    @Serializable
    private data class Stored(
        val quietHoursEnabled: Boolean = AlertSettings.DEFAULT.quietHoursEnabled,
        val quietStartHour: Int = AlertSettings.DEFAULT.quietStartHour,
        val quietEndHour: Int = AlertSettings.DEFAULT.quietEndHour,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: AlertSettings): String =
        json.encodeToString(
            Stored(settings.quietHoursEnabled, settings.quietStartHour, settings.quietEndHour),
        )

    fun decode(raw: String?): AlertSettings {
        if (raw.isNullOrBlank()) return AlertSettings.DEFAULT
        val stored = runCatching { json.decodeFromString<Stored>(raw) }.getOrNull() ?: return AlertSettings.DEFAULT
        return AlertSettings(stored.quietHoursEnabled, stored.quietStartHour, stored.quietEndHour)
    }
}
