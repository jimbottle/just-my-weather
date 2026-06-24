package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertRulesCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists the user's alert rules (one JSON blob) plus the set of rule ids
 * currently in a fired state. That firing set is the dedup memory: the worker
 * notifies only when a rule *enters* the set, so an ongoing condition (cold all
 * night) pings once, not every poll. Quiet by default — no rules, no set.
 */
class AlertRulesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val rules: Flow<List<AlertRule>> =
        dataStore.data.map { prefs -> AlertRulesCodec.decode(prefs[RULES]) }

    suspend fun save(rules: List<AlertRule>) {
        dataStore.edit { prefs -> prefs[RULES] = AlertRulesCodec.encode(rules) }
    }

    suspend fun firingIds(): Set<String> = dataStore.data.first()[FIRING].orEmpty()

    suspend fun setFiringIds(ids: Set<String>) {
        dataStore.edit { prefs -> prefs[FIRING] = ids }
    }

    private companion object {
        val RULES = stringPreferencesKey("alert_rules")
        val FIRING = stringSetPreferencesKey("alert_firing_ids")
    }
}
