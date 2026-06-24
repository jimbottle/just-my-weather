package io.raylytics.justmyweather.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.alerts.AlertRule
import io.raylytics.justmyweather.alerts.Comparison
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.view.WeatherField
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
) : ViewModel() {
    val rules: StateFlow<List<AlertRule>> =
        repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(field: WeatherField, comparison: Comparison, threshold: Double) =
        edit { it + AlertRule(UUID.randomUUID().toString(), field, comparison, threshold) }

    fun toggle(id: String) =
        edit { rules -> rules.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }

    fun delete(id: String) = edit { rules -> rules.filterNot { it.id == id } }

    private fun edit(transform: (List<AlertRule>) -> List<AlertRule>) {
        viewModelScope.launch { repository.save(transform(rules.value)) }
    }
}
