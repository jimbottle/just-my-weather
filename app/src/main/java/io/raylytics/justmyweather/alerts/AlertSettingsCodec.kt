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
        val pollMinutes: Int = AlertSettings.DEFAULT.pollMinutes,
        val safetyNotifications: Boolean = AlertSettings.DEFAULT.safetyNotifications,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: AlertSettings): String =
        json.encodeToString(
            Stored(
                settings.quietHoursEnabled,
                settings.quietStartHour,
                settings.quietEndHour,
                settings.pollMinutes,
                settings.safetyNotifications,
            ),
        )

    fun decode(raw: String?): AlertSettings {
        if (raw.isNullOrBlank()) return AlertSettings.DEFAULT
        val stored = runCatching { json.decodeFromString<Stored>(raw) }.getOrNull() ?: return AlertSettings.DEFAULT
        // An out-of-range cadence (older/foreign blob) falls back to the default.
        val poll =
            stored.pollMinutes.takeIf { it in AlertSettings.POLL_CHOICES } ?: AlertSettings.DEFAULT.pollMinutes
        return AlertSettings(
            stored.quietHoursEnabled,
            stored.quietStartHour,
            stored.quietEndHour,
            poll,
            stored.safetyNotifications,
        )
    }
}
