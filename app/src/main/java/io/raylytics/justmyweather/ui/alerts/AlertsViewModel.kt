package io.raylytics.justmyweather.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertScheduling
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
    /**
     * Called whenever the answer to "should the periodic worker be running,
     * and at what cadence?" may have changed. It receives the decision itself
     * rather than the rule list: the predicate combines rules AND the safety
     * setting, and when it lived in the caller nothing could assert it —
     * flipping it back to a rules-only test left every test green.
     */
    private val onWorkChanged: (hasWork: Boolean, pollMinutes: Int) -> Unit = { _, _ -> },
    /** Kicks an immediate alert check. Invoked after a change that could make a
     * rule newly fire, so the user gets feedback now instead of next hour. */
    private val onRuleActivated: () -> Unit = {},
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

    /**
     * Set the quiet window. A zero-length window (start == end) is refused
     * rather than saved: `isQuietAt` would then match no hour at all, so the
     * toggle would read "on" while nothing was ever silenced. The UI already
     * blocks it; this is the second line so a future caller can't reintroduce
     * the silent no-op.
     */
    fun setQuietWindow(startHour: Int, endHour: Int) {
        if (startHour == endHour) return
        if (startHour !in 0..23 || endHour !in 0..23) return
        viewModelScope.launch {
            settingsRepository.save(settings.value.copy(quietStartHour = startHour, quietEndHour = endHour))
        }
    }

    /**
     * Turning safety alerts on has to (re)schedule the worker even when the
     * user has no personal rules — that is the whole point of the setting —
     * and turning it off must not cancel a worker a live rule still needs.
     */
    fun setSafetyNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val next = settings.value.copy(safetyNotifications = enabled)
            settingsRepository.save(next)
            // `next`, not settings.value: the StateFlow is a projection of
            // DataStore and may not have re-emitted yet, so re-reading it here
            // would decide from the value we just replaced.
            syncWork(rules.value, next)
            if (enabled) onRuleActivated()
        }
    }

    fun setPollCadence(minutes: Int) {
        viewModelScope.launch {
            val next = settings.value.copy(pollMinutes = minutes)
            settingsRepository.save(next)
            // Unconditional. This used to retune only while a personal rule was
            // live — the same stale assumption fixed everywhere else — so a
            // safety-alerts user with no rules saved a new cadence that never
            // reached WorkManager until the next process start.
            syncWork(rules.value, next)
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
            syncWork(next, settings.value)
            if (check) onRuleActivated()
        }
    }

    /**
     * The single place scheduling is decided, from values passed in rather than
     * re-read, so a caller can never accidentally use pre-save state.
     */
    private fun syncWork(rules: List<AlertRule>, settings: AlertSettings) {
        onWorkChanged(AlertScheduling.hasWork(rules, settings), settings.pollMinutes)
    }
}
