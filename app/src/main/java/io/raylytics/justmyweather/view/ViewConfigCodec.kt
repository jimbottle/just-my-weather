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
        // Defaulted null so a config written before modules had widths decodes;
        // null (or an unknown key) resolves to the field's own default span.
        val span: String? = null,
    )

    @Serializable
    private data class StoredConfig(
        // Defaulted so a config written before density existed (and any future
        // build that omits it) decodes at the shipped middle rather than failing.
        val density: String = Density.DEFAULT.key,
        // Same defaulting story: configs written before an option existed
        // decode to that option's shipped default.
        val mode: String = ViewMode.DEFAULT.key,
        val dailyStyle: String = DailyStyle.DEFAULT.key,
        val dailyLayout: String = ForecastLayout.DEFAULT.key,
        val hourlyLayout: String = ForecastLayout.DEFAULT.key,
        // Defaulted like every field here, so a config written before the
        // safety banner existed still decodes.
        val alertBannerPosition: String = AlertBannerPosition.DEFAULT.key,
        // Defaulted false so a config written before sun times existed decodes
        // to the feature being off, rather than switching itself on.
        val showSunTimes: Boolean = false,
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
                hourlyLayout = config.hourlyLayout.key,
                alertBannerPosition = config.alertBannerPosition.key,
                showSunTimes = config.showSunTimes,
                items = config.items.map { StoredSetting(it.field.key, it.visible, it.customLabel, it.span.key) },
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

    // Named property reads only — positional destructuring would silently
    // rebind keys if StoredConfig's field order ever changes, and the codec's
    // fall-back-to-default resilience would mask the corruption.
    private fun build(stored: StoredConfig): ViewConfig {
        val settings =
            stored.items.mapNotNull { s ->
                WeatherField.byKey(s.key)?.let { field ->
                    val span = s.span?.let(ModuleSpan::byKey) ?: field.defaultSpan
                    FieldSetting(field, s.visible, s.label, span)
                }
            }
        // No recognised settings means this wasn't really a config — an empty or
        // foreign JSON object (`{}`, `{"version":2}`) that `ignoreUnknownKeys`
        // happily accepts, or all-unknown field keys. normalize(emptyList) would
        // yield an all-hidden glance (hero "—", no rows), which is worse than the
        // documented contract; fall back to DEFAULT instead.
        if (settings.isEmpty()) return ViewConfig.DEFAULT
        val density = Density.byKey(stored.density) ?: Density.DEFAULT
        val mode = ViewMode.byKey(stored.mode) ?: ViewMode.DEFAULT
        val dailyStyle = DailyStyle.byKey(stored.dailyStyle) ?: DailyStyle.DEFAULT
        val dailyLayout = ForecastLayout.byKey(stored.dailyLayout) ?: ForecastLayout.DEFAULT
        val hourlyLayout = ForecastLayout.byKey(stored.hourlyLayout) ?: ForecastLayout.DEFAULT
        val bannerPosition = AlertBannerPosition.byKey(stored.alertBannerPosition) ?: AlertBannerPosition.DEFAULT
        return ViewConfig.normalized(
            settings,
            density,
            mode,
            dailyStyle,
            dailyLayout,
            hourlyLayout,
            bannerPosition,
            stored.showSunTimes,
        )
    }
}
