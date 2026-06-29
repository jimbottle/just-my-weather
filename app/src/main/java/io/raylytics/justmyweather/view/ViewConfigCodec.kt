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

    @Serializable
    private data class StoredConfig(
        // Defaulted so a config written before density existed (and any future
        // build that omits it) decodes at the shipped middle rather than failing.
        val density: String = Density.DEFAULT.key,
        val items: List<StoredSetting> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: ViewConfig): String =
        json.encodeToString(
            StoredConfig(
                density = config.density.key,
                items = config.items.map { StoredSetting(it.field.key, it.visible, it.customLabel) },
            ),
        )

    /** Decode stored JSON, or the [ViewConfig.DEFAULT] on absent/corrupt data —
     * a broken preference must never crash the home view. */
    fun decode(raw: String?): ViewConfig {
        if (raw.isNullOrBlank()) return ViewConfig.DEFAULT
        // Current shape: an object with density + items.
        runCatching { json.decodeFromString<StoredConfig>(raw) }.getOrNull()?.let {
            return build(it.density, it.items)
        }
        // Legacy shape: a bare array of settings, written before density existed.
        runCatching { json.decodeFromString<List<StoredSetting>>(raw) }.getOrNull()?.let {
            return build(Density.DEFAULT.key, it)
        }
        return ViewConfig.DEFAULT
    }

    private fun build(densityKey: String, items: List<StoredSetting>): ViewConfig {
        val density = Density.byKey(densityKey) ?: Density.DEFAULT
        val settings =
            items.mapNotNull { s ->
                WeatherField.byKey(s.key)?.let { FieldSetting(it, s.visible, s.label) }
            }
        return ViewConfig.normalized(settings, density)
    }
}
