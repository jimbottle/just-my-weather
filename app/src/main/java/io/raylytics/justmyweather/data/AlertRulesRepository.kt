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

    /**
     * Change the rule list atomically: decode, transform, and encode inside one
     * DataStore edit, so the transform always sees the list as it is on disk at
     * that instant. Both writers go through this — the Alerts screen and the
     * background worker (fire counts, a rule switching itself off) — because a
     * read-then-[save] from either side would let one overwrite the other: the
     * worker's read-to-write window spans a network fetch, long enough for a
     * rule deleted meanwhile to come back. Returns the list as saved.
     */
    suspend fun update(transform: (List<AlertRule>) -> List<AlertRule>): List<AlertRule> {
        var result: List<AlertRule> = emptyList()
        dataStore.edit { prefs ->
            result = transform(AlertRulesCodec.decode(prefs[RULES]))
            prefs[RULES] = AlertRulesCodec.encode(result)
        }
        return result
    }

    suspend fun firingIds(): Set<String> = dataStore.data.first()[FIRING].orEmpty()

    /**
     * Safety-alert ids already notified, so a warning standing for six hours
     * notifies once rather than at every poll. Same transition idea as
     * [firingIds]; kept separate because official alerts and personal rules
     * come and go independently.
     */
    suspend fun notifiedSafetyIds(): Set<String> = dataStore.data.first()[SAFETY_NOTIFIED].orEmpty()

    suspend fun setNotifiedSafetyIds(ids: Set<String>) {
        dataStore.edit { prefs -> prefs[SAFETY_NOTIFIED] = ids }
    }

    suspend fun setFiringIds(ids: Set<String>) {
        dataStore.edit { prefs -> prefs[FIRING] = ids }
    }

    private companion object {
        val RULES = stringPreferencesKey("alert_rules")
        val FIRING = stringSetPreferencesKey("alert_firing_ids")
        val SAFETY_NOTIFIED = stringSetPreferencesKey("safety_notified_ids")
    }
}
