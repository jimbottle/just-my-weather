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
        // Same defaulting story: configs written before view modes existed
        // decode to the calm Now glance.
        val mode: String = ViewMode.DEFAULT.key,
        val dailyStyle: String = DailyStyle.DEFAULT.key,
        val dailyLayout: String = DailyLayout.DEFAULT.key,
        val items: List<StoredSetting> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: ViewConfig): String =
        json.encodeToString(
            StoredConfig(
                density = config.density.key,
                mode = config.defaultMode.key,
                dailyStyle = config.dailyStyle.key,
                dailyLayout = config.dailyLayout.key,
                items = config.items.map { StoredSetting(it.field.key, it.visible, it.customLabel) },
            ),
        )

    /** Decode stored JSON, or the [ViewConfig.DEFAULT] on absent/corrupt data —
     * a broken preference must never crash the home view. */
    fun decode(raw: String?): ViewConfig {
        if (raw.isNullOrBlank()) return ViewConfig.DEFAULT
        // Current shape: an object with density + mode + daily options + items.
        runCatching { json.decodeFromString<StoredConfig>(raw) }.getOrNull()?.let {
            return build(it)
        }
        // Legacy shape: a bare array of settings, written before density existed.
        runCatching { json.decodeFromString<List<StoredSetting>>(raw) }.getOrNull()?.let {
            return build(StoredConfig(items = it))
        }
        return ViewConfig.DEFAULT
    }

    private fun build(stored: StoredConfig): ViewConfig {
        val (densityKey, modeKey, dailyStyleKey, dailyLayoutKey, items) = stored
        val settings =
            items.mapNotNull { s ->
                WeatherField.byKey(s.key)?.let { FieldSetting(it, s.visible, s.label) }
            }
        // No recognised settings means this wasn't really a config — an empty or
        // foreign JSON object (`{}`, `{"version":2}`) that `ignoreUnknownKeys`
        // happily accepts, or all-unknown field keys. normalize(emptyList) would
        // yield an all-hidden glance (hero "—", no rows), which is worse than the
        // documented contract; fall back to DEFAULT instead.
        if (settings.isEmpty()) return ViewConfig.DEFAULT
        val density = Density.byKey(densityKey) ?: Density.DEFAULT
        val mode = ViewMode.byKey(modeKey) ?: ViewMode.DEFAULT
        val dailyStyle = DailyStyle.byKey(dailyStyleKey) ?: DailyStyle.DEFAULT
        val dailyLayout = DailyLayout.byKey(dailyLayoutKey) ?: DailyLayout.DEFAULT
        return ViewConfig.normalized(settings, density, mode, dailyStyle, dailyLayout)
    }
}
