package io.raylytics.justmyweather.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.ThemeConfigRepository
import io.raylytics.justmyweather.view.ThemeConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the app's [ThemeConfig]. Observed at the activity root to wrap the whole
 * UI in the chosen theme, and edited from the customize screen. Like the other
 * config view models, the persisted config is the single source of truth: each
 * edit is a pure transform saved back to DataStore, and because the theme wraps
 * everything, a change re-themes the app immediately.
 */
class ThemeViewModel(
    private val repository: ThemeConfigRepository,
) : ViewModel() {
    val config: StateFlow<ThemeConfig> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeConfig.DEFAULT)

    fun save(config: ThemeConfig) {
        viewModelScope.launch { repository.save(config) }
    }
}
