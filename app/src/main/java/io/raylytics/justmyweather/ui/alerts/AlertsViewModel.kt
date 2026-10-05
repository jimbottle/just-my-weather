package io.raylytics.justmyweather.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.AlertScheduling
import io.raylytics.justmyweather.alerts.AlertSettings
import io.raylytics.justmyweather.alerts.AlertSubject
import io.raylytics.justmyweather.alerts.AlertWindow
import io.raylytics.justmyweather.alerts.Comparison
import io.raylytics.justmyweather.alerts.FireLimit
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    /**
     * One-shot confirmations for the screen to surface and forget — a snackbar,
     * not state. Sent only after the write lands, so "Alert created" is never
     * shown for a rule that didn't persist. A Channel rather than a StateFlow
     * because a confirmation has no "current value": re-collecting after a
     * rotation must not replay it.
     */
    private val eventChannel = Channel<AlertsEvent>(Channel.BUFFERED)
    val events: Flow<AlertsEvent> = eventChannel.receiveAsFlow()

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
        limit: FireLimit = FireLimit.UNLIMITED,
    ) = edit(activates = { _, _ -> true }, confirmation = AlertsEvent.RULE_ADDED) {
        it + AlertRule(UUID.randomUUID().toString(), subject, comparison, threshold, window = window, limit = limit)
    }

    // Only an *enable* can make a rule newly fire; disabling just drops it, so
    // skip the network check in that case. Switching a spent rule back on is
    // how it is re-armed, so the count starts over — otherwise it would be off
    // again at the next tick without ever notifying.
    fun toggle(id: String) =
        edit(
            // Decided from the list the transform actually saw, not the
            // StateFlow: the worker may have just switched this rule off and
            // the projection not caught up, and that re-arm must check now.
            activates = { before, after ->
                before.any { it.id == id && !it.enabled } && after.any { it.id == id && it.enabled }
            },
        ) { rules ->
            rules.map {
                when {
                    it.id != id -> it
                    it.enabled -> it.copy(enabled = false)
                    else -> it.copy(enabled = true, firedCount = if (it.isSpent) 0 else it.firedCount)
                }
            }
        }

    // Deleting can't make a rule fire, so no check needed.
    fun delete(id: String) = edit { rules -> rules.filterNot { it.id == id } }

    /**
     * Every edit is one atomic repository update: the background worker also
     * writes this list (fire counts, a rule switching itself off), so a
     * transform of the StateFlow's snapshot could silently undo its work.
     * [activates] sees the before and after lists and says whether the change
     * could make a rule newly fire, which is what warrants an immediate check.
     * [confirmation], if given, is sent to [events] once the edit is saved.
     */
    private fun edit(
        activates: (before: List<AlertRule>, after: List<AlertRule>) -> Boolean = { _, _ -> false },
        confirmation: AlertsEvent? = null,
        transform: (List<AlertRule>) -> List<AlertRule>,
    ) {
        viewModelScope.launch {
            var before: List<AlertRule> = emptyList()
            val next =
                repository.update { current ->
                    before = current
                    transform(current)
                }
            // Every edit re-syncs scheduling (add/enable starts it, removing the
            // last enabled rule stops it); only an activating change checks now.
            syncWork(next, settings.value)
            if (activates(before, next)) onRuleActivated()
            confirmation?.let { eventChannel.send(it) }
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

/** Things the alerts screen acknowledges once and lets go of. */
enum class AlertsEvent {
    /** A new rule was saved. The screen says so, briefly. */
    RULE_ADDED,
}
