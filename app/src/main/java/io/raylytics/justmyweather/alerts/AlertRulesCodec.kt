package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.view.WeatherField
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for the alert rule list, split from the DataStore
 * repository so it tests on the JVM. Like the view config, rules persist by
 * field/comparison string keys (not enum ordinal), and a rule referencing a key
 * this build no longer knows is dropped on read rather than crashing.
 */
object AlertRulesCodec {
    @Serializable
    private data class StoredRule(
        val id: String,
        val field: String,
        val comparison: String,
        val threshold: Double,
        val enabled: Boolean = true,
        // Defaulted so rules written before forecast windows existed decode as
        // NOW rules rather than being dropped.
        val window: String = AlertWindow.NOW.key,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(rules: List<AlertRule>): String =
        json.encodeToString(
            rules.map {
                StoredRule(it.id, it.field.key, it.comparison.key, it.threshold, it.enabled, it.window.key)
            },
        )

    fun decode(raw: String?): List<AlertRule> {
        if (raw.isNullOrBlank()) return emptyList()
        val stored =
            runCatching { json.decodeFromString<List<StoredRule>>(raw) }.getOrNull() ?: return emptyList()
        return stored.mapNotNull { s ->
            val field = WeatherField.byKey(s.field) ?: return@mapNotNull null
            val comparison = Comparison.byKey(s.comparison) ?: return@mapNotNull null
            // An unknown window key falls back to NOW rather than dropping the rule.
            val window = AlertWindow.byKey(s.window) ?: AlertWindow.NOW
            AlertRule(s.id, field, comparison, s.threshold, s.enabled, window)
        }
    }
}
