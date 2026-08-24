package io.raylytics.justmyweather.view

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for [ViewConfig], split out from the DataStore
 * repository so it tests on the JVM with no Android. Modules persist by their
 * string [ModuleKey.key], never an ordinal, so reordering or removing one in
 * code cannot corrupt a saved config. Unknown keys (a module deleted from the
 * catalog) are dropped on read; missing keys (a module added) are filled in as
 * hidden by [ViewConfig.normalized].
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
        //
        // `mode` is the LEGACY key, kept read-only for migration: it held a
        // screen-wide NOW/HOURLY/DAILY, where "now" meant no forecast at all.
        // It is split on read into showForecast + forecastMode below and never
        // written again, so a config saved by this build carries the new pair
        // and an older one still opens the way its owner left it.
        val mode: String? = null,
        val showForecast: Boolean? = null,
        val forecastMode: String? = null,
        val dailyStyle: String = DailyStyle.DEFAULT.key,
        // Defaulted like every field here, so a config written before the
        // safety banner existed still decodes.
        val alertBannerPosition: String = AlertBannerPosition.DEFAULT.key,
        // LEGACY, read-only: sun times used to be a screen-wide switch rather
        // than a module with a place on the grid. It is folded on read into
        // the "sun" module's visibility and never written again, so someone
        // who had it on still sees it — as a tile they can now move and
        // resize. Defaulted false: an opt-in that switches itself on during an
        // app update is not opt-in.
        val showSunTimes: Boolean = false,
        val items: List<StoredSetting> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: ViewConfig): String =
        json.encodeToString(
            StoredConfig(
                density = config.density.key,
                showForecast = config.showForecast,
                forecastMode = config.defaultForecastMode.key,
                dailyStyle = config.dailyStyle.key,
                alertBannerPosition = config.alertBannerPosition.key,
                items = config.items.map { StoredSetting(it.module.key, it.visible, it.customLabel, it.span.key) },
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
                ModuleKey.byKey(s.key)?.let { module ->
                    val span = s.span?.let(ModuleSpan::byKey) ?: module.defaultSpan
                    ModuleSetting(module, s.visible, s.label, span)
                }
            }
        // No recognised settings means this wasn't really a config — an empty or
        // foreign JSON object (`{}`, `{"version":2}`) that `ignoreUnknownKeys`
        // happily accepts, or all-unknown module keys. normalize(emptyList) would
        // yield an all-hidden glance (hero "—", no rows), which is worse than the
        // documented contract; fall back to DEFAULT instead.
        if (settings.isEmpty()) return ViewConfig.DEFAULT
        val density = Density.byKey(stored.density) ?: Density.DEFAULT
        val bannerPosition = AlertBannerPosition.byKey(stored.alertBannerPosition) ?: AlertBannerPosition.DEFAULT
        val (showForecast, forecastMode) = stored.forecastChoice()
        return ViewConfig.normalized(
            // Fold the retired screen-wide sun switch into the module, unless
            // this config already carries a "sun" entry of its own — a config
            // written by THIS build is the authority on its own layout, and a
            // leftover `showSunTimes` must not re-show a tile the user has
            // since hidden.
            settings.withLegacySunTimes(stored.showSunTimes),
            density,
            showForecast,
            forecastMode,
            DailyStyle.byKey(stored.dailyStyle) ?: DailyStyle.DEFAULT,
            bannerPosition,
        )
    }

    private fun List<ModuleSetting>.withLegacySunTimes(showSunTimes: Boolean): List<ModuleSetting> {
        if (!showSunTimes || any { it.module == ModuleKey.Sun }) return this
        return this + ModuleSetting(ModuleKey.Sun, visible = true)
    }

    /**
     * Resolve "does the forecast show, and in which framing" from either
     * shape. The new pair wins when present; otherwise the legacy `mode` is
     * split — "now" meant the forecast was hidden, and hourly/daily meant it
     * was shown in that framing. Absent both (a config older than either), the
     * shipped default: shown, hourly.
     */
    private fun StoredConfig.forecastChoice(): Pair<Boolean, ForecastMode> {
        showForecast?.let { shown ->
            return shown to (forecastMode?.let(ForecastMode::byKey) ?: ForecastMode.DEFAULT)
        }
        return when (mode) {
            null -> true to ForecastMode.DEFAULT
            "now" -> false to ForecastMode.DEFAULT
            else -> true to (ForecastMode.byKey(mode) ?: ForecastMode.DEFAULT)
        }
    }
}
