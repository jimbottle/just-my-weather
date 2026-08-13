package io.raylytics.justmyweather.ui.customize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.GadgetbridgeSettingsRepository
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.view.AlertBannerPosition
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ForecastLayout
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewMode
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
    private val gadgetbridgeSettings: GadgetbridgeSettingsRepository,
) : ViewModel() {
    val config: StateFlow<ViewConfig> =
        repository.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewConfig.DEFAULT)

    /**
     * Kept separate from [config] rather than folded into ViewConfig: that
     * object describes what the glance looks like, while this is a hand-off to
     * another app on the phone. Different concern, different store.
     */
    val gadgetbridgeEnabled: StateFlow<Boolean> =
        gadgetbridgeSettings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setGadgetbridgeEnabled(value: Boolean) {
        viewModelScope.launch { gadgetbridgeSettings.setEnabled(value) }
    }

    fun toggle(field: WeatherField) = edit { it.toggle(field) }

    fun relabel(field: WeatherField, label: String?) = edit { it.relabel(field, label) }

    fun moveUp(index: Int) = edit { it.moveUp(index) }

    fun moveDown(index: Int) = edit { it.moveDown(index) }

    fun setDensity(density: Density) = edit { it.setDensity(density) }

    fun setDefaultMode(mode: ViewMode) = edit { it.setDefaultMode(mode) }

    fun setDailyStyle(style: DailyStyle) = edit { it.setDailyStyle(style) }

    fun setDailyLayout(layout: ForecastLayout) = edit { it.setDailyLayout(layout) }

    fun setHourlyLayout(layout: ForecastLayout) = edit { it.setHourlyLayout(layout) }

    fun setAlertBannerPosition(position: AlertBannerPosition) = edit { it.setAlertBannerPosition(position) }

    fun setShowSunTimes(show: Boolean) = edit { it.setShowSunTimes(show) }

    private fun edit(transform: (ViewConfig) -> ViewConfig) {
        viewModelScope.launch { repository.save(transform(config.value)) }
    }
}
