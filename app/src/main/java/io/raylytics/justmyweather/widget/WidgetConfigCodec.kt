package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.view.ThemeConfigCodec
import io.raylytics.justmyweather.view.ViewConfigCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Pure JSON (de)serialisation for [WidgetConfig]: one object holding the
 * view config and the theme, each in the shape its own codec writes. Nesting
 * the two codecs' output — as JSON, not as escaped strings — means a widget
 * config inherits every default and legacy fold those codecs already know,
 * and a module added to the catalog later decodes as hidden here exactly as
 * it does on the glance.
 *
 * Absent or corrupt data decodes to null: the caller then seeds a fresh
 * config from the glance, which is what a widget with no settings should be.
 */
object WidgetConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: WidgetConfig): String =
        JsonObject(
            mapOf(
                VIEW to json.parseToJsonElement(ViewConfigCodec.encode(config.view)),
                THEME to json.parseToJsonElement(ThemeConfigCodec.encode(config.theme)),
            ),
        ).toString()

    fun decode(raw: String?): WidgetConfig? {
        if (raw.isNullOrBlank()) return null
        val stored = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        // A missing or malformed half resolves to that half's default through
        // its codec; a config with neither half is not a config.
        val view = stored[VIEW] ?: return null
        return WidgetConfig(
            view = ViewConfigCodec.decode(view.toString()),
            theme = ThemeConfigCodec.decode(stored[THEME]?.toString()),
        )
    }

    private const val VIEW = "view"
    private const val THEME = "theme"
}
