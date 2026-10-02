package io.raylytics.justmyweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.data.GadgetbridgeSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs App Settings: the settings that are about the app rather than about
 * what the glance looks like. Customize edits the canvas (ViewConfig); this
 * holds everything else, each in its own store.
 */
class AppSettingsViewModel(
    private val gadgetbridgeSettings: GadgetbridgeSettingsRepository,
) : ViewModel() {
    /** A hand-off to another app on the phone, not part of the glance — which
     * is why it lives here and not in ViewConfig. */
    val gadgetbridgeEnabled: StateFlow<Boolean> =
        gadgetbridgeSettings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setGadgetbridgeEnabled(value: Boolean) {
        viewModelScope.launch { gadgetbridgeSettings.setEnabled(value) }
    }
}
