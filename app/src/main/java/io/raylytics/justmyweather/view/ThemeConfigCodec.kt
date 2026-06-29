package io.raylytics.justmyweather.view

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for [ThemeConfig], split from the DataStore
 * repository so it tests on the JVM. Choices persist by their stable string
 * keys (not enum ordinal), and every field is defaulted so a partial or older
 * blob — or an unknown key from a future build — resolves to the shipped
 * default rather than crashing the app's theme.
 */
object ThemeConfigCodec {
    @Serializable
    private data class StoredTheme(
        val mood: String = ThemeMood.DEFAULT.key,
        val accent: String = AccentChoice.DEFAULT.key,
        val type: String = TypeChoice.DEFAULT.key,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: ThemeConfig): String =
        json.encodeToString(StoredTheme(config.mood.key, config.accent.key, config.type.key))

    /** Decode stored JSON, or [ThemeConfig.DEFAULT] on absent/corrupt data — a
     * broken preference must never crash the theme. */
    fun decode(raw: String?): ThemeConfig {
        if (raw.isNullOrBlank()) return ThemeConfig.DEFAULT
        val stored =
            runCatching { json.decodeFromString<StoredTheme>(raw) }.getOrNull() ?: return ThemeConfig.DEFAULT
        return ThemeConfig(
            mood = ThemeMood.byKey(stored.mood) ?: ThemeMood.DEFAULT,
            accent = AccentChoice.byKey(stored.accent) ?: AccentChoice.DEFAULT,
            type = TypeChoice.byKey(stored.type) ?: TypeChoice.DEFAULT,
        )
    }
}
