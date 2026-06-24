package io.raylytics.justmyweather.ui.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.WeatherField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the customize screen. The persisted config is the single source of
 * truth: every edit is a pure transform of the latest config, saved back to
 * DataStore. Because the home view observes the same repository flow, edits
 * here show up there immediately — no event bus, no manual refresh.
 */
class CustomizeViewModel(
    private val repository: ViewConfigRepository,
) : ViewModel() {
    val config: StateFlow<ViewConfig> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewConfig.DEFAULT)

    fun toggle(field: WeatherField) = edit { it.toggle(field) }

    fun relabel(field: WeatherField, label: String?) = edit { it.relabel(field, label) }

    fun moveUp(index: Int) = edit { it.moveUp(index) }

    fun moveDown(index: Int) = edit { it.moveDown(index) }

    private fun edit(transform: (ViewConfig) -> ViewConfig) {
        viewModelScope.launch { repository.save(transform(config.value)) }
    }
}
