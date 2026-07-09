package io.raylytics.justmyweather.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertSettings
import io.raylytics.justmyweather.alerts.AlertSubject
import io.raylytics.justmyweather.alerts.AlertWindow
import io.raylytics.justmyweather.alerts.Comparison
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Backs the alerts screen. Like the customize screen, the persisted rule list is
 * the source of truth — every change is a pure transform saved back to DataStore,
 * and the background worker reads the same store, so a rule added here is live on
 * the next poll with no extra wiring.
 */
class AlertsViewModel(
    private val repository: AlertRulesRepository,
    private val settingsRepository: AlertSettingsRepository,
    /** Called after every change with the new rule list, to (re)schedule or
     * cancel the background worker so it runs only when rules are live. */
    private val onRulesChanged: (List<AlertRule>) -> Unit = {},
    /** Kicks an immediate alert check. Invoked after a change that could make a
     * rule newly fire, so the user gets feedback now instead of next hour. */
    private val onRuleActivated: () -> Unit = {},
    /** Called when the poll cadence changes, to retune the periodic worker. */
    private val onCadenceChanged: (Int) -> Unit = {},
) : ViewModel() {
    val rules: StateFlow<List<AlertRule>> =
        repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AlertSettings> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AlertSettings.DEFAULT,
        )

    fun setQuietHours(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.save(settings.value.copy(quietHoursEnabled = enabled)) }
    }

    fun setPollCadence(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.save(settings.value.copy(pollMinutes = minutes))
            // Retune the worker only while rules are live (else it's not scheduled).
            if (rules.value.any { it.enabled }) onCadenceChanged(minutes)
        }
    }

    fun add(
        subject: AlertSubject,
        comparison: Comparison,
        threshold: Double,
        window: AlertWindow = AlertWindow.NOW,
    ) = edit(check = true) {
        it + AlertRule(UUID.randomUUID().toString(), subject, comparison, threshold, window = window)
    }

    // Only an *enable* can make a rule newly fire; disabling just drops it, so
    // skip the network check in that case.
    fun toggle(id: String) {
        val enabling = rules.value.any { it.id == id && !it.enabled }
        edit(check = enabling) { rules -> rules.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }
    }

    // Deleting can't make a rule fire, so no check needed.
    fun delete(id: String) = edit(check = false) { rules -> rules.filterNot { it.id == id } }

    private fun edit(check: Boolean, transform: (List<AlertRule>) -> List<AlertRule>) {
        viewModelScope.launch {
            val next = transform(rules.value)
            repository.save(next)
            // Every edit re-syncs scheduling (add/enable starts it, removing the
            // last enabled rule stops it); only an activating change checks now.
            onRulesChanged(next)
            if (check) onRuleActivated()
        }
    }
}
