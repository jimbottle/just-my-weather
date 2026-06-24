package io.raylytics.justmyweather.view

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for [ViewConfig], split out from the DataStore
 * repository so it tests on the JVM with no Android. Fields persist by their
 * string [WeatherField.key], not enum ordinal, so reordering or removing a
 * field in code never corrupts a saved config. Unknown keys (a field deleted
 * from the catalog) are dropped on read; missing keys (a field added) are
 * filled in as hidden by [ViewConfig.normalized].
 */
object ViewConfigCodec {
    @Serializable
    private data class StoredSetting(
        val key: String,
        val visible: Boolean,
        val label: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: ViewConfig): String =
        json.encodeToString(
            config.items.map { StoredSetting(it.field.key, it.visible, it.customLabel) },
        )

    /** Decode stored JSON, or the [ViewConfig.DEFAULT] on absent/corrupt data —
     * a broken preference must never crash the home view. */
    fun decode(raw: String?): ViewConfig {
        if (raw.isNullOrBlank()) return ViewConfig.DEFAULT
        val stored =
            runCatching { json.decodeFromString<List<StoredSetting>>(raw) }.getOrNull()
                ?: return ViewConfig.DEFAULT
        val settings =
            stored.mapNotNull { s ->
                WeatherField.byKey(s.key)?.let { FieldSetting(it, s.visible, s.label) }
            }
        return ViewConfig.normalized(settings)
    }
}
